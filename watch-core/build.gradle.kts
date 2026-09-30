plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
}

/*
 * Interfaz, lógica y modelos del reloj — un solo código para tres usos:
 *  - android: reloj Wear OS real (wear/) y reloj emulado dentro del teléfono (app/)
 *  - desktop: emulador del reloj en el computador (watch-emulator/)
 */
kotlin {
    android {
        namespace = "com.andres.walksecurity.watch"
        compileSdk = 37
        minSdk = 24
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
        }
        androidMain.dependencies {
            // DataMap del Wearable Data Layer (protocolo reloj <-> teléfono)
            api(libs.play.services.wearable)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
