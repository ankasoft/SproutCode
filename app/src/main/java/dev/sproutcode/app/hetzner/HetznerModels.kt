package dev.sproutcode.app.hetzner

data class HetznerLocation(
    val name:        String,
    val city:        String,
    val country:     String,
    val description: String
) {
    val label: String get() = "$name — $city, $country"
}

data class HetznerServerType(
    val name:         String,
    val description:  String,
    val cores:        Int,
    val memory:       Double,  // GB
    val disk:         Int,     // GB
    val architecture: String   // "x86" / "arm"
) {
    val label: String
        get() = "$name — ${cores}vCPU · ${memory}GB RAM · ${disk}GB disk · $architecture"
}

data class HetznerImage(
    val id:          Long,
    val type:        String,        // system, snapshot, backup, app
    val name:        String?,       // os image'lar için (ubuntu-24.04 vb.)
    val osFlavor:    String,
    val osVersion:   String?,
    val description: String
) {
    /** API'de server oluştururken kullanılacak değer: sistem imageları için isim, diğerleri için id. */
    val identifier: String get() = name ?: id.toString()
    val label: String
        get() {
            val tag = when (type) {
                "system"   -> ""
                "snapshot" -> "[Snapshot] "
                "backup"   -> "[Backup] "
                "app"      -> "[App] "
                else       -> "[$type] "
            }
            val desc = description.ifBlank { osFlavor.ifBlank { type } }
            return "$tag$identifier — $desc"
        }
}

data class HetznerSshKey(
    val id:          Long,
    val name:        String,
    val fingerprint: String,
    val publicKey:   String
)

data class HetznerServerSummary(
    val id:         Long,
    val name:       String,
    val status:     String,         // initializing, starting, running, off...
    val ipv4:       String?,
    val datacenter: String,
    val serverType: String,
    val image:      String?
)

data class CreateServerRequest(
    val name:             String,
    val serverType:       String,
    val location:         String,
    val image:            String,
    val sshKeyIds:        List<Long>,
    val startAfterCreate: Boolean = true,
    val userData:         String? = null
)

class HetznerException(message: String) : RuntimeException(message)

