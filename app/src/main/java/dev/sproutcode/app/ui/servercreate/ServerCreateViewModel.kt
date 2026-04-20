package dev.sproutcode.app.ui.servercreate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sproutcode.app.data.HetznerConfigStore
import dev.sproutcode.app.data.Server
import dev.sproutcode.app.data.ServerStore
import dev.sproutcode.app.data.SshKeyStore
import dev.sproutcode.app.hetzner.CreateServerRequest
import dev.sproutcode.app.hetzner.HetznerClient
import dev.sproutcode.app.hetzner.HetznerImage
import dev.sproutcode.app.hetzner.HetznerLocation
import dev.sproutcode.app.hetzner.HetznerServerSummary
import dev.sproutcode.app.hetzner.HetznerServerType
import dev.sproutcode.app.ssh.SshProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServerCreateUiState(
    val name:             String                  = "",
    val location:         String                  = "",
    val serverType:       String                  = "",
    val image:            String                  = "",
    val githubRepoUrl:    String                  = "",
    val locations:        List<HetznerLocation>   = emptyList(),
    val serverTypes:      List<HetznerServerType> = emptyList(),
    val images:           List<HetznerImage>      = emptyList(),
    val isLoadingOptions: Boolean                 = false,
    val optionsError:     String?                 = null,
    val isCreating:       Boolean                 = false,
    val progress:         String                  = "",
    val createError:      String?                 = null,
    val createdServerId:  String?                 = null,
    val tokenMissing:     Boolean                 = false
)

class ServerCreateViewModel(app: Application) : AndroidViewModel(app) {

    private val hetznerStore = HetznerConfigStore(app)
    private val sshKeyStore  = SshKeyStore(app)
    private val serverStore  = ServerStore(app)

    private val _uiState = MutableStateFlow(ServerCreateUiState())
    val uiState: StateFlow<ServerCreateUiState> = _uiState

    init { loadDefaultsAndOptions() }

    private fun loadDefaultsAndOptions() {
        viewModelScope.launch {
            try {
                val cfg = withContext(Dispatchers.IO) { hetznerStore.load() }
                if (cfg.apiToken.isBlank()) {
                    _uiState.value = _uiState.value.copy(tokenMissing = true)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    location   = cfg.defaultLocation,
                    serverType = cfg.defaultServerType,
                    image      = cfg.defaultImage,
                    isLoadingOptions = true
                )
                try {
                    val locations   = HetznerClient.listLocations(cfg.apiToken)
                    val serverTypes = HetznerClient.listServerTypes(cfg.apiToken)
                    val images      = HetznerClient.listImages(cfg.apiToken)
                    _uiState.value = _uiState.value.copy(
                        locations        = locations,
                        serverTypes      = serverTypes,
                        images           = images,
                        isLoadingOptions = false
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingOptions = false,
                        optionsError     = e.message ?: "Failed to load options."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingOptions = false,
                    optionsError     = e.message ?: "Failed to read settings."
                )
            }
        }
    }

    fun onNameChange(v: String)          { _uiState.value = _uiState.value.copy(name          = v) }
    fun onLocationChange(v: String)      { _uiState.value = _uiState.value.copy(location      = v) }
    fun onServerTypeChange(v: String)    { _uiState.value = _uiState.value.copy(serverType    = v) }
    fun onImageChange(v: String)         { _uiState.value = _uiState.value.copy(image         = v) }
    fun onGithubRepoUrlChange(v: String) { _uiState.value = _uiState.value.copy(githubRepoUrl = v) }

    fun create() {
        val s = _uiState.value
        if (s.location.isBlank() || s.serverType.isBlank() || s.image.isBlank()) {
            _uiState.value = s.copy(createError = "Location, type and image are required.")
            return
        }
        viewModelScope.launch {
            val cfg = withContext(Dispatchers.IO) { hetznerStore.load() }
            val token = cfg.apiToken
            if (token.isBlank()) {
                _uiState.value = _uiState.value.copy(tokenMissing = true)
                return@launch
            }
            try {
                progress("Uploading SSH key to Hetzner...", true)
                val keyId = HetznerClient.ensureSshKey(token, sshKeyStore.publicKey())

                progress("Creating server...", true)
                val name = s.name.trim().ifBlank { "sproutcode-${System.currentTimeMillis() / 1000}" }
                val userData = s.githubRepoUrl.trim().takeIf { it.isNotBlank() }
                    ?.let { buildUserData(it, cfg.githubToken) }
                val created = HetznerClient.createServer(
                    token,
                    CreateServerRequest(
                        name       = name,
                        serverType = s.serverType,
                        location   = s.location,
                        image      = s.image,
                        sshKeyIds  = listOf(keyId),
                        userData   = userData
                    )
                )

                progress("Starting VM...", true)
                val ready = waitForRunning(token, created.id) { elapsed ->
                    progress("Starting VM... (${elapsed}s)", true)
                }
                val host = ready.ipv4 ?: throw Exception("No IP assigned to server.")

                val tcpOk = SshProbe.waitForTcp(host, 22, maxAttempts = 30) { attempt ->
                    progress("Waiting for SSH port... (${attempt * 2}s)", true)
                }
                if (!tcpOk) throw Exception("Port 22 did not open (60s timeout).")

                val authOk = SshProbe.waitForSshAuth(
                    host       = host,
                    port       = 22,
                    username   = "root",
                    privateKey = sshKeyStore.privateKey(),
                    maxAttempts = 20
                ) { attempt ->
                    progress("Waiting for key injection... (${attempt * 3}s)", true)
                }
                if (!authOk) throw Exception("SSH key auth failed. Cloud-init error possible.")

                val saved = Server(
                    name       = ready.name,
                    host       = ready.ipv4,
                    port       = 22,
                    username   = "root",
                    password   = "",
                    hetznerId  = ready.id,
                    useKeyAuth = true
                )
                withContext(Dispatchers.IO) { serverStore.save(saved) }

                _uiState.value = _uiState.value.copy(
                    isCreating      = false,
                    progress        = "Ready!",
                    createdServerId = saved.id
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating  = false,
                    progress    = "",
                    createError = e.message ?: "Creation failed."
                )
            }
        }
    }

    private fun buildUserData(repoUrl: String, token: String): String {
        val normalized = when {
            repoUrl.startsWith("https://") || repoUrl.startsWith("http://") -> repoUrl
            repoUrl.startsWith("github.com") -> "https://$repoUrl"
            else -> "https://github.com/$repoUrl"
        }
        val cloneUrl = if (token.isBlank()) normalized
                       else normalized.replace("https://", "https://$token@")
        return "#!/bin/bash\ncd /root\ngit clone $cloneUrl repo\n"
    }

    private suspend fun waitForRunning(
        token: String,
        id:    Long,
        onTick: (elapsedSec: Int) -> Unit = {}
    ): HetznerServerSummary {
        var info: HetznerServerSummary? = null
        repeat(40) { attempt ->
            onTick(attempt * 3)
            val current = HetznerClient.getServer(token, id)
            info = current
            if (current.status == "running" && !current.ipv4.isNullOrBlank()) return current
            delay(3_000)
        }
        return info ?: throw Exception("VM did not become ready in time (2min).")
    }

    private fun progress(text: String, creating: Boolean) {
        _uiState.value = _uiState.value.copy(progress = text, isCreating = creating, createError = null)
    }
}
