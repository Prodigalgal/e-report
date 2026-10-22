package com.mnnu.ereport.observability

import android.app.Application
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

object EReportCrashRecorder {
  private const val PREFERENCES_NAME = "e_report_observability"
  private val installed = AtomicBoolean(false)

  fun install(application: Application) {
    if (!installed.compareAndSet(false, true)) return
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
          .edit()
          .putLong("crash_timestamp", System.currentTimeMillis())
          .putString("crash_thread", sanitize(thread.name, 100))
          .putString("crash_type", sanitize(throwable.javaClass.name, 200))
          .putString("crash_message", sanitize(throwable.message.orEmpty(), 1000))
          .putString("crash_stack", sanitize(throwable.stackTraceToString(), 16000))
          .commit()
      } finally {
        if (previousHandler != null) {
          previousHandler.uncaughtException(thread, throwable)
        } else {
          Process.killProcess(Process.myPid())
        }
      }
    }
  }

  fun preferencesName() = PREFERENCES_NAME

  private fun sanitize(value: String, maximumLength: Int): String {
    return value
      .replace(Regex("(?i)bearer\\s+[a-z0-9._~+/=-]+"), "Bearer [redacted]")
      .replace(Regex("(?i)(password|passwd|token|authorization|cookie|captcha)\\s*[:=]\\s*[^\\s,;&]+")) {
        "${it.groupValues[1]}=[redacted]"
      }
      .replace(Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)"), "[phone-redacted]")
      .replace(Regex("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"), "[email-redacted]")
      .replace(Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}"), "[mac-redacted]")
      .replace(Regex("(?i)(latitude|longitude|lat|lng)\\s*[:=]\\s*-?\\d{1,3}(?:\\.\\d+)?")) {
        "${it.groupValues[1]}=[coordinate-redacted]"
      }
      .take(maximumLength)
  }
}
