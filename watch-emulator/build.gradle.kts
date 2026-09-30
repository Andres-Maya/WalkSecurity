import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Emulador del reloj para el computador (Windows/macOS/Linux).
// Usa la MISMA interfaz y lógica que el reloj Wear OS (watch-core); el teléfono se simula.
//   ./gradlew :watch-emulator:run                                   -> abre la ventana
//   ./gradlew :watch-emulator:run --args="--export ../docs/reloj"   -> exporta capturas PNG
//   ./gradlew :watch-emulator:createDistributable                   -> .exe sin necesidad de Gradle
dependencies {
    implementation(project(":watch-core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.mp.material3)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.andres.walksecurity.emulator.MainKt"
        // El JBR de Android Studio (con el que suele correr Gradle) no trae jpackage:
        // para generar el .exe se usa el JDK de JAVA_HOME (p. ej. C:\Program Files\Java\jdk-26).
        providers.environmentVariable("JAVA_HOME").orNull?.let { javaHome = it }
        nativeDistributions {
            // Sugeridos por ./gradlew :watch-emulator:suggestRuntimeModules (sin ellos el .exe no abre)
            modules("java.instrument", "jdk.unsupported")
            targetFormats(TargetFormat.Msi)
            packageName = "WalkSecurityReloj"
            packageVersion = "1.0.0"
            description = "Emulador del reloj de WalkSecurity"
            vendor = "WalkSecurity"
        }
    }
}
