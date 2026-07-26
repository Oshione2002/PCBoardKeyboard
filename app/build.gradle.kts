plugins { id("com.android.application") }

android {
    namespace = "com.treasure.pcboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.treasure.pcboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.0.0"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}
