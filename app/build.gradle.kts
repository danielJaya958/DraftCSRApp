    plugins {
        alias(libs.plugins.android.application)
        alias(libs.plugins.kotlin.android)
        alias(libs.plugins.hilt.android)
        id("kotlin-kapt")
    }

    android {
        namespace = "com.ksm.draftcsrapp"
        compileSdk {
            version = release(36)
        }

        defaultConfig {
            applicationId = "com.ksm.draftcsrapp"
            minSdk = 24
            targetSdk = 36
            versionCode = 1
            versionName = "1.0"

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

        buildFeatures {
            viewBinding = true
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    dependencies {
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.constraintlayout)

        // Retrofit & OkHttp untuk komunikasi dengan ASP.NET Core
        implementation("com.squareup.retrofit2:retrofit:2.9.0")
        implementation("com.squareup.retrofit2:converter-gson:2.9.0")
        implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

        // Hilt Dependency Injection
        implementation("com.google.dagger:hilt-android:2.48")
        kapt("com.google.dagger:hilt-android-compiler:2.48")

        // Jetpack Security (Untuk enkripsi JWT token)
        implementation("androidx.security:security-crypto:1.1.0-alpha06")

        // Navigation Compose
        implementation("androidx.navigation:navigation-compose:2.7.4")
        implementation("androidx.hilt:hilt-navigation-compose:1.0.0")

        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
    }