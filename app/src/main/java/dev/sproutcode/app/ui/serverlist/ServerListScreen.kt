package dev.sproutcode.app.ui.serverlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sproutcode.app.data.Server
import dev.sproutcode.app.data.ThemeMode
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    onConnect:       (String) -> Unit,
    onAddManual:     () -> Unit,
    onCreateHetzner: () -> Unit,
    onEdit:          (String) -> Unit,
    onSettings:      () -> Unit,
    vm: ServerListViewModel = viewModel()
) {
    val servers     by vm.servers.collectAsStateWithLifecycle()
    val themeMode   by vm.appPrefs.themeMode.collectAsState()
    val deleteError by vm.deleteError.collectAsStateWithLifecycle()
    val serverStatuses by vm.serverStatuses.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refresh() }

    var pendingDelete by remember { mutableStateOf<Server?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Servers") },
                actions = {
                    IconButton(onClick = { vm.checkAllServerStatuses() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check status")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { vm.toggleTheme() }) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK) Icons.Default.LightMode
                                          else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "New server")
            }
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "No servers yet.\nTap + to add one.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = padding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                    val status = serverStatuses[server.id]
                    ServerRow(
                        server   = server,
                        status   = status,
                        onClick  = { onConnect(server.id) },
                        onEdit   = { onEdit(server.id) },
                        onDelete = { pendingDelete = server }
                    )
                }
                items(listOf("spacer")) { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState       = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent   = { Text("Create on Hetzner") },
                    supportingContent = { Text("Automatically set up with app SSH key") },
                    leadingContent    = {
                        Icon(Icons.Default.CloudUpload, contentDescription = null,
                             tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showAddSheet = false
                        onCreateHetzner()
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent   = { Text("Add manually") },
                    supportingContent = { Text("Enter IP, username and password") },
                    leadingContent    = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showAddSheet = false
                        onAddManual()
                    }
                )
            }
        }
    }

    deleteError?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.clearDeleteError() },
            title   = { Text("Hetzner deletion failed") },
            text    = { Text(err) },
            confirmButton = {
                TextButton(onClick = { vm.clearDeleteError() }) { Text("OK") }
            }
        )
    }

    pendingDelete?.let { srv ->
        if (srv.hetznerId != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title   = { Text("Delete server") },
                text    = { Text("\"${srv.name}\" was created on Hetzner Cloud. Also delete it from Hetzner?") },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            vm.delete(srv.id)
                            pendingDelete = null
                        }) { Text("Local only") }
                        TextButton(
                            onClick = {
                                vm.deleteWithHetzner(srv)
                                pendingDelete = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Delete from Hetzner") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title   = { Text("Delete server") },
                text    = { Text("\"${srv.name}\" will be deleted. Are you sure?") },
                confirmButton = {
                    TextButton(
                        onClick = { vm.delete(srv.id); pendingDelete = null },
                        colors  = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ServerRow(
    server:   Server,
    status:   ServerStatus?,
    onClick:  () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            if (status != null) {
                val statusColor = when {
                    status.isChecking -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    status.isOnline -> androidx.compose.ui.graphics.Color(0xFF3FB950)
                    else -> MaterialTheme.colorScheme.error
                }
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = if (status.isOnline) "Online" else "Offline",
                    tint = statusColor,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = server.name.ifBlank { "${server.username}@${server.host}" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text  = "${server.username}@${server.host}:${server.port}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
