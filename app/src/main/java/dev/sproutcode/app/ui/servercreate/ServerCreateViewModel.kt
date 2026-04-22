package dev.sproutcode.app.ui.servercreate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
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

    private var workObserver: Observer<List<WorkInfo>>? = null
    private var creationStartedThisSession: Boolean = false

    init { 
        // Ekran her açıldığında state'i sıfırla
        _uiState.value = ServerCreateUiState()
        loadDefaultsAndOptions()
    }
    
    fun observeWorkProgress(lifecycleOwner: LifecycleOwner) {
        removeWorkObserver()
        
        val workManager = androidx.work.WorkManager.getInstance(getApplication())
        val observer = Observer<List<WorkInfo>> { workInfos ->
            workInfos?.firstOrNull()?.let { workInfo ->
                if (!creationStartedThisSession) return@let
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getString("progress") ?: "Creating server..."
                        _uiState.value = _uiState.value.copy(
                            isCreating = true,
                            progress = progress
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val serverId = workInfo.outputData.getString("server_id")
                        _uiState.value = _uiState.value.copy(
                            isCreating = false,
                            progress = "Ready!",
                            createdServerId = serverId
                        )
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString("error") ?: "Creation failed"
                        _uiState.value = _uiState.value.copy(
                            isCreating = false,
                            progress = "",
                            createError = error
                        )
                    }
                    else -> {}
                }
            }
        }
        workObserver = observer
        workManager.getWorkInfosForUniqueWorkLiveData(dev.sproutcode.app.worker.ServerCreationWorker.WORK_NAME)
            .observe(lifecycleOwner, observer)
    }
    
    fun removeWorkObserver() {
        workObserver?.let {
            val workManager = androidx.work.WorkManager.getInstance(getApplication())
            workManager.getWorkInfosForUniqueWorkLiveData(dev.sproutcode.app.worker.ServerCreationWorker.WORK_NAME)
                .removeObserver(it)
            workObserver = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        removeWorkObserver()
    }
    
    fun resetCreatedServerId() {
        _uiState.value = _uiState.value.copy(createdServerId = null)
    }

    fun resetState() {
        creationStartedThisSession = false
        _uiState.value = ServerCreateUiState()
        loadDefaultsAndOptions()
    }

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

    fun create(sendNotification: (title: String, message: String, serverId: String?) -> Unit = { _, _, _ -> }) {
        val s = _uiState.value
        if (s.location.isBlank() || s.serverType.isBlank() || s.image.isBlank()) {
            _uiState.value = s.copy(createError = "Location, type and image are required.")
            return
        }
        
        // Start WorkManager for background execution
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<dev.sproutcode.app.worker.ServerCreationWorker>()
            .setInputData(androidx.work.workDataOf(
                dev.sproutcode.app.worker.ServerCreationWorker.KEY_SERVER_NAME to s.name,
                dev.sproutcode.app.worker.ServerCreationWorker.KEY_LOCATION to s.location,
                dev.sproutcode.app.worker.ServerCreationWorker.KEY_SERVER_TYPE to s.serverType,
                dev.sproutcode.app.worker.ServerCreationWorker.KEY_IMAGE to s.image,
                dev.sproutcode.app.worker.ServerCreationWorker.KEY_GITHUB_REPO to s.githubRepoUrl
            ))
            .build()
        
        androidx.work.WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(
                dev.sproutcode.app.worker.ServerCreationWorker.WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )

        creationStartedThisSession = true
        _uiState.value = _uiState.value.copy(
            isCreating = true,
            progress = "Server creation started in background..."
        )
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
