package dev.sproutcode.app.data

import java.util.UUID

data class Server(
    val id:         String = UUID.randomUUID().toString(),
    val name:       String,
    val host:       String,
    val port:       Int     = 22,
    val username:   String  = "root",
    val password:   String  = "",
    val hetznerId:  Long?   = null,   // null = added manually; Long = created on Hetzner
    val useKeyAuth: Boolean = false   // true = use app SSH key; false = use password
)
