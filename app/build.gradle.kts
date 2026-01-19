plugins {

    id("com.android.application")
}

android {
    namespace ="com.example.tp7"
    compileSdk = 36 // ARCore nécessite API 34

    defaultConfig {
        applicationId = "com.example.tp7"
        minSdk = 24 // ARCore minimum API 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        // ----- Bloc pour NDK / CMake -----
        externalNativeBuild {
            cmake {
                // Arguments optionnels à passer à CMake
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }


    }



    android {

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
        signingConfigs {
            create("release") {
                storeFile = file("C:/Users/PC/AndroidStudioProjects/TP7/keystore/my-release-key.jks")
                storePassword = "123456"
                keyAlias = "my-key-alias"
                keyPassword = "123456"
            }
        }

        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Important pour Sceneform
    buildFeatures {
        viewBinding = true
    }
}


dependencies {
    // Existantes
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Google Maps SDK pour Android
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")

    // ============ ARCore & Sceneform ============

    // ARCore (Google)
    implementation("com.google.ar:core:1.41.0")

    // Sceneform (Community maintained - dernière version stable)
    implementation("com.gorisse.thomas.sceneform:sceneform:1.21.0")



    // Support pour les modèles 3D
    implementation("de.javagl:obj:0.4.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // Retrofit pour API (si besoin d'API avancées)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Kotlin coroutines (utile pour opérations asynchrones)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

// Configuration pour éviter les conflits de dépendances
configurations.all {
    resolutionStrategy {
        force("com.google.ar:core:1.41.0")
        exclude ("com.android.support', module: 'support-compat")
        exclude ("com.android.support', module: 'support-core-utils")
        exclude ("com.android.support', module: 'support-annotations")
    }
    }
