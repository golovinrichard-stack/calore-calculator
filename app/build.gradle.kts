import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AGP 9 compiles Kotlin itself, so there is no separate org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.calore.tracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.calore.tracker"
        minSdk = 26
        // Meets Google Play's current requirement. Raise to 37 after testing on Android 17.
        targetSdk = 36
        versionCode = 13
        versionName = "0.13.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // M10.4: the research server's address, from the Gradle property calore.researchServerUrl.
        // Empty (the default) keeps the whole research feature hidden and sends nothing.
        val researchServerUrl = providers.gradleProperty("calore.researchServerUrl").getOrElse("")
        buildConfigField("String", "RESEARCH_SERVER_URL", "\"" + researchServerUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The signed dependency metadata block is opaque to F-Droid's reproducible build checks.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Lets MigrationTestHelper read the exported schema of every database version.
    sourceSets["androidTest"].assets.srcDirs(files("$projectDir/schemas"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // M7.6: the app lock (fingerprint, face or the phone's PIN). Open source, part of AndroidX.
    implementation(libs.androidx.biometric)
    // M9: the home-screen widget. Open source, part of AndroidX.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Barcode scanner (M2.4): CameraX for the preview, ZXing (Apache 2.0) to decode. No ML Kit.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.serialization.json)
    // Declared directly (not just via AndroidX) so app and test code agree on one coroutines version.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
