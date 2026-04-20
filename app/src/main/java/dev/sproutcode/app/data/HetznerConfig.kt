package dev.sproutcode.app.data

data class HetznerConfig(
    val apiToken:          String = "",
    val defaultLocation:   String = "nbg1",
    val defaultServerType: String = "cx22",
    val defaultImage:      String = "ubuntu-24.04",
    val githubToken:       String = ""
) {
    val isConfigured: Boolean get() = apiToken.isNotBlank()
}
