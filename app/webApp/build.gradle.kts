plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:shared"))
            implementation(libs.compose.ui)
        }

        val webMain by creating {
            dependsOn(commonMain.get())
        }

        jsMain.get().dependsOn(webMain)
    }
}