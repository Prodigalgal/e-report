# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# react-native-reanimated
-keep class com.swmansion.reanimated.** { *; }
-keep class com.facebook.react.turbomodule.** { *; }

# AMap ships an optional soft-RTK integration point without its private implementation.
# Keep this scoped to the single vendor class so every other missing dependency remains fatal.
-dontwarn com.amap.ams.gnss.GnssSoftLocator

# Add any project specific keep options here:
-renamesourcefileattribute SourceFile
-keepattributes LineNumberTable,SourceFile
