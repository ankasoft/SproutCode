package dev.sproutcode.app

import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.sproutcode.app.data.AppPrefs
import dev.sproutcode.app.navigation.AppNavGraph
import dev.sproutcode.app.notification.NotificationHelper
import dev.sproutcode.app.ui.theme.SproutCodeTheme

class MainActivity : ComponentActivity() {
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)
        
        // Uygulama çalışırken ekranı açık tut
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // WakeLock al - uygulama arka planda çalışırken uykuya geçmesin
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SproutCode::TerminalWakeLock"
        )
        wakeLock?.acquire(10*60*1000L) // 10 dakika
        
        setContent {
            val appPrefs  = AppPrefs.getInstance(applicationContext)
            val themeMode by appPrefs.themeMode.collectAsState()
            SproutCodeTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // WakeLock'u serbest bırak
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
