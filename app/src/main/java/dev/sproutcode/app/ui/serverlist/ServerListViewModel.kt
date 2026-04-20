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

class ServerListViewModel(app: Application) : AndroidViewModel(app) {

    private val store        = ServerStore(app)
    private val hetznerStore = HetznerConfigStore(app)
    val appPrefs = AppPrefs.getInstance(app)

    private val _servers     = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError

    fun clearDeleteError() { _deleteError.value = null }

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _servers.value = withContext(Dispatchers.IO) { store.list() }
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
