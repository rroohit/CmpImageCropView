import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.cmp.image.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.cmp.image"
            packageVersion = "1.0.0"

            macOS { iconFile.set(project.file("src/main/resources/icon.png")) }
            windows { iconFile.set(project.file("src/main/resources/icon.png")) }
            linux { iconFile.set(project.file("src/main/resources/icon.png")) }
        }
    }
}