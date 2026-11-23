plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val legacy = providers.gradleProperty("legacyDb").orNull == "true"
android {
    namespace = "mu.ac.uom.s0000000.tikafe"
    compileSdk = 35
    defaultConfig {
        applicationId = "mu.ac.uom.s" + (providers.gradleProperty("studentId").orNull ?: "0000000") + ".tikafe"
        minSdk = 26
        targetSdk = 35
        versionCode = if (legacy) 1 else 2
        versionName = if (legacy) "1.0" else "2.0"
        buildConfigField("int", "DATABASE_VERSION", if (legacy) "1" else "2")
    }
    buildFeatures { viewBinding = true; compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
