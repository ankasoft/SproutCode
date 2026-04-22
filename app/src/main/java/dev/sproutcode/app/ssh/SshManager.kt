package dev.sproutcode.app.ssh

import android.content.Context
import dev.sproutcode.app.data.KnownHostsStore
import dev.sproutcode.app.data.Server
import dev.sproutcode.app.data.SshKeyStore
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

data class SshStreams(
    val input: InputStream,
    val output: OutputStream
)

sealed class HostKeyVerificationResult {
    data class Trusted(val fingerprint: String) : HostKeyVerificationResult()
    data class NewHost(val fingerprint: String) : HostKeyVerificationResult()
    data class Changed(val expected: String, val actual: String) : HostKeyVerificationResult()
}

class SshManager(context: Context) {

    private val sshKeyStore = SshKeyStore(context)
    private val knownHostsStore = KnownHostsStore(context)

    private var session: Session?      = null
    private var channel: ChannelShell? = null

    fun verifyHostKey(host: String, port: Int): HostKeyVerificationResult {
        val expectedFingerprint = knownHostsStore.getFingerprint(host, port)

        return try {
            val jsch = JSch()
            val sess = jsch.getSession("dummy", host, port)
            sess.setConfig("StrictHostKeyChecking", "no")
            sess.connect(10_000)
            val hostKey = sess.hostKey
            val fingerprint = hostKey?.let { 
                try {
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    val hash = md.digest(it.key.toByteArray(Charsets.UTF_8))
                    hash.joinToString("") { b -> String.format("%02x", b) }
                } catch (e: Exception) {
                    ""
                }
            } ?: ""
            sess.disconnect()

            when {
                expectedFingerprint == null -> HostKeyVerificationResult.NewHost(fingerprint)
                expectedFingerprint == fingerprint -> HostKeyVerificationResult.Trusted(fingerprint)
                else -> HostKeyVerificationResult.Changed(expectedFingerprint, fingerprint)
            }
        } catch (e: Exception) {
            HostKeyVerificationResult.NewHost("")
        }
    }

    fun trustHost(host: String, port: Int, fingerprint: String) {
        knownHostsStore.saveFingerprint(host, port, fingerprint)
    }

    suspend fun connect(server: Server, columns: Int, rows: Int): SshStreams =
        withContext(Dispatchers.IO) {
            val jsch = JSch()

            if (server.useKeyAuth) {
                jsch.addIdentity("sproutcode", sshKeyStore.privateKey(), null, null)
            }

            val sess = jsch.getSession(server.username, server.host, server.port)
            if (server.useKeyAuth) {
                sess.setConfig("PreferredAuthentications", "publickey")
            } else {
                sess.setPassword(server.password)
                sess.setConfig("PreferredAuthentications", "password")
            }
            sess.setConfig("StrictHostKeyChecking", "no")
            sess.setServerAliveInterval(30_000)
            sess.setServerAliveCountMax(3)
            sess.connect(15_000)

            val ch = sess.openChannel("shell") as ChannelShell
            ch.setPtyType("xterm-256color")
            ch.setPty(true)
            ch.setPtySize(columns, rows, columns * 8, rows * 16)

            val inputStream  = ch.inputStream
            val outputStream = ch.outputStream

            ch.connect(10_000)

            session = sess
            channel = ch

            SshStreams(input = inputStream, output = outputStream)
        }

    suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        channel?.setPtySize(columns, rows, columns * 8, rows * 16)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
    }

    val isConnected: Boolean
        get() = session?.isConnected == true && channel?.isClosed == false
}
