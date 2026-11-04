package com.rcj811.slowjog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.rcj811.slowjog.ui.AppRoot
import com.rcj811.slowjog.ui.JogTheme

class MainActivity : ComponentActivity() {

    private var pendingStart = false

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // 不論同意與否都開始（拒絕只是看不到通知列控制）
        if (pendingStart) { pendingStart = false; JogService.start(this) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        SessionStore.load(this)
        setContent {
            JogTheme {
                AppRoot(onStart = ::startRun)
            }
        }
    }

    private fun startRun() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        JogService.start(this)
    }
}
