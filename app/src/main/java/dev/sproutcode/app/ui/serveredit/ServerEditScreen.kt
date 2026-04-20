package dev.sproutcode.app.ui.serveredit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    serverId: String?,
    onBack:   () -> Unit,
    vm: ServerEditViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) { vm.load(serverId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (serverId == null) "New Server" else "Edit Server") },
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value         = state.name,
                onValueChange = vm::onNameChange,
                label         = { Text("Name (optional)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value           = state.host,
                onValueChange   = vm::onHostChange,
                label           = { Text("IP / Hostname") },
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value           = state.port,
                onValueChange   = vm::onPortChange,
                label           = { Text("Port") },
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value         = state.username,
                onValueChange = vm::onUsernameChange,
                label         = { Text("Username") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value                = state.password,
                onValueChange        = vm::onPasswordChange,
                label                = { Text("Password") },
                singleLine           = true,
                modifier             = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector        = if (showPassword) Icons.Default.VisibilityOff
                                                 else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = { vm.save(onBack) },
                enabled  = vm.isValid() && state.isLoaded,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Save")
            }
        }
    }
}
