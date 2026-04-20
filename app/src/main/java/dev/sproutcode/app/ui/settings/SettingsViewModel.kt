package dev.sproutcode.app.ui.settings

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sproutcode.app.data.HetznerConfig
import dev.sproutcode.app.data.HetznerConfigStore
import dev.sproutcode.app.data.SshKeyStore
import dev.sproutcode.app.hetzner.HetznerClient
import dev.sproutcode.app.hetzner.HetznerImage
import dev.sproutcode.app.hetzner.HetznerLocation
import dev.sproutcode.app.hetzner.HetznerServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val hetzner:       HetznerConfig            = HetznerConfig(),
    val sshPublicKey:  String                   = "",
    val isLoaded:      Boolean                  = false,
    val loadError:     String?                  = null,
    val locations:     List<HetznerLocation>    = emptyList(),
    val serverTypes:   List<HetznerServerType>  = emptyList(),
    val images:        List<HetznerImage>       = emptyList(),
    val isFetching:    Boolean                  = false,
    val fetchError:    String?                  = null,
    val fetchedOnce:   Boolean                  = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store       = HetznerConfigStore(app)
    private val sshKeyStore = SshKeyStore(app)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            try {
                val cfg    = withContext(Dispatchers.IO) { store.load() }
                val pubKey = withContext(Dispatchers.IO) { sshKeyStore.publicKey() }
                _uiState.value = _uiState.value.copy(
                    hetzner      = cfg,
                    sshPublicKey = pubKey,
                    isLoaded     = true
                )
            } catch (e: Throwable) {
                Log.e("SettingsVM", "Load failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoaded  = true,
                    loadError = "${e::class.simpleName}: ${e.message}"
                )
            }
        }
    }

    private fun update(transform: (HetznerConfig) -> HetznerConfig) {
        _uiState.value = _uiState.value.copy(hetzner = transform(_uiState.value.hetzner))
    }

    fun onApiTokenChange(v: String)    = update { it.copy(apiToken          = v) }
    fun onLocationChange(v: String)    = update { it.copy(defaultLocation   = v) }
    fun onServerTypeChange(v: String)  = update { it.copy(defaultServerType = v) }
    fun onImageChange(v: String)       = update { it.copy(defaultImage      = v) }
    fun onGithubTokenChange(v: String) = update { it.copy(githubToken       = v) }

    fun copyPublicKeyToClipboard() {
        val clip = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("SproutCode SSH Public Key", _uiState.value.sshPublicKey))
    }

    fun regenerateSshKey() {
        viewModelScope.launch {
            val newKey = withContext(Dispatchers.IO) {
                sshKeyStore.regenerate()
                sshKeyStore.publicKey()
            }
            _uiState.value = _uiState.value.copy(sshPublicKey = newKey)
        }
    }

    fun fetchHetznerOptions() {
        val token = _uiState.value.hetzner.apiToken.trim()
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(fetchError = "Enter an API token first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetching = true, fetchError = null)
            try {
                val locations   = HetznerClient.listLocations(token)
                val serverTypes = HetznerClient.listServerTypes(token)
                val images      = HetznerClient.listImages(token)
                _uiState.value = _uiState.value.copy(
                    locations    = locations,
                    serverTypes  = serverTypes,
                    images       = images,
                    isFetching   = false,
                    fetchedOnce  = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFetching = false,
                    fetchError = e.message ?: "Failed to fetch."
                )
            }
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val cfg = _uiState.value.hetzner.copy(
                    apiToken = _uiState.value.hetzner.apiToken.trim()
                )
                withContext(Dispatchers.IO) { store.save(cfg) }
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    fetchError = e.message ?: "Save failed."
                )
            }
        }
    }
}
