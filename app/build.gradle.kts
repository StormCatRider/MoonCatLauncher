import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// =========================
//   Version configuration
// =========================

val major = 0
val minor = 1
val patch = 0
val build = 0

val baseVersionName = "$major.$minor.$patch"

val baseVersionCode =
    (String.format("%02d", major) +
            String.format("%02d", minor) +
            String.format("%02d", patch) +
            String.format("%02d", build)).toInt()

// =========================
//   Android configuration
// =========================

extensions.configure<ApplicationExtension>("android") {

    namespace = "com.github.codeworkscreativehub.mlauncher"

    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        versionCode = baseVersionCode
        versionName = baseVersionName
    }

    flavorDimensions += "channel"

    productFlavors {
        create("prod") {
            dimension = "channel"
            applicationId = "app.mooncat.launcher"
            resValue("string", "app_name", "Moon Cat Launcher")
            resValue("bool", "is_play_store", "false")
        }

        create("play") {
            dimension = "channel"
            applicationId = "app.mooncat.launcher"
            resValue("string", "app_name", "Moon Cat Launcher")
            resValue("bool", "is_play_store", "true")
        }

        create("beta") {
            dimension = "channel"
            applicationId = "app.mooncat.launcher.beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "Moon Cat Launcher Beta")
            resValue("bool", "is_play_store", "false")
        }

        create("alpha") {
            dimension = "channel"
            applicationId = "app.mooncat.launcher.alpha"
            versionNameSuffix = "-alpha"
            resValue("string", "app_name", "Moon Cat Launcher Alpha")
            resValue("bool", "is_play_store", "false")
        }

        create("nightly") {
            dimension = "channel"
            applicationId = "app.mooncat.launcher.nightly"
            versionNameSuffix = "-nightly"
            resValue("string", "app_name", "Moon Cat Launcher Nightly")
            resValue("bool", "is_play_store", "false")
        }
    }

    // Independent release signing; debug builds need no release secrets.
    val releaseKey = rootProject.file("app/mooncat.jks")
    fun signingSecret(name: String): String? =
        System.getenv(name) ?: project.findProperty(name) as? String
    val hasReleaseSigning = releaseKey.isFile &&
        listOf("KEY_STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").all { !signingSecret(it).isNullOrBlank() }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKey
                storePassword = signingSecret("KEY_STORE_PASSWORD")
                keyAlias = signingSecret("KEY_ALIAS")
                keyPassword = signingSecret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs["debug"]

            resValue("string", "app_version", baseVersionName)
            resValue("string", "app_name", "Moon Cat Launcher Debug")
            resValue("string", "empty", "")
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (hasReleaseSigning) signingConfig = signingConfigs["release"]

            resValue("string", "app_version", baseVersionName)
            resValue("string", "empty", "")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("libandroidx.graphics.path.so")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}


// =========================
//   KSP
// =========================

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// =========================
//   Dependencies
// =========================

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.activity.ktx)
    implementation(libs.palette.ktx)
    implementation(libs.material)
    implementation(libs.viewpager2)
    implementation(libs.activity)
    implementation(libs.fragment.ktx)
    implementation(libs.commons.text)

    implementation(libs.lifecycle.extensions)
    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.work.runtime.ktx)

    implementation(libs.constraintlayout)
    implementation(libs.constraintlayout.compose)
    implementation(libs.activity.compose)

    implementation(libs.compose.material)
    implementation(libs.compose.android)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling)

    implementation(libs.biometric.ktx)

    implementation(libs.moshi)
    implementation(libs.moshi.ktx)
    ksp(libs.moshi.codegen)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.espresso.idling.resource)

    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.test.core.ktx)

    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)

    androidTestImplementation(libs.navigation.testing)

    testImplementation(libs.junit)
}
