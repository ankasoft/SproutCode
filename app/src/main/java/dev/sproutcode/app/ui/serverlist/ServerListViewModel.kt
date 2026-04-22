package dev.sproutcode.app.ui.serverlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sproutcode.app.data.AppPrefs
import dev.sproutcode.app.data.HetznerConfigStore
import dev.sproutcode.app.data.Server
import dev.sproutcode.app.data.ServerStore
import dev.sproutcode.app.hetzner.HetznerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServerStatus(
    val serverId: String,
    val isOnline: Boolean,
    val isChecking: Boolean = false
)

class ServerListViewModel(app: Application) : AndroidViewModel(app) {

    private val store        = ServerStore(app)
    private val hetznerStore = HetznerConfigStore(app)
    val appPrefs = AppPrefs.getInstance(app)

    private val _servers     = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers

    private val _serverStatuses = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    val serverStatuses: StateFlow<Map<String, ServerStatus>> = _serverStatuses

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError

    fun clearDeleteError() { _deleteError.value = null }

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _servers.value = withContext(Dispatchers.IO) { store.list() }
            checkAllServerStatuses()
        }
    }

    fun checkAllServerStatuses() {
        viewModelScope.launch {
            val serverList = _servers.value
            serverList.forEach { server ->
                _serverStatuses.value = _serverStatuses.value + (server.id to ServerStatus(
                    serverId = server.id,
                    isOnline = false,
                    isChecking = true
                ))
                val isOnline = withContext(Dispatchers.IO) {
                    checkServerOnline(server.host, server.port)
                }
                _serverStatuses.value = _serverStatuses.value + (server.id to ServerStatus(
                    serverId = server.id,
                    isOnline = isOnline,
                    isChecking = false
                ))
            }
        }
    }

    private fun checkServerOnline(host: String, port: Int): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), 3000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            refresh()
        }
    }

    fun deleteWithHetzner(server: Server) {
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) { hetznerStore.load().apiToken }
                if (token.isNotBlank() && server.hetznerId != null) {
                    withContext(Dispatchers.IO) {
                        HetznerClient.deleteServer(token, server.hetznerId)
                    }
                }
                withContext(Dispatchers.IO) { store.delete(server.id) }
                refresh()
            } catch (e: Exception) {
                _deleteError.value = e.message ?: "Failed to delete from Hetzner."
            }
        }
    }

    fun toggleTheme() = appPrefs.toggleTheme()
}
