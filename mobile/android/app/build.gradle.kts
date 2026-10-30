import java.util.Base64

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.dailyworklog.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Same id as the earlier Android app, so this installs as an update and keeps its data.
        applicationId = "com.dailyworklog.app"
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // An app can only be updated by an APK signed with the same key. The earlier app was built in
    // Termux and signed with Termux's ~/.android/debug.keystore, so the online build signs with
    // that same keystore, given base64-encoded in the ANDROID_KEYSTORE_BASE64 secret.
    val keystoreB64 = System.getenv("ANDROID_KEYSTORE_BASE64")
    signingConfigs {
        if (!keystoreB64.isNullOrBlank()) {
            create("termux") {
                val f = layout.buildDirectory.file("termux.keystore").get().asFile
                f.parentFile.mkdirs()
                f.writeBytes(Base64.getMimeDecoder().decode(keystoreB64))
                storeFile = f
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystoreB64.isNullOrBlank()) signingConfigs.getByName("debug")
            else signingConfigs.getByName("termux")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
