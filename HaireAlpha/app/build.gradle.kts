plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.takehaire.alpha"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.takehaire.alpha"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0-rc2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Modern Galaxy flagships are 64-bit ARM only; this keeps ML Kit's native code to one ABI.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        create("sideload") {
            storeFile = rootProject.file("keystore/haire-sideload.jks")
            storePassword = "haire-alpha"
            keyAlias = "haire"
            keyPassword = "haire-alpha"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            // R8 is off for this release candidate: the build cannot be exercised on hardware here, so
            // we avoid shrinker-only failure modes. Enable after the physical-device checklist passes.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "4g"
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Optional: point Robolectric at pre-downloaded android-all jars (e.g. when Maven Central rate-limits).
                (project.findProperty("robolectricDepsDir") as String?)?.let { dir ->
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", dir)
                }
                it.systemProperty("haire.screenshots", rootProject.layout.projectDirectory.dir("docs/screenshots").asFile.absolutePath)
            }
        }
    }
    packaging { resources { excludes += setOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1") } }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":imaging"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Experimental virtual bokeh: subject masks (Play services module, any subject) with a bundled
    // offline people-only fallback. Used only when the photographer turns virtual bokeh on.
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.mlkit.selfie.segmentation)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
