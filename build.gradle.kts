plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.onee.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.onee.browser"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    dependencies {
        // ... diğer bağımlılıklar
        implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    }
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    //noinspection UseTomlInstead,GradleDependency
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    //noinspection UseTomlInstead,GradleDependency
    implementation("androidx.activity:activity-ktx:1.9.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
