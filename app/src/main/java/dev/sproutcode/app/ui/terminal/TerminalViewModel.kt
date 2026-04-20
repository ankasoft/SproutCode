package dev.sproutcode.app.ui.terminal

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sproutcode.app.data.ServerStore
import dev.sproutcode.app.ssh.SshManager
import dev.sproutcode.app.ssh.SshStreams
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
}

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val serverStore = ServerStore(app)
    private val sshManager  = SshManager(app)
    private val uiPrefs     = app.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<TerminalUiState>(TerminalUiState.Connecting)
    val uiState: StateFlow<TerminalUiState> = _uiState

    private var serverId: String? = null
    private var sshStreams:       SshStreams?      = null
    private var terminalView:    TerminalView?    = null
    private var terminalSession: TerminalSession? = null
    private var readJob:         Job?             = null
    private var currentCols = 80
    private var currentRows = 24

    var fontSize: Int = uiPrefs.getInt("font_size", 14)
        private set

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
        if (serverId != null) return
        serverId = id
    }

    fun onSessionReady(session: TerminalSession) {
        if (terminalSession != null) return
        terminalSession = session
        viewModelScope.launch { connectSsh() }
    }

    private suspend fun connectSsh() {
        val id = serverId ?: run {
            _uiState.value = TerminalUiState.Error("Server not found.")
            return
        }
        val server = withContext(Dispatchers.IO) { serverStore.get(id) }
        if (server == null) {
            _uiState.value = TerminalUiState.Error("Server was deleted or not found.")
            return
        }
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
                    if (_uiState.value !is TerminalUiState.Error) {
                        _uiState.value = TerminalUiState.Disconnected
                    }
                }
            }
        }
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
        val streams = sshStreams
        sshStreams      = null
        terminalView    = null
        terminalSession = null
        viewModelScope.launch(Dispatchers.IO) {
            if (streams != null) sshManager.disconnect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
