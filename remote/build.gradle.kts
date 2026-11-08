import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Misma version que el launcher: se lee de app/build.gradle.kts para que nunca se desfasen.
val appGradle = rootProject.file("app/build.gradle.kts").readText()
val vName = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(appGradle)!!.groupValues[1]
val vCode = Regex("versionCode\\s*=\\s*(\\d+)").find(appGradle)!!.groupValues[1].toInt()

// Misma llave de firma que el launcher (keystore.properties local o variables de GitHub Actions).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(prop: String, env: String): String? =
    (keystoreProps.getProperty(prop) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val ksFile = signingValue("storeFile", "KEYSTORE_FILE")
val ksPass = signingValue("storePassword", "KEYSTORE_PASSWORD")
val ksAlias = signingValue("keyAlias", "KEY_ALIAS")
val ksKeyPass = signingValue("keyPassword", "KEY_PASSWORD")
val hasOwnKey = ksFile != null && ksPass != null && ksAlias != null && ksKeyPass != null

android {
    namespace = "com.mini.launcher.remote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mini.launcher.remote"
        minSdk = 26
        targetSdk = 34
        versionCode = vCode
        versionName = vName
    }
    signingConfigs {
        if (hasOwnKey) {
            create("release") {
                storeFile = rootProject.file(ksFile!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = if (hasOwnKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    // Escaner de QR de Google (sin permiso de camara; necesita Google Play Services en el celular).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
