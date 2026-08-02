import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Optional release signing — set in local.properties (gitignored) or env:
//   RELEASE_STORE_FILE=/path/to/upload.jks
//   RELEASE_STORE_PASSWORD=...
//   RELEASE_KEY_ALIAS=...
//   RELEASE_KEY_PASSWORD=...
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
fun propOrEnv(key: String): String? =
    localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.crome.freshrss"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crome.freshrss"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "0.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val storePath = propOrEnv("RELEASE_STORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = propOrEnv("RELEASE_STORE_PASSWORD") ?: ""
                keyAlias = propOrEnv("RELEASE_KEY_ALIAS") ?: ""
                keyPassword = propOrEnv("RELEASE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Stable Keystore-backed EncryptedSharedPreferences / EncryptedFile
    implementation("androidx.security:security-crypto:1.0.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
