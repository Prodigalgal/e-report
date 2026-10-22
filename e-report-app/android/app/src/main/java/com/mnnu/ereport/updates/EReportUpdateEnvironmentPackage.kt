package com.mnnu.ereport.updates

import android.content.Intent
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager

class EReportUpdateEnvironmentPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> = listOf(UpdateEnvironmentModule(context), AppPackageVerificationModule(context))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

private class UpdateEnvironmentModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    override fun getName() = "EReportUpdateEnvironment"
    override fun getConstants(): Map<String, Any> = mapOf(
        "startupEnvironment" to EReportUpdateEnvironment.startupEnvironment,
        "startupApiBaseUrl" to EReportUpdateEnvironment.startupApiBaseUrl
    )
    @ReactMethod
    fun save(environment: String, apiBaseUrl: String, promise: Promise) {
        try { promise.resolve(EReportUpdateEnvironment.save(reactApplicationContext, environment, apiBaseUrl)) }
        catch (exception: Exception) { promise.reject("UPDATE_ENVIRONMENT_INVALID", "无法保存原生更新环境", exception) }
    }
    @ReactMethod
    fun restart(promise: Promise) {
        val activity = reactApplicationContext.currentActivity
        if (activity == null) { promise.reject("ACTIVITY_UNAVAILABLE", "应用未处于前台，请重新打开应用。"); return }
        activity.runOnUiThread {
            try {
                activity.startActivity(Intent(activity, UpdateRestartActivity::class.java)
                    .putExtra("processId", android.os.Process.myPid()))
                promise.resolve(null)
            } catch (exception: Exception) { promise.reject("RESTART_FAILED", "无法重启应用，请完全关闭后重新打开。", exception) }
        }
    }
}
