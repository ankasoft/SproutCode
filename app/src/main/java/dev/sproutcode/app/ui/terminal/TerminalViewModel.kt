package dev.sproutcode.app.ui.terminal

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sproutcode.app.data.AppPrefs
import dev.sproutcode.app.data.Server
import dev.sproutcode.app.data.ServerStore
import dev.sproutcode.app.ssh.HostKeyVerificationResult
import dev.sproutcode.app.ssh.SshManager
import dev.sproutcode.app.ssh.SshStreams
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class TerminalUiState {
    data object Connecting   : TerminalUiState()
    data object Connected    : TerminalUiState()
    data class  Error(val message: String) : TerminalUiState()
    data object Disconnected : TerminalUiState()
    data class  VerifyHostKey(
        val host: String,
        val port: Int,
        val fingerprint: String,
        val isChanged: Boolean,
        val expectedFingerprint: String? = null
    ) : TerminalUiState()
    data object Reconnecting : TerminalUiState()
}

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val serverStore = ServerStore(app)
    private val sshManager  = SshManager(app)
    private val uiPrefs     = app.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    private val appPrefs    = AppPrefs.getInstance(app)

    private val _uiState = MutableStateFlow<TerminalUiState>(TerminalUiState.Connecting)
    val uiState: StateFlow<TerminalUiState> = _uiState

    val terminalTheme = appPrefs.terminalTheme
    val terminalFont = appPrefs.terminalFont

    private var serverId: String? = null
    private var sshStreams:       SshStreams?      = null
    private var terminalView:    TerminalView?    = null
    private var terminalSession: TerminalSession? = null
    private var readJob:         Job?             = null
    private var currentCols = 80
    private var currentRows = 24

    var fontSize: Int = uiPrefs.getInt("font_size", 14)
        private set

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var reconnectJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex

    private var isSearchMode = false

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
            _currentSearchIndex.value = -1
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val session = terminalSession ?: return@launch
            val emulator = session.emulator ?: return@launch
            val results = mutableListOf<Int>()

            val rows = emulator.mRows
            val cols = emulator.mColumns

            for (row in 0 until rows) {
                val line = StringBuilder()
                for (col in 0 until cols) {
                    val char = emulator.getScreen().getSelectedText(row, col, row, col + 1)
                    if (char != null && char.isNotEmpty()) {
                        line.append(char)
                    }
                }
                val lineStr = line.toString()
                var index = lineStr.indexOf(query, ignoreCase = true)
                while (index != -1) {
                    results.add(row)
                    index = lineStr.indexOf(query, index + 1, ignoreCase = true)
                }
            }

            _searchResults.value = results
            _currentSearchIndex.value = if (results.isNotEmpty()) 0 else -1
        }
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIndex = (_currentSearchIndex.value + 1) % results.size
        _currentSearchIndex.value = nextIndex
    }

    fun previousSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val prevIndex = if (_currentSearchIndex.value <= 0) results.size - 1 else _currentSearchIndex.value - 1
        _currentSearchIndex.value = prevIndex
    }

    fun toggleSearchMode() {
        isSearchMode = !isSearchMode
        if (!isSearchMode) {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _currentSearchIndex.value = -1
        }
    }

    fun isSearchMode(): Boolean = isSearchMode

    fun adjustFontSize(delta: Int) {
        val newSize = (fontSize + delta).coerceIn(8, 32)
        if (newSize != fontSize) {
            fontSize = newSize
            uiPrefs.edit().putInt("font_size", newSize).apply()
            terminalView?.setTextSize(newSize)
        }
    }

    fun attachTerminalView(view: TerminalView) {
        terminalView = view
    }

    fun onTerminalSizeChanged(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        currentCols = cols
        currentRows = rows
        viewModelScope.launch(Dispatchers.IO) {
            if (sshManager.isConnected) sshManager.resize(cols, rows)
        }
        terminalSession?.updateSize(cols, rows)
    }

    fun setServerId(id: String) {
        if (serverId == id) return
        if (serverId != null) {
            disconnect()
        }
        serverId = id
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        _uiState.value = TerminalUiState.Connecting
    }

    fun onSessionReady(session: TerminalSession) {
        terminalSession = session
        viewModelScope.launch { verifyAndConnect() }
    }

    private suspend fun verifyAndConnect() {
        val id = serverId ?: run {
            _uiState.value = TerminalUiState.Error("Server not found.")
            return
        }
        val server = withContext(Dispatchers.IO) { serverStore.get(id) }
        if (server == null) {
            _uiState.value = TerminalUiState.Error("Server was deleted or not found.")
            return
        }

        // Check host key
        _uiState.value = TerminalUiState.Connecting
        val verification = withContext(Dispatchers.IO) {
            sshManager.verifyHostKey(server.host, server.port)
        }

        when (verification) {
            is HostKeyVerificationResult.Trusted -> {
                connectSsh(server)
            }
            is HostKeyVerificationResult.NewHost -> {
                if (verification.fingerprint.isBlank()) {
                    // Fingerprint alınamadı, doğrudan bağlan
                    connectSsh(server)
                } else {
                    _uiState.value = TerminalUiState.VerifyHostKey(
                        host = server.host,
                        port = server.port,
                        fingerprint = verification.fingerprint,
                        isChanged = false
                    )
                }
            }
            is HostKeyVerificationResult.Changed -> {
                _uiState.value = TerminalUiState.VerifyHostKey(
                    host = server.host,
                    port = server.port,
                    fingerprint = verification.actual,
                    isChanged = true,
                    expectedFingerprint = verification.expected
                )
            }
        }
    }

    fun trustHostAndConnect(host: String, port: Int, fingerprint: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                sshManager.trustHost(host, port, fingerprint)
            }
            val id = serverId ?: return@launch
            val server = withContext(Dispatchers.IO) { serverStore.get(id) } ?: return@launch
            connectSsh(server)
        }
    }

    fun trustHostAndReconnect(host: String, port: Int, fingerprint: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                sshManager.trustHost(host, port, fingerprint)
            }
            val id = serverId ?: return@launch
            val server = withContext(Dispatchers.IO) { serverStore.get(id) } ?: return@launch
            connectSsh(server)
        }
    }

    private suspend fun connectSsh(server: Server) {
        try {
            _uiState.value = TerminalUiState.Connecting
            val streams = sshManager.connect(server, currentCols, currentRows)
            sshStreams = streams
            _uiState.value = TerminalUiState.Connected
            startReadLoop(streams)
        } catch (e: Exception) {
            _uiState.value = TerminalUiState.Error(e.message ?: "Connection failed.")
        }
    }

    private fun startReadLoop(streams: SshStreams) {
        readJob = viewModelScope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            try {
                while (isActive) {
                    val len = streams.input.read(buf)
                    if (len <= 0) break
                    withContext(Dispatchers.Main) {
                        terminalSession?.emulator?.append(buf, len)
                        terminalView?.onScreenUpdated()
                    }
                }
            } catch (_: Exception) {
                // channel closed
            } finally {
                withContext(Dispatchers.Main) {
                    if (_uiState.value !is TerminalUiState.Error && _uiState.value !is TerminalUiState.Disconnected) {
                        attemptReconnect()
                    }
                }
            }
        }
    }

    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            _uiState.value = TerminalUiState.Error("Connection lost. Max reconnection attempts reached.")
            return
        }

        reconnectAttempts++
        _uiState.value = TerminalUiState.Reconnecting

        val delayMs = (1000L * reconnectAttempts).coerceAtMost(30000L)

        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (!isActive) return@launch

            val id = serverId ?: return@launch
            val server = withContext(Dispatchers.IO) { serverStore.get(id) } ?: return@launch

            // Re-verify host key if needed
            val verification = withContext(Dispatchers.IO) {
                sshManager.verifyHostKey(server.host, server.port)
            }

            when (verification) {
                is HostKeyVerificationResult.Trusted -> {
                    connectSsh(server)
                }
                is HostKeyVerificationResult.NewHost -> {
                    _uiState.value = TerminalUiState.VerifyHostKey(
                        host = server.host,
                        port = server.port,
                        fingerprint = verification.fingerprint,
                        isChanged = false
                    )
                }
                is HostKeyVerificationResult.Changed -> {
                    _uiState.value = TerminalUiState.VerifyHostKey(
                        host = server.host,
                        port = server.port,
                        fingerprint = verification.actual,
                        isChanged = true,
                        expectedFingerprint = verification.expected
                    )
                }
            }
        }
    }

    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        _uiState.value = TerminalUiState.Disconnected
    }

    fun writeToSsh(data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sshStreams?.output?.apply { write(data); flush() }
            } catch (_: Exception) {}
        }
    }

    fun writeToSsh(text: String) = writeToSsh(text.toByteArray(Charsets.UTF_8))

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        val streams = sshStreams
        sshStreams      = null
        terminalView    = null
        terminalSession = null
        serverId        = null
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentSearchIndex.value = -1
        isSearchMode = false
        _uiState.value  = TerminalUiState.Disconnected
        viewModelScope.launch(Dispatchers.IO) {
            if (streams != null) sshManager.disconnect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    fun clearState() {
        disconnect()
    }
}
