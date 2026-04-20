package dev.sproutcode.app.ssh

import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Hetzner "status=running" dese de sshd gecikmeli gelebilir, cloud-init SSH anahtarını
 * birkaç saniye sonra enjekte eder. Bu yüzden iki aşamalı prob:
 *   1. TCP bağlantısı → sshd gerçekten dinliyor mu?
 *   2. SSH handshake + key auth → anahtarımız içeri alınıyor mu?
 */
object SshProbe {

    /** Port 22'de TCP bağlantısı kurulana kadar bekler. Ready ise true döner. */
    suspend fun waitForTcp(
        host:        String,
        port:        Int,
        maxAttempts: Int  = 30,
        delayMs:     Long = 2_000,
        onTick:      (attempt: Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        repeat(maxAttempts) { attempt ->
            onTick(attempt + 1)
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 3_000)
                    return@withContext true
                }
            } catch (_: Exception) {
                delay(delayMs)
            }
        }
        false
    }

    /** SSH key auth ile geçici session açar, hemen kapatır. Başarılıysa true. */
    suspend fun waitForSshAuth(
        host:        String,
        port:        Int,
        username:    String,
        privateKey:  ByteArray,
        maxAttempts: Int  = 20,
        delayMs:     Long = 3_000,
        onTick:      (attempt: Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        repeat(maxAttempts) { attempt ->
            onTick(attempt + 1)
            try {
                val jsch = JSch()
                jsch.addIdentity("sproutcode-probe", privateKey, null, null)
                val sess = jsch.getSession(username, host, port)
                sess.setConfig("StrictHostKeyChecking", "no")
                sess.setConfig("PreferredAuthentications", "publickey")
                sess.connect(5_000)
                val ok = sess.isConnected
                runCatching { sess.disconnect() }
                if (ok) return@withContext true
            } catch (_: Exception) {
                delay(delayMs)
            }
        }
        false
    }
}
