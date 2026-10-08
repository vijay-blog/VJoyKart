import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing is read from customer/keystore.properties (git-ignored) or from
// environment variables so no secret is ever committed. When neither is present the
// release AAB is produced UNSIGNED instead of silently being signed with the debug key.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("customer/keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(property: String, env: String): String? =
    keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "VJOYKART_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "VJOYKART_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "VJOYKART_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "VJOYKART_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null }

// Production defaults can be overridden with Gradle project properties per environment.
val apiBaseUrl = (project.findProperty("vjoykartApiBaseUrl") as String?)
    ?: "https://zeptopluse-production.up.railway.app/api/v1"
val catalogImageBaseUrl = (project.findProperty("vjoykartCatalogImageBaseUrl") as String?)
    ?: "https://nexamartpartner-production.up.railway.app"

android {
    // Must stay identical to the published Google Play application.
    namespace = "com.nexamart.customer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nexamart.customer"
        minSdk = 24
        targetSdk = 36
        // Play requires a versionCode higher than the published customer release.
        versionCode = 10
        versionName = "2.2.2"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "CATALOG_IMAGE_BASE_URL", "\"$catalogImageBaseUrl\"")
        buildConfigField("String", "ACCOUNT_DELETION_URL", "\"${project.findProperty("vjoykartAccountDeletionUrl") ?: "$apiBaseUrl".removeSuffix("/api/v1") + "/delete-account"}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "ENABLE_NETWORK_LOGGING", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("Boolean", "ENABLE_NETWORK_LOGGING", "false")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.coil)
    implementation(libs.razorpay.checkout)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
