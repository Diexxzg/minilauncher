import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Llave de firma propia (opcional). Se lee de keystore.properties (local) o de variables de
// entorno (GitHub Actions). Si no existe, se firma con la llave de debug como antes.
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
    namespace = "com.mini.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mini.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "2.7"
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
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Con llave propia si esta configurada; si no, con la de debug (sideload directo).
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
}
