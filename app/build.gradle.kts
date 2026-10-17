// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.aboutlibraries)
}

// Release signing comes from local.properties (never in git); without the four properties the release is built unsigned.
val secrets = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load) }
val releaseSigning = listOf("tikstik.keystore", "tikstik.keystore.password", "tikstik.key.alias", "tikstik.key.password").map(secrets::getProperty)

android {
    namespace = "com.redrixdev.tikstik"
    compileSdk {
        version = release(37)
    }
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.redrixdev.tikstik"
        minSdk = 26
        // 37 requires ACCESS_LOCAL_NETWORK for the ADB connection over Wi-Fi.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // 32-bit for old low-end phones and x86_64 for the emulator.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    testOptions {
        // Logger calls android.util.Log: in JVM tests it does nothing instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (releaseSigning.all { !it.isNullOrBlank() }) {
            create("release") {
                storeFile = rootProject.file(releaseSigning[0]!!)
                storePassword = releaseSigning[1]
                keyAlias = releaseSigning[2]
                keyPassword = releaseSigning[3]
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Full R8 with the classic DSL: with `optimization {}` the Baseline Profile came out obfuscated.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // So a crash in the native codec shows function names in Play Console.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
        // Optimized release with the debug signature and id, to measure and test without losing the debug data.
        create("optimized") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Only the app's languages: otherwise the libraries bring 84 and their pieces came out in a different language than the rest.
    androidResources {
        localeFilters += listOf("en", "es")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // targetSdk 36 on purpose until ACCESS_LOCAL_NETWORK is ready.
        disable += "OldTargetApi"
    }
}

// The published APK leaves out x86_64 (emulators and a few Chromebooks), a third of its size; the builds run on the emulator keep it.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.jniLibs.excludes.add("lib/x86_64/**")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// A single profile, generated with the release, in src/main: used by both release and optimized.
baselineProfile {
    mergeIntoMain = true
}

// Third-party licenses, generated into res/raw and committed: run :app:exportLibraryDefinitions after changing dependencies.
aboutLibraries {
    collect {
        configPath = file("licenses")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
    export {
        outputFile = file("src/main/res/raw/aboutlibraries.json")
        variant = "release"
        excludeFields.addAll("developers", "funding", "description", "scm", "organization")
        prettyPrint = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.libadb)
    // libadb only brings it at runtime; its ASN.1 is used here for the certificate.
    implementation(libs.bouncycastle.bcprov)
    // libadb uses it by reflection for its TLS; without it, pairing would fall back on the platform's hidden API.
    implementation(libs.conscrypt)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    // Installs the Baseline Profile on phones that do not get it from Play.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.aboutlibraries.core)
    baselineProfile(project(":baselineprofile"))
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // ui-test brings Espresso 3.5, which fails to inject taps on Android 17; 3.7.0 fixes it.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
