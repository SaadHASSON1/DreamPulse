plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") // الـ Plugin الجديد المطلوب في Kotlin 2.0
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.x13labs.dreampulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.x13labs.dreampulse"
        minSdk = 30 
        targetSdk = 35 
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // ملاحظة: في Kotlin 2.0، لا نحتاج لـ composeOptions kotlinCompilerExtensionVersion
}

dependencies {
    // Wear OS Latest (2026)
    implementation("androidx.wear.compose:compose-material:1.4.0-alpha01")
    implementation("androidx.wear.compose:compose-foundation:1.4.0-alpha01")
    implementation("androidx.wear.compose:compose-navigation:1.4.0-alpha01")
    
    // Health Services (Latest 2026)
    implementation("androidx.health:health-services-client:1.1.0-rc01")
    
    // Hilt & Others
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    
    // Wear OS Tiles (Stable Versions)
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.0")
}
