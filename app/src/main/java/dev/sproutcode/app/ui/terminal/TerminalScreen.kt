package dev.sproutcode.app.ui.terminal

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    LaunchedEffect(serverId) { vm.setServerId(serverId) }

    BackHandler {
        vm.disconnect()
        onDisconnect()
    }

    LaunchedEffect(uiState) {
        if (uiState is TerminalUiState.Disconnected) onDisconnect()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        if (uiState !is TerminalUiState.Error) {
            TerminalViewContainer(vm = vm)
        }

        when (val state = uiState) {
            is TerminalUiState.Connecting -> {
                CircularProgressIndicator(
                    modifier    = Modifier.align(Alignment.Center),
                    color       = TerminalPrimary,
                    trackColor  = TerminalSurface,
                    strokeWidth = 2.dp
                )
            }
            is TerminalUiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBack  = { vm.disconnect(); onDisconnect() }
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun TerminalViewContainer(vm: TerminalViewModel) {
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

            val sessionClient = buildSessionClient()
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
            tv.setTerminalViewClient(buildViewClient(vm, ctx, tv))
            vm.attachTerminalView(tv)

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

private fun buildSessionClient(): TerminalSessionClient = object : TerminalSessionClient {
    override fun onTextChanged(s: TerminalSession)                                  {}
    override fun onTitleChanged(s: TerminalSession)                                 {}
    override fun onSessionFinished(s: TerminalSession)                              {}
    override fun onCopyTextToClipboard(s: TerminalSession, text: String?)           {}
    override fun onPasteTextFromClipboard(s: TerminalSession?)                      {}
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

private fun buildViewClient(vm: TerminalViewModel, ctx: Context, tv: TerminalView): TerminalViewClient =
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
