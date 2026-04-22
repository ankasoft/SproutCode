package dev.sproutcode.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.sproutcode.app.data.HetznerConfigStore
import dev.sproutcode.app.data.Server
import dev.sproutcode.app.data.ServerStore
import dev.sproutcode.app.data.SshKeyStore
import dev.sproutcode.app.hetzner.CreateServerRequest
import dev.sproutcode.app.hetzner.HetznerClient
import dev.sproutcode.app.notification.NotificationHelper
import dev.sproutcode.app.ssh.SshProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ServerCreationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val hetznerStore = HetznerConfigStore(context)
    private val sshKeyStore = SshKeyStore(context)
    private val serverStore = ServerStore(context)

    companion object {
        const val WORK_NAME = "server_creation"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_LOCATION = "location"
        const val KEY_SERVER_TYPE = "server_type"
        const val KEY_IMAGE = "image"
        const val KEY_GITHUB_REPO = "github_repo"
    }

    override suspend fun doWork(): Result {
        val serverName = inputData.getString(KEY_SERVER_NAME) ?: ""
        val location = inputData.getString(KEY_LOCATION) ?: ""
        val serverType = inputData.getString(KEY_SERVER_TYPE) ?: ""
        val image = inputData.getString(KEY_IMAGE) ?: ""
        val githubRepo = inputData.getString(KEY_GITHUB_REPO) ?: ""

        return try {
            val cfg = withContext(Dispatchers.IO) { hetznerStore.load() }
            val token = cfg.apiToken

            if (token.isBlank()) {
                return Result.failure(workDataOf("error" to "API token not configured"))
            }

            setProgress(workDataOf("progress" to "Uploading SSH key..."))
            val keyId = HetznerClient.ensureSshKey(token, sshKeyStore.publicKey())

            setProgress(workDataOf("progress" to "Creating server..."))
            val name = serverName.ifBlank { "sproutcode-${System.currentTimeMillis() / 1000}" }
            val userData = githubRepo.takeIf { it.isNotBlank() }
                ?.let { buildUserData(it, cfg.githubToken) }

            val created = HetznerClient.createServer(
                token,
                CreateServerRequest(
                    name       = name,
                    serverType = serverType,
                    location   = location,
                    image      = image,
                    sshKeyIds  = listOf(keyId),
                    userData   = userData
                )
            )

            setProgress(workDataOf("progress" to "Waiting for server to start..."))
            val ready = waitForRunning(token, created.id)
            val host = ready.ipv4 ?: throw Exception("No IP assigned to server.")

            setProgress(workDataOf("progress" to "Waiting for SSH port..."))
            val tcpOk = SshProbe.waitForTcp(host, 22, maxAttempts = 30) { attempt ->
                // Can't call suspend function in lambda, use simple progress
            }
            if (!tcpOk) throw Exception("Port 22 did not open (60s timeout).")

            setProgress(workDataOf("progress" to "Waiting for SSH key injection..."))
            val authOk = SshProbe.waitForSshAuth(
                host       = host,
                port       = 22,
                username   = "root",
                privateKey = sshKeyStore.privateKey(),
                maxAttempts = 20
            ) { attempt ->
                // Can't call suspend function in lambda, use simple progress
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

            NotificationHelper.showServerCreationNotification(
                applicationContext,
                "Server Ready",
                "${saved.name} is ready. Tap to connect.",
                saved.id
            )

            Result.success(workDataOf("server_id" to saved.id))
        } catch (e: Exception) {
            NotificationHelper.showServerCreationNotification(
                applicationContext,
                "Server Creation Failed",
                e.message ?: "Unknown error",
                null
            )
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun waitForRunning(
        token: String,
        id: Long
    ): dev.sproutcode.app.hetzner.HetznerServerSummary {
        var info: dev.sproutcode.app.hetzner.HetznerServerSummary? = null
        repeat(40) { attempt ->
            setProgress(workDataOf("progress" to "Starting VM... (${attempt * 3}s)"))
            val current = HetznerClient.getServer(token, id)
            info = current
            if (current.status == "running" && !current.ipv4.isNullOrBlank()) return current
            delay(3_000)
        }
        return info ?: throw Exception("VM did not become ready in time (2min).")
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
}
