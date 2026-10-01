plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.gatsaeng.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.gatsaeng.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        getByName("debug") {
            // Keep the local test key with this checkout, away from the user's home.
            storeFile = rootProject.file(".local-signing/debug.keystore")
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Generate a private local test key on the first debug build. Never ship this key.
val localDebugKey = rootProject.file(".local-signing/debug.keystore")
val createLocalDebugKey by tasks.registering(Exec::class) {
    outputs.file(localDebugKey)
    onlyIf { !localDebugKey.exists() }
    doFirst { localDebugKey.parentFile.mkdirs() }
    val keytoolName = if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool"
    commandLine(
        File(System.getProperty("java.home"), "bin/$keytoolName").absolutePath,
        "-genkeypair", "-keystore", localDebugKey.absolutePath,
        "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "10000", "-storetype", "JKS", "-storepass", "android", "-keypass", "android",
        "-dname", "CN=Android Debug,O=Android,C=US",
    )
}
tasks.matching { it.name == "validateSigningDebug" }.configureEach {
    dependsOn(createLocalDebugKey)
}
