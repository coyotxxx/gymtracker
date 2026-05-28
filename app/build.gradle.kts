plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    jacoco  // v1.27 — coverage report systemu testów
}

android {
    namespace = "pl.filebit.gymtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.filebit.gymtracker"
        minSdk = 29
        targetSdk = 35
        versionCode = 576
        versionName = "2.6.0"
        vectorDrawables { useSupportLibrary = true }
        // v1.14.1: androidTest infra dla MigrationTestHelper
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Stały keystore w repo — bez tego każdy build CI ma inną sygnaturę
            // i Android odmawia aktualizacji ('Nie zainstalowano aplikacji').
            // To NIE jest klucz produkcyjny — debug only, hasło publicznie znane.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
            enableUnitTestCoverage = true  // v1.27 — JaCoCo .exec z testDebugUnitTest
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // For hobby use only — debug-signed APK is fine via Actions
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // v1.27 — system testów: Robolectric potrzebuje dostępu do zasobów i assetów
    // aplikacji (ExerciseSeeder czyta assets/exercises.json przy imporcie scenariuszy).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// v1.14.1: eksportuj Room schema JSON do app/schemas/ (per version DB).
// Wymagane dla MigrationTestHelper żeby walidować że migracja produkuje schemat
// matching @Entity definicji. Bez tego v1.13.0 schema mismatch crash mógłby się powtórzyć.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    // Coil (obrazki ćwiczeń + GIFy ExerciseDB v1.25.0)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // WorkManager + Hilt
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    // AI integration
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)

    // Lottie (splash animation)
    implementation(libs.lottie.compose)

    // Markwon — markdown dla tekstow od AI (View-based, niezalezne od Compose)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.linkify)

    // BarcodeScanner (v0.95) — CameraX + ML Kit barcode scanning
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // ListenableFuture dla CameraX (skipped przez Health Connect alpha)
    implementation("com.google.guava:guava:32.1.3-android")

    // Health Connect (v0.96) — auto-sync kroków
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Testing — pure logic unit testy (CI: ./gradlew :app:testDebugUnitTest)
    testImplementation(libs.junit)
    // v1.27 — system testów: Robolectric uruchamia Room in-memory + Context
    // w JVM unit teście (CI bez emulatora). Headless E2E harness.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)

    // androidTest — Room migration testing (v1.14.1)
    // Wymaga emulatora / urządzenia — uruchamiane przez ./gradlew :app:connectedDebugAndroidTest
    // CI obecnie tylko :app:testDebugUnitTest (bo brak emulatora w GitHub Actions).
    // Testy lokalne na fizycznym urządzeniu / emulatorze.
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// v1.27 — FAZA 0.4 systemu testów: raport pokrycia (JaCoCo).
// ./gradlew :app:jacocoTestReport → app/build/reports/jacoco/jacocoTestReport/
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Raport pokrycia testami JVM unit (FAZA 0.4)"

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }

    // Wykluczenia — kod generowany (nie liczy się do pokrycia logiki)
    val generated = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*_Hilt*.*", "**/Hilt_*.*", "**/Dagger*.*",
        "**/*_Factory.*", "**/*_MembersInjector.*", "**/*Module_*.*",
        "**/*_Impl.*", "**/*ComposableSingletons*.*", "**/*\$\$serializer.*",
        "**/databinding/**", "**/BR.*"
    )
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(generated)
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) { include("**/testDebugUnitTest.exec") }
    )
}
