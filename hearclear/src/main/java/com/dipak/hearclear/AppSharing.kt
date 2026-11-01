package com.dipak.hearclear

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Share the installed universal APK, not a link to an older unrestricted release. */
object AppSharing {
    private val busy = AtomicBoolean()
    fun share(activity: Activity) {
        if (!busy.compareAndSet(false, true)) return
        Toast.makeText(activity, "Preparing HearClear APK…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                val directory = File(activity.cacheDir, "shared_apk").apply { mkdirs() }
                val apk = File(directory, "HearClear-${BuildConfig.VERSION_NAME}.apk")
                val staging = File.createTempFile("package-", ".tmp", directory)
                try {
                    File(activity.applicationInfo.sourceDir).copyTo(staging, overwrite = true)
                    check(staging.renameTo(apk)) { "Could not prepare shared APK" }
                } finally { staging.delete() }
                FileProvider.getUriForFile(activity, activity.packageName + ".sharing", apk)
            }
            activity.runOnUiThread {
                busy.set(false)
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { uri ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(activity.contentResolver, "HearClear APK", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { activity.startActivity(Intent.createChooser(send, "Share HearClear APK")) }
                        .onFailure { Toast.makeText(activity, "No app could share this APK.", Toast.LENGTH_LONG).show() }
                }.onFailure { Toast.makeText(activity, "Could not prepare the APK. Please try again.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
