import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Needed for the @Serializable callable request payloads in
    // data/BriefsRepository.kt. :core already applies it for the models.
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()
    
    js {
        browser()
        binaries.executable()
    }

    android {
        namespace = "com.zayedmd.anteroom.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        // purchases-kmp publishes Android and Apple artifacts only, so the store-backed billing
        // code is compiled into both mobile targets from one shared directory. An intermediate
        // source set wired with dependsOn() would switch off the default hierarchy template that
        // this module relies on for iosMain.
        androidMain.get().kotlin.srcDir("src/mobileMain/kotlin")
        iosMain.get().kotlin.srcDir("src/mobileMain/kotlin")

        // Compose Multiplatform's skiko-backed targets share API that the Android actual lacks
        // (DialogProperties.scrimColor). Same one-directory trick as mobileMain.
        iosMain.get().kotlin.srcDir("src/skikoMain/kotlin")
        jvmMain.get().kotlin.srcDir("src/skikoMain/kotlin")
        jsMain.get().kotlin.srcDir("src/skikoMain/kotlin")

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation(libs.purchases.core)
            implementation(libs.purchases.result)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.purchases.core)
            implementation(libs.purchases.result)
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Pulled in transitively by firebase-*-jvm, but referenced
            // directly by FirebaseInit.jvm.kt (FirebasePlatform and the stub
            // android.content.Context), so it has to be on the compile
            // classpath rather than only the runtime one.
            implementation("dev.gitlive:firebase-java-sdk:0.4.5")
            // GitLive's JVM auth dispatches its callbacks onto
            // Dispatchers.Main, which plain JVM does not provide -- the
            // failure is an IllegalStateException on an OkHttp thread
            // ("Module with the Main dispatcher is missing") that leaves the
            // sign-in coroutine hanging rather than throwing to the caller.
            implementation(libs.kotlinx.coroutinesSwing)
        }
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation("com.russhwolf:multiplatform-settings:1.2.0")
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.2.0")
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.storage)
            implementation(libs.gitlive.firebase.functions)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.client.js)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}