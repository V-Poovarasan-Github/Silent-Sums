plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "app.silentsums"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
    val storePwd = providers.environmentVariable("STORE_PASSWORD").orNull
    val keyPwd = providers.environmentVariable("KEY_PASSWORD").orNull
    val releaseKeystore = file(keystorePath ?: "${rootDir}/my-upload-key.jks")
    if (releaseKeystore.exists() && !storePwd.isNullOrEmpty() && !keyPwd.isNullOrEmpty()) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = storePwd
        keyAlias = "upload"
        keyPassword = keyPwd
        enableV1Signing = true
        enableV2Signing = true
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  sourceSets {
    getByName("test").assets.directories.add("$projectDir/schemas")
    getByName("androidTest").assets.directories.add("$projectDir/schemas")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

val defaultReleaseKeystorePath = "${rootDir}/my-upload-key.jks"
val checkReleaseSigning by tasks.registering {
  val keystorePathProvider = providers.environmentVariable("KEYSTORE_PATH")
  val storePasswordProvider = providers.environmentVariable("STORE_PASSWORD")
  val keyPasswordProvider = providers.environmentVariable("KEY_PASSWORD")
  val fallbackKeystorePath = defaultReleaseKeystorePath
  doLast {
    val resolvedKeystorePath = keystorePathProvider.orNull
    val keystoreFile = File(resolvedKeystorePath ?: fallbackKeystorePath)
    val missingVars = buildList {
      if (resolvedKeystorePath.isNullOrEmpty() && !keystoreFile.exists()) {
        add("KEYSTORE_PATH")
      } else if (!keystoreFile.exists()) {
        add("KEYSTORE_PATH (file not found at ${keystoreFile.path})")
      }
      if (storePasswordProvider.orNull.isNullOrEmpty()) add("STORE_PASSWORD")
      if (keyPasswordProvider.orNull.isNullOrEmpty()) add("KEY_PASSWORD")
    }
    if (missingVars.isNotEmpty()) {
      throw GradleException(
        "Release signing credentials missing: ${missingVars.joinToString(", ")}. " +
          "Provide KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD to build release artifacts."
      )
    }
  }
}

tasks.matching {
  it.name == "preReleaseBuild" ||
    it.name == "packageRelease" ||
    it.name == "bundleRelease" ||
    it.name == "packageReleaseBundle" ||
    it.name == "signReleaseBundle" ||
    it.name == "validateSigningRelease"
}.configureEach {
  dependsOn(checkReleaseSigning)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
