package com.mnnu.ereport

import android.app.Application
import android.app.ActivityManager
import android.content.res.Configuration
import android.os.Build
import android.os.Process

import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactPackage
import com.facebook.react.ReactHost
import com.facebook.react.common.ReleaseLevel
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.mnnu.ereport.location.EReportLocationPackage
import com.mnnu.ereport.navigation.EReportNavigationPackage
import com.mnnu.ereport.observability.EReportCrashRecorder
import com.mnnu.ereport.observability.EReportObservabilityPackage
import com.mnnu.ereport.security.EReportDeviceSecurityPackage
import com.mnnu.ereport.updates.EReportUpdateEnvironment
import com.mnnu.ereport.updates.EReportUpdateEnvironmentPackage

import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ExpoReactHostFactory

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    ExpoReactHostFactory.getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(EReportLocationPackage())
          add(EReportNavigationPackage())
          add(EReportObservabilityPackage())
          add(EReportDeviceSecurityPackage())
          add(EReportUpdateEnvironmentPackage())
        }
    )
  }

  override fun onCreate() {
    super.onCreate()
    val processName = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
      else (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
        ?.firstOrNull { it.pid == Process.myPid() }?.processName.orEmpty()
    if (processName.endsWith(":restart")) return
    EReportUpdateEnvironment.configure(this)
    EReportCrashRecorder.install(this)
    DefaultNewArchitectureEntryPoint.releaseLevel = try {
      ReleaseLevel.valueOf(BuildConfig.REACT_NATIVE_RELEASE_LEVEL.uppercase())
    } catch (e: IllegalArgumentException) {
      ReleaseLevel.STABLE
    }
    loadReactNative(this)
    ApplicationLifecycleDispatcher.onApplicationCreate(this)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
  }
}
