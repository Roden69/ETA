plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseVersion = providers.environmentVariable("ETA_RELEASE_VERSION").orNull
val releaseVersionParts = releaseVersion?.let {
    Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)""")
        .matchEntire(it)?.groupValues?.drop(1)
        ?: throw GradleException("ETA_RELEASE_VERSION must be MAJOR.MINOR.PATCH with canonical numeric components.")
}
val releaseVersionNumbers = releaseVersionParts?.map {
    it.toLongOrNull()
        ?: throw GradleException("ETA_RELEASE_VERSION components are out of range.")
}
val releaseMajor = releaseVersionNumbers?.get(0)
val releaseMinor = releaseVersionNumbers?.get(1)
val releasePatch = releaseVersionNumbers?.get(2)
if (releaseMajor != null &&
    (releaseMajor !in 0L..2099L || releaseMinor !in 0L..999L || releasePatch !in 0L..999L)
) {
    throw GradleException("ETA_RELEASE_VERSION components are out of range.")
}
val releaseStorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigningValues = releaseSigningValues.any { it != null }
if (hasReleaseSigningValues && releaseSigningValues.any { it.isNullOrEmpty() }) {
    throw GradleException("Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD.")
}
if (releaseStorePath != null && !file(releaseStorePath).isFile) {
    throw GradleException("ANDROID_KEYSTORE_PATH must point to an existing keystore file.")
}

val releaseVersionCode = if (releaseMajor == null) null
    else (releaseMajor * 1_000_000L + releaseMinor!! * 1_000L + releasePatch!! + 1L).toInt()
val releaseVersionName = releaseVersion

android {
    signingConfigs {
        if (hasReleaseSigningValues) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }
    namespace = "com.example.eta"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The app was called ERIK until the rename to Eta. The id keeps the old
        // name on purpose: a new one would install as a second, empty app.
        applicationId = "com.example.erik_iteration_2"
        minSdk = 24
        targetSdk = 37
        versionCode = releaseVersionCode ?: 1
        versionName = releaseVersionName ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasReleaseSigningValues) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // kotlinx-datetime resolves to java.time, which minSdk 24 lacks natively.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    // Exported schemas are the input for Room migration tests.
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.datetime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}