import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Load release signing configuration from environment variables or local keystore.properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val releaseStoreFile: String? = System.getenv("HCMC_RELEASE_STORE_FILE")
    ?: keystoreProperties.getProperty("HCMC_RELEASE_STORE_FILE")
    ?: keystoreProperties.getProperty("storeFile")
val releaseStorePassword: String? = System.getenv("HCMC_RELEASE_STORE_PASSWORD")
    ?: keystoreProperties.getProperty("HCMC_RELEASE_STORE_PASSWORD")
    ?: keystoreProperties.getProperty("storePassword")
val releaseKeyAlias: String? = System.getenv("HCMC_RELEASE_KEY_ALIAS")
    ?: keystoreProperties.getProperty("HCMC_RELEASE_KEY_ALIAS")
    ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword: String? = System.getenv("HCMC_RELEASE_KEY_PASSWORD")
    ?: keystoreProperties.getProperty("HCMC_RELEASE_KEY_PASSWORD")
    ?: keystoreProperties.getProperty("keyPassword")

val hasReleaseSigning: Boolean = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.hcmcbustracker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hcmcbustracker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning && releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
            val resolvedStoreFile = if (File(releaseStoreFile).isAbsolute) {
                File(releaseStoreFile)
            } else {
                rootProject.file(releaseStoreFile)
            }
            create("release") {
                storeFile = resolvedStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

gradle.taskGraph.whenReady {
    val isPackagingRelease = allTasks.any { task ->
        task.project == project && (
            task.name.equals("packageRelease", ignoreCase = true) ||
            task.name.equals("assembleRelease", ignoreCase = true)
        )
    }
    if (isPackagingRelease) {
        if (!hasReleaseSigning || releaseStoreFile == null) {
            throw GradleException(
                "\n[HCMC Bus Tracker] LỖI CẤU HÌNH KÝ PHÁT HÀNH (RELEASE SIGNING ERROR):\n" +
                "Không thể tạo bản phát hành chưa ký (unsigned release APK).\n" +
                "Vui lòng cung cấp thông tin ký phát hành qua:\n" +
                "  1. Biến môi trường: HCMC_RELEASE_STORE_FILE, HCMC_RELEASE_STORE_PASSWORD, HCMC_RELEASE_KEY_ALIAS, HCMC_RELEASE_KEY_PASSWORD\n" +
                "  2. Hoặc tệp 'keystore.properties' tại thư mục gốc dự án (tham khảo keystore.properties.example).\n" +
                "Xem hướng dẫn chi tiết tại docs/RELEASE_BUILD_GUIDE.md."
            )
        }
        val resolvedFile = if (File(releaseStoreFile).isAbsolute) {
            File(releaseStoreFile)
        } else {
            rootProject.file(releaseStoreFile)
        }
        if (!resolvedFile.exists()) {
            throw GradleException(
                "\n[HCMC Bus Tracker] LỖI TỆP KEYSTORE KHÔNG TỒN TẠI:\n" +
                "Tệp keystore được chỉ định tại '${resolvedFile.absolutePath}' không tồn tại.\n" +
                "Vui lòng kiểm tra lại đường dẫn trong 'keystore.properties' hoặc biến môi trường HCMC_RELEASE_STORE_FILE."
            )
        }
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-network"))
    implementation(project(":core-database"))
    implementation(project(":core-designsystem"))
    implementation(project(":feature-search"))
    implementation(project(":feature-route"))
    implementation(project(":feature-prediction"))
    implementation(project(":feature-map"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.navigation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
