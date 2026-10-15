plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mina.studymirror"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mina.studymirror"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "4.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("study-mirror.keystore")
            storePassword = "studymirror"
            keyAlias = "studymirror"
            keyPassword = "studymirror"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
