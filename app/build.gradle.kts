plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.encryptvault.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.encryptvault.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.documentfile:documentfile:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
