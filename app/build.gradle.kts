plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.daily.nexamartpartner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.daily.nexamartpartner"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "APP_ENV", "\"development\"")
            buildConfigField("String", "BASE_URL", "\"${project.findProperty("nexamartApiUrl") ?: "https://dev-api.nexamart.example.com/api/v1/"}\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "APP_ENV", "\"staging\"")
            buildConfigField("String", "BASE_URL", "\"${project.findProperty("nexamartStagingApiUrl") ?: "https://staging-api.nexamart.example.com/api/v1/"}\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "APP_ENV", "\"production\"")
            buildConfigField("String", "BASE_URL", "\"${project.findProperty("nexamartProdApiUrl") ?: "https://zeptopluse-production.up.railway.app/api/v1/"}\"")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("Boolean", "ENABLE_NETWORK_LOGGING", "true")
        }
        release {
            isMinifyEnabled = false
            optimization {
                enable = false
            }
            buildConfigField("Boolean", "ENABLE_NETWORK_LOGGING", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.coil)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
}