import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.weenas.castbay.protocol"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                cppFlags += listOf("-std=c++17", "-fvisibility=hidden")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// OpenSSL's libcrypto for each ABI, built from source (third_party/openssl) before the native
// build links it; quick once built (see tools/build-openssl.sh).
val buildOpenSsl by tasks.registering(Exec::class) {
    val abis = android.defaultConfig.ndk.abiFilters.toList()
    val ndkDir = android.ndkDirectory.absolutePath
    workingDir = rootDir
    environment("ANDROID_NDK_ROOT", ndkDir)
    commandLine(listOf("bash", "tools/build-openssl.sh") + abis)
}
tasks.named("preBuild") { dependsOn(buildOpenSsl) }
