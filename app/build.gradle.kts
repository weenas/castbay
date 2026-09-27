import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The native AirPlay integration depends on a separately built UxPlay artifact.
// Keep the UI app buildable until that integration is supplied.
val enableNativeBuild = providers.gradleProperty("enableNativeBuild")
    .map(String::toBoolean)
    .orElse(false)

android {
    namespace = "com.weenas.castbay"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.weenas.castbay"
        minSdk = 26
        targetSdk = 35
        // Bumped for every build installed on a test TV; the name's last part matches versionCode.
        versionCode = 38
        versionName = "1.0.38"

        if (enableNativeBuild.get()) {
            externalNativeBuild {
                cmake {
                    cppFlags("-std=c++17 -frtti -fexceptions")
                    arguments("-DANDROID_STL=c++_shared")
                }
            }
        }
    }

    // The release key comes from the environment (GitHub Actions secrets, or a local shell);
    // it never lives in the repository. Without it, release builds fall back to the debug key
    // so local release builds still install on test TVs.
    val releaseKeystore = System.getenv("CASTBAY_KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("CASTBAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CASTBAY_KEY_ALIAS")
                keyPassword = System.getenv("CASTBAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 drops the unused parts of Compose, Media3 and Kotlin (about 2/3 of the dex).
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    if (enableNativeBuild.get()) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":airplay"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.tv:tv-foundation:1.0.0")

    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")

    implementation("com.google.code.gson:gson:2.11.0")
    // QR codes on the About screen (a TV can't easily open a link; a phone can scan one).
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
