package com.mnnu.ereport.updates

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.content.Intent

/** Independent process restarts the main process so Expo reads the new scope before initialization. */
class UpdateRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousProcess = intent.getIntExtra("processId", -1)
        if (previousProcess > 0 && previousProcess != Process.myPid()) Process.killProcess(previousProcess)
        Handler(Looper.getMainLooper()).postDelayed({
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            finish()
            Process.killProcess(Process.myPid())
        }, 400)
    }
}
