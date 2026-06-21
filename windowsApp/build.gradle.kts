import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.8.2"
}

group = "com.pulsedeck"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.pulsedeck.windows.WindowsMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "PulseDeck"
            packageVersion = "0.1.0"
            vendor = "PulseDeck"
            description = "PulseDeck Windows desktop music player prototype"
            modules("java.desktop", "java.logging")
        }
    }
}
