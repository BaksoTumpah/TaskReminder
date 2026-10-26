import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionProps = Properties().apply {
    file("../version.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.catatan.sekolah"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.catatan.sekolah"
        minSdk = 24
        targetSdk = 34
        versionCode = versionProps["VERSION_CODE"].toString().trim().toInt()
        versionName = versionProps["VERSION_NAME"].toString().trim()
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "catatan123"
            keyAlias = "catatan"
            keyPassword = "catatan123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    lint { checkReleaseBuilds = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
}
