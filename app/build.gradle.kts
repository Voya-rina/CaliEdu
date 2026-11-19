import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.caliedu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.caliedu"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"

        // Chaquopy 17 只提供 64 位运行时；x86_64 是留给模拟器的。
        // 注意：这里一旦写了 abiFilters，就不能再开 splits.abi（AGP 会判冲突）。
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

    }

    /**
     * 用 debug keystore 签 release，只是为了拿到一个"能装、体积小"的包自测。
     * 真要分发必须换成自己的签名，别拿这个上应用商店。
     */
    signingConfigs {
        create("local") {
            val store = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (store.exists()) {
                storeFile = store
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("local")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        // pypdf 是纯 Python，用来抽 PDF 的文字层（扫码件抽不出，如实报错）。
        pip {
            install("pypdf")
        }
        // 只放源码，不预编译 .pyc：调试时改 runner.py 不用全量重建。
        pyc {
            src = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // 页面切换与分段控件的动画（版本由 Compose BOM 统一管）
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
