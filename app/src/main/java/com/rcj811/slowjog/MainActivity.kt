package com.rcj811.slowjog

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
        AvatarStore.load(this)
        volumeControlStream = AudioManager.STREAM_MUSIC // 音量鍵直接調媒體音量（節拍與語音）
        applyOrientation(Prefs.settings.value.screenMode)
        lifecycleScope.launch {
            Prefs.settings.map { it.screenMode }.distinctUntilChanged().collect { applyOrientation(it) }
        }
        // 每日提醒：開 App 時與設定改變時重新排程
        lifecycleScope.launch {
            Prefs.settings.map { Triple(it.dailyReminderOn, it.dailyHour, it.dailyMinute) }
                .distinctUntilChanged()
                .collect { DailyReminder.schedule(this@MainActivity) }
        }
        setContent {
            JogTheme {
                AppRoot(onStart = ::startRun)
            }
        }
    }

    private fun applyOrientation(mode: ScreenMode) {
        val want = when (mode) {
            ScreenMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        if (requestedOrientation != want) requestedOrientation = want
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
