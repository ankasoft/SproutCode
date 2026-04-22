package dev.sproutcode.app.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sproutcode.app.ui.theme.TerminalBackground
import dev.sproutcode.app.ui.theme.TerminalError
import dev.sproutcode.app.ui.theme.TerminalOnSurface
import dev.sproutcode.app.ui.theme.TerminalPrimary
import dev.sproutcode.app.ui.theme.TerminalSurface
import dev.sproutcode.app.ui.theme.toColorScheme
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@Composable
fun TerminalScreen(
    serverId:     String,
    onDisconnect: () -> Unit,
    vm: TerminalViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val terminalTheme by vm.terminalTheme.collectAsStateWithLifecycle()
    val colorScheme = terminalTheme.toColorScheme()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val currentSearchIndex by vm.currentSearchIndex.collectAsStateWithLifecycle()
    var isSearchVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(serverId) { vm.setServerId(serverId) }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            vm.disconnect()
        }
    }

    BackHandler {
        if (isSearchVisible) {
            isSearchVisible = false
            vm.toggleSearchMode()
        } else {
            vm.disconnect()
            onDisconnect()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is TerminalUiState.Disconnected) onDisconnect()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        if (uiState !is TerminalUiState.Error && uiState !is TerminalUiState.VerifyHostKey) {
            TerminalViewContainer(vm = vm, onSearchRequest = { isSearchVisible = true })
        }

        // Search overlay
        if (isSearchVisible && uiState is TerminalUiState.Connected) {
            SearchOverlay(
                query = searchQuery,
                results = searchResults,
                currentIndex = currentSearchIndex,
                colorScheme = colorScheme,
                onQueryChange = { vm.setSearchQuery(it) },
                onNext = { vm.nextSearchResult() },
                onPrevious = { vm.previousSearchResult() },
                onClose = {
                    isSearchVisible = false
                    vm.toggleSearchMode()
                }
            )
        }

        when (val state = uiState) {
            is TerminalUiState.Connecting -> {
                CircularProgressIndicator(
                    modifier    = Modifier.align(Alignment.Center),
                    color       = colorScheme.primary,
                    trackColor  = colorScheme.surface,
                    strokeWidth = 2.dp
                )
            }
            is TerminalUiState.Reconnecting -> {
                ReconnectingOverlay(vm = vm, colorScheme = colorScheme)
            }
            is TerminalUiState.VerifyHostKey -> {
                HostKeyVerificationDialog(
                    host = state.host,
                    port = state.port,
                    fingerprint = state.fingerprint,
                    isChanged = state.isChanged,
                    expectedFingerprint = state.expectedFingerprint,
                    colorScheme = colorScheme,
                    onTrust = { host, port, fingerprint ->
                        vm.trustHostAndConnect(host, port, fingerprint)
                    },
                    onCancel = { vm.disconnect(); onDisconnect() }
                )
            }
            is TerminalUiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    colorScheme = colorScheme,
                    onBack  = { vm.disconnect(); onDisconnect() }
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun TerminalViewContainer(
    vm: TerminalViewModel,
    onSearchRequest: () -> Unit = {}
) {
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
        factory = { ctx ->
            val tv = TerminalView(ctx, null)
            tv.setTextSize(vm.fontSize)
            tv.isFocusableInTouchMode = true

            val sessionClient = buildSessionClient(ctx)
            // Use a no-op shell command that exits naturally. Do NOT call finishIfRunning():
            // sh -c "" exits so fast that the PID may be reused before finishIfRunning sends
            // SIGKILL, which could kill our own process. Letting it exit on its own is safe.
            val session = TerminalSession(
                "/system/bin/sh",
                "/",
                arrayOf("-c", ":"),                         // ":" builtin is instant no-op
                arrayOf("TERM=xterm-256color", "HOME=/"),
                2000,
                sessionClient
            )

            tv.attachSession(session)
            tv.setTerminalViewClient(buildViewClient(vm, ctx, tv, onSearchRequest))
            vm.attachTerminalView(tv)

            // Apply font
            val font = vm.terminalFont.value
            val typeface = when (font) {
                dev.sproutcode.app.data.TerminalFont.DEFAULT -> android.graphics.Typeface.MONOSPACE
                dev.sproutcode.app.data.TerminalFont.JETBRAINS_MONO -> {
                    try {
                        android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/JetBrainsMono-Regular.ttf")
                    } catch (e: Exception) {
                        android.graphics.Typeface.MONOSPACE
                    }
                }
                dev.sproutcode.app.data.TerminalFont.FIRA_CODE -> {
                    try {
                        android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/FiraCode-Regular.ttf")
                    } catch (e: Exception) {
                        android.graphics.Typeface.MONOSPACE
                    }
                }
                dev.sproutcode.app.data.TerminalFont.SOURCE_CODE_PRO -> {
                    try {
                        android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/SourceCodePro-Regular.ttf")
                    } catch (e: Exception) {
                        android.graphics.Typeface.MONOSPACE
                    }
                }
            }
            tv.setTypeface(typeface)

            var sessionReady = false
            tv.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0) {
                    view.post {
                        val cols = session.emulator?.mColumns ?: 80
                        val rows = session.emulator?.mRows    ?: 24
                        if (!sessionReady) {
                            sessionReady = true
                            vm.onSessionReady(session)
                        }
                        vm.onTerminalSizeChanged(cols, rows)
                    }
                }
            }

            tv.post {
                tv.requestFocus()
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(tv, InputMethodManager.SHOW_FORCED)
            }

            tv
        },
        update = { tv ->
            tv.post {
                if (!tv.isFocused) tv.requestFocus()
            }
        }
    )
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Text(
            text  = "Connection Error",
            color = TerminalError,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = message,
            color = TerminalOnSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBack,
            colors  = ButtonDefaults.buttonColors(containerColor = TerminalPrimary)
        ) {
            Text("Back", color = Color.White)
        }
    }
}

