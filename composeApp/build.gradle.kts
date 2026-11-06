import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences)
            implementation(libs.ktor.client.core)
            implementation("io.ktor:ktor-client-websockets:${libs.versions.ktor.get()}")
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation("androidx.work:work-runtime-ktx:2.10.1")
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.androidx.activity.compose)
            implementation(libs.pdfbox.android)
            implementation(libs.mlkit.text.recognition)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.ziaee.zabankhan"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ziaee.zabankhan"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("APP_VERSION_NAME").get()
    }
    val signingPropsFile = file(providers.gradleProperty("zabankhanSigningProps").getOrElse(
        "${System.getProperty("user.home")}/.android/zabankhan-release.properties"
    ))
    if (signingPropsFile.exists()) {
        val props = Properties().apply { signingPropsFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    flavorDimensions += "store"
    productFlavors {
        // play: may use Google Play services (Drive backup). bazaar: no GMS (Cafe Bazaar / Myket).
        create("play") { dimension = "store" }
        create("bazaar") { dimension = "store" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Shared Compose runtime tests use Android Trace markers without a device runtime.
    testOptions.unitTests.isReturnDefaultValues = true
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
    // Google sign-in for Drive backup ships only in the Play flavour; bazaar stays free of it (src/androidPlay vs src/androidBazaar).
    add("playImplementation", "com.google.android.gms:play-services-auth:21.4.0")
}

// Android's SQLite AAR contains device JNI libraries. Unit tests need the host
// loader and native library; keep this configuration out of every APK dependency graph.
val sqliteHostTestRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}
dependencies {
    add(sqliteHostTestRuntime.name, "androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqlite.get()}")
}
tasks.withType<Test>().matching { it.name.endsWith("UnitTest") }.configureEach {
    inputs.files(sqliteHostTestRuntime)
    // AGP assigns the final unit-test classpath after task configuration.
    // Prepend at execution time so the host loader wins over the Android one.
    doFirst { classpath = files(sqliteHostTestRuntime) + classpath }
}

compose.resources {
    packageOfResClass = "com.ziaee.zabankhan.resources"
}

// iOS simulator tests read fixtures from the host filesystem (simctl forwards SIMCTL_CHILD_* vars).
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    environment("SIMCTL_CHILD_PDF_FIXTURE", file("src/iosTest/resources/fr-sample.pdf").absolutePath)
}
