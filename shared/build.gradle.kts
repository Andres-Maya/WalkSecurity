plugins {
    alias(libs.plugins.android.library)
}

// Protocolo compartido reloj <-> teléfono (Wearable Data Layer)
android {
    namespace = "com.andres.walksecurity.shared"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.play.services.wearable)
    testImplementation(libs.junit)
}
