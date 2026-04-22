package dev.sproutcode.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sproutcode.app.data.TerminalFont
import dev.sproutcode.app.data.TerminalTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val terminalTheme by vm.terminalTheme.collectAsStateWithLifecycle()
    val terminalFont by vm.terminalFont.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.loadError?.let { err ->
                Text(
                    text  = "Load error: $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SectionHeader("Hetzner Cloud")
            Text(
                text  = "For token: Hetzner Console → Project → Security → API Tokens (Read & Write).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedTextField(
                value                = state.hetzner.apiToken,
                onValueChange        = vm::onApiTokenChange,
                label                = { Text("API Token") },
                singleLine           = true,
                modifier             = Modifier.fillMaxWidth(),
                visualTransformation = if (showToken) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )

            OutlinedButton(
                onClick  = { vm.fetchHetznerOptions() },
                enabled  = !state.isFetching && state.hetzner.apiToken.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isFetching) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Loading...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.fetchedOnce) "Refresh lists" else "Fetch from Hetzner")
                }
            }

            state.fetchError?.let { err ->
                Text(
                    text  = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(4.dp))

            DropdownField(
                label         = "Default Location",
                value         = state.hetzner.defaultLocation,
                onValueChange = vm::onLocationChange,
                options       = state.locations.map { it.name to it.label },
                placeholder   = "nbg1 / fsn1 / hel1 / ash / hil / sin"
            )

            DropdownField(
                label         = "Default Server Type",
                value         = state.hetzner.defaultServerType,
                onValueChange = vm::onServerTypeChange,
                options       = state.serverTypes.map { it.name to it.label },
                placeholder   = "cx22 / cpx11 / cax11 / ..."
            )

            DropdownField(
                label         = "Default Image",
                value         = state.hetzner.defaultImage,
                onValueChange = vm::onImageChange,
                options       = state.images.map { it.identifier to it.label },
                placeholder   = "ubuntu-24.04 / debian-12 / ..."
            )

            Spacer(Modifier.height(12.dp))
            SectionHeader("SSH Key")
            Text(
                text  = "The app generated its own RSA 4096 key pair. The public key is automatically added to new Hetzner servers. The private key is stored encrypted on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedTextField(
                value         = state.sshPublicKey,
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Public Key") },
                minLines      = 3,
                maxLines      = 6,
                modifier      = Modifier.fillMaxWidth(),
                textStyle     = MaterialTheme.typography.bodySmall
            )

            var showRegenerateConfirm by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.copyPublicKeyToClipboard() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = { showRegenerateConfirm = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Autorenew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Regenerate")
                }
            }

            if (showRegenerateConfirm) {
                AlertDialog(
                    onDismissRequest = { showRegenerateConfirm = false },
                    title = { Text("Generate new key?") },
                    text  = { Text("The current private key will be deleted and a new RSA 4096 key pair will be generated. You may lose access to servers using this key.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.regenerateSshKey()
                            showRegenerateConfirm = false
                        }) { Text("Regenerate") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRegenerateConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader("GitHub")
            Text(
                text  = "Required for cloning private repositories on new servers. Create a token with repo scope at github.com/settings/tokens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            var showGithubToken by remember { mutableStateOf(false) }
            OutlinedTextField(
                value                = state.hetzner.githubToken,
                onValueChange        = vm::onGithubTokenChange,
                label                = { Text("Personal Access Token (optional)") },
                singleLine           = true,
                modifier             = Modifier.fillMaxWidth(),
                visualTransformation = if (showGithubToken) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showGithubToken = !showGithubToken }) {
                        Icon(
                            imageVector = if (showGithubToken) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )

            Spacer(Modifier.height(12.dp))
            SectionHeader("Terminal Theme")
            DropdownField(
                label         = "Terminal Theme",
                value         = terminalTheme.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = { themeName ->
                    TerminalTheme.values().firstOrNull {
                        it.name.replace("_", " ").lowercase() == themeName.lowercase()
                    }?.let { vm.setTerminalTheme(it) }
                },
                options       = TerminalTheme.values().map {
                    it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } to
                    it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }
                },
                placeholder   = "Select terminal theme"
            )

            Spacer(Modifier.height(12.dp))
            SectionHeader("Terminal Font")
            DropdownField(
                label         = "Terminal Font",
                value         = terminalFont.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = { fontName ->
                    TerminalFont.values().firstOrNull {
                        it.name.replace("_", " ").lowercase() == fontName.lowercase()
                    }?.let { vm.setTerminalFont(it) }
                },
                options       = TerminalFont.values().map {
                    it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } to
                    it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }
                },
                placeholder   = "Select terminal font"
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = { vm.save(onBack) },
                enabled  = state.isLoaded,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label:         String,
    value:         String,
    onValueChange: (String) -> Unit,
    options:       List<Pair<String, String>>,
    placeholder:   String
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { if (options.isNotEmpty()) expanded = !expanded }
    ) {
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            label         = { Text(label) },
            placeholder   = { Text(placeholder) },
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
            trailingIcon  = {
                if (options.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
        )
        if (options.isNotEmpty()) {
            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false },
                modifier         = Modifier.exposedDropdownSize(matchTextFieldWidth = true)
            ) {
                options.forEach { (v, lbl) ->
                    DropdownMenuItem(
                        text    = { Text(lbl) },
                        onClick = {
                            onValueChange(v)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