@Composable
private fun SearchOverlay(
    query: String,
    results: List<Int>,
    currentIndex: Int,
    colorScheme: dev.sproutcode.app.ui.theme.TerminalColorScheme,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface.copy(alpha = 0.95f))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search", color = colorScheme.onSurface.copy(alpha = 0.6f)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(color = colorScheme.onSurface)
            )
            
            if (results.isNotEmpty()) {
                Text(
                    text = "${currentIndex + 1}/${results.size}",
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            IconButton(onClick = onPrevious, enabled = results.isNotEmpty()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous",
                    tint = if (results.isNotEmpty()) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = onNext, enabled = results.isNotEmpty()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next",
                    tint = if (results.isNotEmpty()) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                    tint = colorScheme.onSurface
                )
            }
        }
    }
}

private fun buildSessionClient(ctx: Context): TerminalSessionClient = object : TerminalSessionClient {
    private val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun onTextChanged(s: TerminalSession)                                  {}
    override fun onTitleChanged(s: TerminalSession)                                 {}
    override fun onSessionFinished(s: TerminalSession)                              {}
    override fun onCopyTextToClipboard(s: TerminalSession, text: String?)           {
        text?.let {
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", it))
        }
    }
    override fun onPasteTextFromClipboard(s: TerminalSession?)                      {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: return
            s?.write(text)
        }
    }
    override fun onBell(s: TerminalSession)                                         {}
    override fun onColorsChanged(s: TerminalSession)                                {}
    override fun onTerminalCursorStateChange(state: Boolean)                        {}
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
    override fun logError(tag: String?, message: String?)                           {}
    override fun logWarn(tag: String?, message: String?)                            {}
    override fun logInfo(tag: String?, message: String?)                            {}
    override fun logDebug(tag: String?, message: String?)                           {}
    override fun logVerbose(tag: String?, message: String?)                         {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?)                         {}
}

@Composable
private fun HostKeyVerificationDialog(
    host: String,
    port: Int,
    fingerprint: String,
    isChanged: Boolean,
    expectedFingerprint: String?,
    colorScheme: dev.sproutcode.app.ui.theme.TerminalColorScheme,
    onTrust: (String, Int, String) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Text(
            text  = if (isChanged) "WARNING: Host Key Changed!" else "Unknown Host",
            color = if (isChanged) colorScheme.error else colorScheme.primary,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = "$host:$port",
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "Fingerprint:",
            color = colorScheme.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = fingerprint,
            color = colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (isChanged && expectedFingerprint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "Expected:",
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = expectedFingerprint,
                color = colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "This may indicate a man-in-the-middle attack!",
                color = colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCancel,
                colors  = ButtonDefaults.buttonColors(containerColor = colorScheme.surface)
            ) {
                Text("Cancel", color = colorScheme.onSurface)
            }
            Button(
                onClick = { onTrust(host, port, fingerprint) },
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (isChanged) colorScheme.error else colorScheme.primary
                )
            ) {
                Text(if (isChanged) "Trust Anyway" else "Trust Host", color = Color.White)
            }
        }
    }
}

