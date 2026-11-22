plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Número de build (GitHub Actions pasa github.run_number en BUILD_NUMBER).
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()

// Datos de firma: solo desde variables de entorno (nunca en el repo).
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.juanma.ytsinshorts"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.juanma.ytsinshorts"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(17)
}
