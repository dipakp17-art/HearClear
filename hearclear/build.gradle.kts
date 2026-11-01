plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.dipak.hearclear"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Account/payment enforcement is paused for this unrestricted test release.
        val accountsEnabled = false
        buildConfigField("boolean", "ACCOUNTS_ENABLED", accountsEnabled.toString())
        manifestPlaceholders["accountsEnabled"] = accountsEnabled.toString()
        val apiUrl = providers.gradleProperty("hearclearApiUrl").orElse("").get()
        require(apiUrl.isEmpty() || (apiUrl.startsWith("https://") && apiUrl.matches(Regex("https://[A-Za-z0-9._:/-]+")))) { "hearclearApiUrl must be an HTTPS URL without query/credentials" }
        buildConfigField("String", "ACCOUNT_API_URL", "\"$apiUrl\"")
        applicationId = "com.dipak.hearclear"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON", "-DANDROID_STL=c++_static") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        it.systemProperty("java.library.path", rootProject.file("build/rnnoise-host").absolutePath)
        it.systemProperty("hearclear.requireNative", providers.gradleProperty("requireNativeRnnoise").orElse("false").get())
    }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }
}
dependencies {
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

