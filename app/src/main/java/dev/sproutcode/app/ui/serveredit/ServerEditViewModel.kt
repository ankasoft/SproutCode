package dev.sproutcode.app.ui.serveredit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sproutcode.app.data.Server
import dev.sproutcode.app.data.ServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServerEditUiState(
    val id:       String? = null,     // null → new
    val name:     String  = "",
    val host:     String  = "",
    val port:     String  = "22",
    val username: String  = "",
    val password: String  = "",
    val isLoaded: Boolean = false
)

class ServerEditViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ServerStore(app)

    private val _uiState = MutableStateFlow(ServerEditUiState())
    val uiState: StateFlow<ServerEditUiState> = _uiState

    private var initialized = false

    fun load(serverId: String?) {
        if (initialized) return
        initialized = true
        if (serverId == null) {
            _uiState.value = _uiState.value.copy(isLoaded = true)
            return
        }
        viewModelScope.launch {
            val srv = withContext(Dispatchers.IO) { store.get(serverId) }
            if (srv != null) {
                _uiState.value = ServerEditUiState(
                    id       = srv.id,
                    name     = srv.name,
                    host     = srv.host,
                    port     = srv.port.toString(),
                    username = srv.username,
                    password = srv.password,
                    isLoaded = true
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoaded = true)
            }
        }
    }

    fun onNameChange(v: String)     { _uiState.value = _uiState.value.copy(name     = v) }
    fun onHostChange(v: String)     { _uiState.value = _uiState.value.copy(host     = v) }
    fun onPortChange(v: String)     { _uiState.value = _uiState.value.copy(port     = v) }
    fun onUsernameChange(v: String) { _uiState.value = _uiState.value.copy(username = v) }
    fun onPasswordChange(v: String) { _uiState.value = _uiState.value.copy(password = v) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _uiState.value
            val server = Server(
                id       = s.id ?: Server(name = "", host = "", port = 22, username = "", password = "").id,
                name     = s.name.trim().ifBlank { "${s.username.trim()}@${s.host.trim()}" },
                host     = s.host.trim(),
                port     = s.port.toIntOrNull() ?: 22,
                username = s.username.trim(),
                password = s.password
            )
            withContext(Dispatchers.IO) { store.save(server) }
            onDone()
        }
    }

    fun isValid(): Boolean {
        val s = _uiState.value
        return s.host.isNotBlank() && s.username.isNotBlank() && s.password.isNotBlank()
    }
}
