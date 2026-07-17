import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

val googleServicesFile = file("google-services.json")
if (googleServicesFile.isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = releaseSigningKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() }
val betaVersionCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val betaVersionName = providers.gradleProperty("VERSION_NAME").orNull ?: "0.1.0-beta.1"

android {
    namespace = "com.dlunaunizar.bobitos"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.dlunaunizar.bobitos"
        minSdk = 26
        targetSdk = 37
        versionCode = betaVersionCode
        versionName = betaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "true")
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"demo-bobitos\"")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"127.0.0.1\"")
            buildConfigField("int", "FIREBASE_AUTH_EMULATOR_PORT", "9099")
            buildConfigField("int", "FIREBASE_FIRESTORE_EMULATOR_PORT", "8080")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"\"")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            buildConfigField("int", "FIREBASE_AUTH_EMULATOR_PORT", "0")
            buildConfigField("int", "FIREBASE_FIRESTORE_EMULATOR_PORT", "0")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyBetaConfiguration = tasks.register("verifyBetaConfiguration") {
    group = "verification"
    description = "Comprueba la configuración local necesaria para firmar y distribuir la beta."
    doLast {
        check(googleServicesFile.isFile) {
            "Falta app/google-services.json. Descárgalo desde el proyecto bobitos-dev de Firebase."
        }
        check(hasReleaseSigning) {
            "Falta keystore.properties o alguna propiedad de firma requerida. Consulta docs/BETA_DISTRIBUTION.md."
        }
    }
}

tasks.matching {
    it.name in setOf("assembleRelease", "bundleRelease")
}.configureEach {
    dependsOn(verifyBetaConfiguration)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.appcheck.playintegrity)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

hilt {
    enableAggregatingTask = true
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
}

ktlint {
    android.set(true)
}

kover {
    reports {
        filters {
            excludes {
                // Código generado (Hilt/Dagger, Compose, BuildConfig).
                classes(
                    "*_Factory",
                    "*_Factory\$*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "hilt_aggregated_deps.*",
                    "dagger.hilt.*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
    }
}
