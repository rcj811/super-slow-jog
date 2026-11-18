package com.rcj811.slowjog

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** 閃退紀錄：出錯時把原因存下來，下次開 App 顯示，可一鍵複製給開發者。 */
object CrashGuard {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, prev))
    }

    private class Handler(val app: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText(
                    ("版本 ${AppUpdater.currentVersion(app)}｜Android ${Build.VERSION.RELEASE}｜${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "執行緒 ${t.name}\n\n$sw").take(20_000)
                )
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun showIfAny(activity: Activity) {
        val f = file(activity)
        if (!f.exists()) return
        val text = runCatching { f.readText() }.getOrDefault("")
        AlertDialog.Builder(activity)
            .setTitle("上次發生錯誤而關閉")
            .setMessage(text.take(3000))
            .setPositiveButton("複製錯誤") { _, _ ->
                activity.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("crash", text))
                Toast.makeText(activity, "已複製，可以貼給開發者", Toast.LENGTH_SHORT).show()
                f.delete()
            }
            .setNegativeButton("關閉") { _, _ -> f.delete() }
            .show()
    }
}
