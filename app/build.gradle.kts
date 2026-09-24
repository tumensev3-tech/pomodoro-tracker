import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin.android plugin: AGP 9 provides Kotlin support itself and rejects it.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

// Release signing. The keystore and its passwords live *outside* the repository: a signing key
// leaked into a public history has no revocation path, and .gitignore is only one layer of defence.
// Looked up in ~/.pomodoro; the old in-project location still works for a checkout not yet migrated.
val keystorePropsFile = sequenceOf(
    File(System.getProperty("user.home"), ".pomodoro/keystore.properties"),
    rootProject.file("keystore.properties")
).firstOrNull { it.exists() }

val keystoreProps = Properties().apply {
    keystorePropsFile?.let { file -> FileInputStream(file).use { load(it) } }
}

android {
    namespace = "com.drklo.pomodoro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.drklo.pomodoro"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The exported schemas are the reference migrations are checked against, so they are versioned.
    sourceSets {
        // MigrationTestHelper reads them from the test APK's assets.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        // Most unit tests touch pure logic; stubbed android.* calls return defaults instead of
        // throwing. Robolectric substitutes real implementations for the tests that ask for it, so
        // this only governs the rest.
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the merged manifest and the compiled resources: the composables under
        // test resolve strings and theme attributes, and createComposeRule() launches the empty
        // activity that ui-test-manifest contributes to the debug manifest.
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        if (keystorePropsFile != null) {
            create("release") {
                // Absolute path since the move out of the repo; a relative one still resolves
                // against the properties file that named it.
                storeFile = File(keystoreProps.getProperty("storeFile")).let { named ->
                    if (named.isAbsolute) named else File(keystorePropsFile.parentFile, named.path)
                }
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v3 carries the key-rotation record. The app is distributed as APK files and
                // lives entirely on this signature, so without v3 a compromised key could never be
                // replaced without orphaning every existing install. v4 is left off: it emits a
                // separate .idsig that only speeds up `adb install --incremental`.
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Install the personal build alongside the official v1.2 APK, whose signature belongs
            // to the original author. This keeps the original app available while the custom build
            // is tested and lets projects/history be moved with the built-in export/import.
            applicationIdSuffix = ".custom"
            versionNameSuffix = "-custom"
        }
        release {
            // Without R8 the APK was 41.7 MB, of which 42 MB was dex: material-icons-extended
            // compiles thousands of icons and the app draws eleven of them. Shrinking is what makes
            // the download reasonable for something handed out as a file.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    lint {
        // Anything new fails the build; the few accepted leftovers live in the baseline.
        warningsAsErrors = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
        disable += setOf(
            // Dependency freshness is a deliberate, separate chore — not a per-build gate.
            "GradleDependency",
            "AndroidGradlePluginVersion",
            // Same chore, and additionally not reproducible: OldTargetApi compares targetSdk
            // against whatever platforms the machine happens to have installed, so one commit
            // passes here and fails on a CI runner with a newer SDK. A gate that depends on the
            // machine rather than the code cannot gate anything. Raising targetSdk is a real task
            // with real device testing behind it, not something to be forced by a lint run.
            "OldTargetApi",
            // The app ships as a plain APK, never as an AAB, so language splits cannot happen.
            "AppBundleLocaleChanges"
        )
    }
}

// Kotlin 2.3 removed the `kotlinOptions` block inside `android`; this is the same settings in the
// compilerOptions DSL that replaced it.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Warnings must not accumulate: every one of them is either fixed or explicitly suppressed.
        allWarningsAsErrors = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Catches what Android Lint does not: complexity, long methods, duplication, formatting.
// `detekt-formatting` wraps ktlint, so no separate formatting plugin is needed.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    // What is left in the baseline is debt, not noise: unnamed numbers in drawing and audio code,
    // and a handful of long parameter lists and complex composables. Formatting was fixed rather
    // than recorded, and the rules that were firing on data — colour palettes, test literals, enum
    // entries — are configured in config/detekt.yml, because a rule aimed at the wrong thing is a
    // configuration problem and only a baseline entry pretends otherwise.
    baseline = file("detekt-baseline.xml")
    // Test code is held to the same bar as production code. This used to be a `check` dependency on
    // the per-variant `detektDebugUnitTest` task, which detekt registered off the Kotlin Android
    // plugin — the plugin AGP 9 no longer wants. Naming the source sets keeps the policy in force
    // and, unlike the old arrangement, states it where someone will read it.
    source.setFrom(
        files("src/main/java", "src/test/java", "src/androidTest/java")
    )
}

dependencies {
    // The app never serialises anything itself, but Room 2.8's migration tooling does, and it needs
    // kotlinx-serialization 1.8.1 or newer. Something on the app's own classpath pins 1.7.3, and
    // Gradle's consistent resolution then forces that same 1.7.3 onto the androidTest classpath —
    // where MigrationTestHelper reads the exported schemas and dies with an AbstractMethodError on
    // a method whose signature changed between the two. A constraint fixes the version without
    // adding a library the production code does not use.
    constraints {
        implementation(libs.kotlinx.serialization.json) {
            because("Room 2.8 migration tooling requires >= 1.8.1; see MigrationTest")
        }
    }

    detektPlugins(libs.detekt.formatting)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    // Hosts the empty activity that createComposeRule() launches; debug-only by design.
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The Compose tests run on the JVM under Robolectric rather than on a device. What they assert
    // is the semantics tree — roles, state descriptions, click actions — which is produced by
    // Compose itself and does not depend on a real renderer, so a device buys nothing and costs an
    // emulator in CI. The instrumented set below stays for what genuinely needs Android: SQLite
    // migrations and the repository against a real database.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