@Composable
private fun ReconnectingOverlay(
    vm: TerminalViewModel,
    colorScheme: dev.sproutcode.app.ui.theme.TerminalColorScheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background.copy(alpha = 0.9f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color      = colorScheme.primary,
            trackColor = colorScheme.surface,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = "Reconnecting...",
            color = colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.cancelReconnect() },
            colors  = ButtonDefaults.buttonColors(containerColor = colorScheme.surface)
        ) {
            Text("Cancel", color = colorScheme.onSurface)
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    colorScheme: dev.sproutcode.app.ui.theme.TerminalColorScheme,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Text(
            text  = "Connection Error",
            color = colorScheme.error,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = message,
            color = colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBack,
            colors  = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            Text("Back", color = Color.White)
        }
    }
}

private fun buildViewClient(vm: TerminalViewModel, ctx: Context, tv: TerminalView, onSearchRequest: () -> Unit = {}): TerminalViewClient =
    object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                vm.adjustFontSize(if (scale > 1.0f) 1 else -1)
                return 1.0f
            }
            return scale
        }
        override fun onSingleTapUp(e: MotionEvent?) {
            tv.requestFocus()
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(tv, InputMethodManager.SHOW_FORCED)
        }
        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput()      = true
        override fun shouldUseCtrlSpaceWorkaround()     = false
        override fun isTerminalViewSelected()           = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onLongPress(e: MotionEvent?)       = false
        override fun readControlKey()                   = false
        override fun readAltKey()                       = false
        override fun readShiftKey()                     = false
        override fun readFnKey()                        = false
        override fun onEmulatorSet()                    {}
        override fun logError(tag: String?, message: String?)   {}
        override fun logWarn(tag: String?, message: String?)    {}
        override fun logInfo(tag: String?, message: String?)    {}
        override fun logDebug(tag: String?, message: String?)   {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
            // Ctrl+F for search
            if (e != null && e.isCtrlPressed && keyCode == KeyEvent.KEYCODE_F) {
                onSearchRequest()
                return true
            }
            // Ctrl+Shift+V for paste
            if (e != null && e.isCtrlPressed && e.isShiftPressed && keyCode == KeyEvent.KEYCODE_V) {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: return true
                    vm.writeToSsh(text)
                }
                return true
            }
            // Ctrl+Shift+C for copy (handled by TerminalView)
            if (e != null && e.isCtrlPressed && e.isShiftPressed && keyCode == KeyEvent.KEYCODE_C) {
                return false // Let TerminalView handle copy mode
            }

            val seq: String? = when (keyCode) {
                KeyEvent.KEYCODE_DEL         -> "\u007F"
                KeyEvent.KEYCODE_FORWARD_DEL -> "\u001B[3~"
                KeyEvent.KEYCODE_ENTER       -> "\r"
                KeyEvent.KEYCODE_DPAD_UP     -> "\u001B[A"
                KeyEvent.KEYCODE_DPAD_DOWN   -> "\u001B[B"
                KeyEvent.KEYCODE_DPAD_RIGHT  -> "\u001B[C"
                KeyEvent.KEYCODE_DPAD_LEFT   -> "\u001B[D"
                KeyEvent.KEYCODE_TAB         -> "\t"
                KeyEvent.KEYCODE_ESCAPE      -> "\u001B"
                KeyEvent.KEYCODE_PAGE_UP     -> "\u001B[5~"
                KeyEvent.KEYCODE_PAGE_DOWN   -> "\u001B[6~"
                KeyEvent.KEYCODE_MOVE_HOME   -> "\u001B[H"
                KeyEvent.KEYCODE_MOVE_END    -> "\u001B[F"
                else -> null
            }
            if (seq != null) {
                vm.writeToSsh(seq)
                return true
            }
            // Ctrl+letter combinations from hardware keyboard
            if (e != null && e.isCtrlPressed) {
                val ucode = e.unicodeChar
                if (ucode > 0) {
                    vm.writeToSsh(String(Character.toChars(ucode)).toByteArray())
                    return true
                }
            }
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
            val bytes = when {
                ctrlDown && codePoint in 'a'.code..'z'.code ->
                    byteArrayOf((codePoint - 'a'.code + 1).toByte())
                ctrlDown && codePoint in 'A'.code..'Z'.code ->
                    byteArrayOf((codePoint - 'A'.code + 1).toByte())
                else ->
                    String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
            }
            vm.writeToSsh(bytes)
            return true
        }
    }
