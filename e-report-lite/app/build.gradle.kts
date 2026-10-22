import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val nativeRelease = JsonSlurper().parse(rootProject.file("native-release.json")) as Map<*, *>
fun signingValue(name: String) = System.getenv(name) ?: providers.gradleProperty(name).orNull
val signingFile = signingValue("E_REPORT_UPLOAD_STORE_FILE")
val signingPassword = signingValue("E_REPORT_UPLOAD_STORE_PASSWORD")
val signingAlias = signingValue("E_REPORT_UPLOAD_KEY_ALIAS")
val signingKeyPassword = signingValue("E_REPORT_UPLOAD_KEY_PASSWORD")
val hasSigning = listOf(signingFile, signingPassword, signingAlias, signingKeyPassword).all { !it.isNullOrBlank() }
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) } && !hasSigning) {
    throw GradleException("Release requires E_REPORT_UPLOAD_* signing configuration")
}

android {
    namespace = "com.mnnu.ereport.lite"
    compileSdk = 36
    defaultConfig {
        applicationId = nativeRelease["applicationId"] as String
        minSdk = 26
        targetSdk = 36
        versionCode = (nativeRelease["versionCode"] as Number).toInt()
        versionName = nativeRelease["version"] as String
        buildConfigField("String", "RUNTIME_VERSION", "\"${nativeRelease["runtimeVersion"]}\"")
    }
    signingConfigs {
        create("release") {
            if (hasSigning) {
                storeFile = file(signingFile!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}

dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("androidx.core:core:1.17.0")
    testImplementation("junit:junit:4.13.2")
}
