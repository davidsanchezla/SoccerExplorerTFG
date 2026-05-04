import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val newsApiKey = localProperties.getProperty("NEWS_API_KEY", "")
val apiFootballKey = localProperties.getProperty("API_FOOTBALL_KEY", "")
val footballDataApiKey = localProperties.getProperty("FOOTBALL_DATA_API_KEY", apiFootballKey)

android {
    namespace = "com.example.soccerexplorer"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.soccerexplorer"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "NEWS_API_KEY", "\"$newsApiKey\"")
        buildConfigField("String", "API_FOOTBALL_KEY", "\"$apiFootballKey\"")
        buildConfigField("String", "FOOTBALL_DATA_API_KEY", "\"$footballDataApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    // Testing libraries
    testImplementation("org.robolectric:robolectric:4.11")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("androidx.test:core:1.5.0")
    // Provide a JVM org.json implementation for unit tests (avoids Android-only stubs)
    testImplementation("org.json:json:20230227")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Instrumented test dependencies
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.github.MKergall:osmbonuspack:6.9.0")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("com.google.guava:guava:33.2.1-android")
}
