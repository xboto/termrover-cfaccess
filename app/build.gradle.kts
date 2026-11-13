plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "io.github.xboto.termrovercfaccess"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.xboto.termrovercfaccess"
        minSdk = 26
        targetSdk = 36
        ndk {
            abiFilters += "arm64-v8a"
        }
        versionCode = 21
        versionName = "1.2.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false

        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {

        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libcloudflared.so"
        }
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
