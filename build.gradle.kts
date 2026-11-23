plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.jarvis"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.jarvis"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.3-phone-only"
    }
}

dependencies {
    implementation("ai.picovoice:porcupine-android:4.0.2")
    implementation("androidx.security:security-crypto:1.1.0")
}
