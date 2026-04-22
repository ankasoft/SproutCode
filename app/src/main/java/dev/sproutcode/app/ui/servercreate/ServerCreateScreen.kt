package dev.sproutcode.app.ui.servercreate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sproutcode.app.notification.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerCreateScreen(
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
    vm: ServerCreateViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Ekran her açıldığında ViewModel state'ini sıfırla
    LaunchedEffect(Unit) {
        vm.resetState()
    }

    DisposableEffect(lifecycleOwner) {
        vm.observeWorkProgress(lifecycleOwner)
        onDispose {
            vm.removeWorkObserver()
        }
    }

    LaunchedEffect(state.createdServerId) {
        state.createdServerId?.let { 
            android.util.Log.d("ServerCreateScreen", "Navigating to terminal with serverId=$it")
            onCreated(it)
            vm.resetCreatedServerId()
        }
    }

    // Debug: Ekran açıldığını logla
    LaunchedEffect(Unit) {
        android.util.Log.e("ServerCreateScreen", "Screen opened! tokenMissing=${state.tokenMissing}, createdServerId=${state.createdServerId}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create on Hetzner") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isCreating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.tokenMissing) {
                TokenMissingView(onBack)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "The app's SSH key is injected automatically. Terminal opens when the server is ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value         = state.name,
                        onValueChange = vm::onNameChange,
                        label         = { Text("Server name (optional)") },
                        placeholder   = { Text("auto: sproutcode-<timestamp>") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        enabled       = !state.isCreating
                    )

                    DropdownField(
                        label         = "Location",
                        value         = state.location,
                        onValueChange = vm::onLocationChange,
                        options       = state.locations.map { it.name to it.label },
                        placeholder   = "nbg1 / fsn1 / hel1 / ...",
                        enabled       = !state.isCreating
                    )

                    DropdownField(
                        label         = "Server type",
                        value         = state.serverType,
                        onValueChange = vm::onServerTypeChange,
                        options       = state.serverTypes.map { it.name to it.label },
                        placeholder   = "cx22 / cpx11 / ...",
                        enabled       = !state.isCreating
                    )

                    DropdownField(
                        label         = "Image",
                        value         = state.image,
                        onValueChange = vm::onImageChange,
                        options       = state.images.map { it.identifier to it.label },
                        placeholder   = "ubuntu-24.04 / snapshot-id",
                        enabled       = !state.isCreating
                    )

                    OutlinedTextField(
                        value         = state.githubRepoUrl,
                        onValueChange = vm::onGithubRepoUrlChange,
                        label         = { Text("GitHub Repository URL (optional)") },
                        placeholder   = { Text("github.com/user/repo") },
                        supportingText = { Text("Leave empty to skip. Token configured in Settings.") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        enabled       = !state.isCreating
                    )

                    state.optionsError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    state.createError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick  = {
                            vm.create { title, message, serverId ->
                                NotificationHelper.showServerCreationNotification(
                                    context,
                                    title,
                                    message,
                                    serverId
                                )
                            }
                        },
                        enabled  = !state.isCreating && !state.isLoadingOptions
                                   && state.location.isNotBlank()
                                   && state.serverType.isNotBlank()
                                   && state.image.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(if (state.isCreating) "Creating..." else "Create")
                    }
                }
            }

            if (state.isCreating) {
                ProgressOverlay(text = state.progress)
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
    placeholder:   String,
    enabled:       Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { if (enabled && options.isNotEmpty()) expanded = !expanded }
    ) {
        OutlinedTextField(
            value         = value,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            placeholder   = { Text(placeholder) },
            singleLine    = true,
            enabled       = enabled && options.isNotEmpty(),
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
private fun ProgressOverlay(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                color       = MaterialTheme.colorScheme.primary,
                modifier    = Modifier.size(48.dp),
                strokeWidth = 3.dp
            )
            Text(
                text  = text,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TokenMissingView(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Add a Hetzner API token in Settings first.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
