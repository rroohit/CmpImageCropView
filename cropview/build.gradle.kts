import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    iosArm64()
    iosSimulatorArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.cmp.image.cropview"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    // Uploads to the Central Portal; the deployment is released manually from the
    // Central Portal UI (https://central.sonatype.com).
    publishToMavenCentral()
    signAllPublications()

    // Bundle the real Dokka-generated API docs as the -javadoc.jar.
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        )
    )

    coordinates("io.github.rroohit", "CmpImgCropView", "0.1.0")

    pom {
        name.set("CmpImgCropView")
        description.set("A customizable image cropping component for Compose Multiplatform.")
        inceptionYear.set("2026")
        url.set("https://github.com/rroohit/CmpImageCropView")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("rroohit")
                name.set("Rohit Chavan")
                url.set("https://github.com/rroohit")
            }
        }

        scm {
            url.set("https://github.com/rroohit/CmpImageCropView")
            connection.set("scm:git:git://github.com/rroohit/CmpImageCropView.git")
            developerConnection.set("scm:git:ssh://git@github.com/rroohit/CmpImageCropView.git")
        }
    }
}