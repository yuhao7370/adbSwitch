import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 构建时间戳（epoch millis）：注入 BuildConfig.BUILD_TIME_MS，App 底部格式化显示，
// 便于确认设备上装的是否为最新版本。此处只取原始毫秒数，格式化放到 App 里做
// （Gradle Kotlin DSL 顶层无法直接引用 java.text/java.time 做格式化）。
val buildTimeMs = System.currentTimeMillis()

// 签名密钥与口令只存 keystore/signing.properties（keystore/ 整个目录在 .gitignore，绝不入库）：
// 仓库公开，明文口令不能写进 gradle。缺该文件（公开仓库 clone 下来）时不算 release 签名，
// release 产物自然出不来——私钥本来就只在维护者机器上。
// （不能写 java.util.Properties：脚本里 java 会解析成工程的 java 扩展，必须 import。）
// 路径相对本模块（adbswitch/app），指向仓库根的 keystore/；Gradle 工程根是 adbswitch/，别用 rootProject 拼。
val signingProps = Properties().apply {
    file("../../keystore/signing.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.elleng.adbswitch"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    // AGP 8 默认不生成 BuildConfig，这里显式开启以便注入构建时间戳
    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.yuhao7370.adbswitch"
        minSdk = 27
        targetSdk = 36
        versionCode = 11
        versionName = "1.2.0"
        buildConfigField("long", "BUILD_TIME_MS", "${buildTimeMs}L")

        // 蒲公英备通道密钥：从仓库外 pgyer.properties 注入 BuildConfig，避免明文进源码/公开仓库。
        // 文件缺失或字段为空时注入空串，蒲公英通道自动降级（未配置即不请求）。
        // 模板见 adbswitch/pgyer.properties.example；真实文件已被根 .gitignore 忽略。
        val pgyerPropsFile = rootProject.file("pgyer.properties")
        val pgyerProps = if (pgyerPropsFile.exists()) {
            Properties().apply { pgyerPropsFile.inputStream().use { load(it) } }
        } else null
        val pgyerApiKey = pgyerProps?.getProperty("PGYER_API_KEY").orEmpty()
        val pgyerAppKey = pgyerProps?.getProperty("PGYER_APP_KEY").orEmpty()
        buildConfigField("String", "PGYER_API_KEY", "\"$pgyerApiKey\"")
        buildConfigField("String", "PGYER_APP_KEY", "\"$pgyerAppKey\"")
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = file("../../keystore/adbswitch.jks")
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 开启 R8 混淆 + 资源压缩。
            // Android framework 反射不受混淆影响；Compose/miuix 使用依赖提供的 consumer rules。
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // 交付文件名使用应用名，不包含个人标识；独立 applicationId 保持不变。
    applicationVariants.all {
        val ver = versionName
        val type = name
        outputs.all {
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                outputFileName = "AdbSwitch-${ver}-${type}.apk"
            }
        }
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

// 主开关的真实写入走双后端：root（su）或 Shizuku（shell uid 2000）。
// Shizuku 的三个工件（api / provider / aidl）13.1.5 已在本机 Gradle 缓存，可离线构建。
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
    implementation("dev.chrisbanes.haze:haze-android:1.7.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("dev.rikka.shizuku:aidl:13.1.5")
}
