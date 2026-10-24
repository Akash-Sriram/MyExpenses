import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

fun getPropertyIfDefined(property: String, defaultValue: String): String =
    project.findProperty(property)?.toString() ?: defaultValue

fun getDate(): String {
    val gitDate = try {
        ProcessBuilder("git", "show", "--no-patch", "--format=%cd", "--date=format-local:%Y,%-m,%-d,%-H,%-M")
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
    } catch (_: Exception) {
        ""
    }
    val dateStr = gitDate.ifEmpty {
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy,M,d,H,m"))
    }
    return "java.time.LocalDateTime.of($dateStr)"
}

android {
    compileSdk = (rootProject.extra["compileSdkVersion"] as Int)
    useLibrary("android.test.mock")

    defaultConfig {
        applicationId = "org.totschnig.myexpenses"
        minSdk = (rootProject.extra["minSdkVersion"] as Int)
        targetSdk = (rootProject.extra["targetSdkVersion"] as Int)
        versionCode = (rootProject.extra["versionCode"] as Int)
        versionName = (rootProject.extra["versionName"] as String)

        buildConfigField("String", "DISTRIBUTION", "\"GITHUB\"")
        buildConfigField("java.time.LocalDateTime", "BUILD_DATE", getDate())
        testInstrumentationRunner = "org.totschnig.myexpenses.MyTestRunner"

        @Suppress("UNCHECKED_CAST")
        androidResources.localeFilters += (rootProject.extra["resConfig"] as List<String>)
        missingDimensionStrategy("textRecognition", "intern")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-project.txt")
            manifestPlaceholders["appName"] = "@string/app_name"
            applicationIdSuffix = ""
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "My Expenses Debug"
            buildConfigField("int", "TEST_SCENARIO", getPropertyIfDefined("testScenario", "1"))
            buildConfigField("String", "OPEN_EXCHANGE_RATES_API_KEY", "\"${getPropertyIfDefined("OPEN_EXCHANGE_RATES_API_KEY", "")}\"")
            buildConfigField("String", "COIN_API_API_KEY", "\"${getPropertyIfDefined("COIN_API_API_KEY", "")}\"")
            buildConfigField("boolean", "BETA", getPropertyIfDefined("BETA", "false"))
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
    }

    buildTypes.configureEach {
        val suffix = applicationIdSuffix ?: ""
        resValue("string", "application_id", "${defaultConfig.applicationId}$suffix")
        resValue("string", "sync_account_type", "${defaultConfig.applicationId}$suffix.sync")
    }

    flavorDimensions += listOf("textRecognition")

    productFlavors {
        create("intern") {
            dimension = "textRecognition"
            isDefault = true
            matchingFallbacks += listOf("intern")
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("META-INF/*", "META-INF/licenses/**")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
        resValues = true
    }

    dynamicFeatures.clear()

    lint {
        abortOnError = true
        checkDependencies = true
        disable += listOf("MissingQuantity", "MissingTranslation", "ImpliedQuantity", "Instantiatable")
    }

    bundle {
        language {
            enableSplit = !project.gradle.startParameter.taskNames.any {
                it.contains("Test") || it.contains("Debug") || it.contains("Connected")
            }
        }
    }

    namespace = "org.totschnig.myexpenses"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1024m"
    systemProperty("robolectric.logging", "stdout")
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
    api(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.animation.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.runtime.livedata)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.paging.compose)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.donut.compose)
    implementation(libs.donut)
    implementation(libs.androidx.compose.material3.window.size)

    ksp(libs.dagger.compiler)
    implementation(libs.dagger)

    implementation(libs.mpandroidchart)
    api(libs.simpledialogfragments)
    api(libs.timber)

    implementation(libs.slf4j.api)
    implementation(libs.logback.android)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.viewpager2)

    api(libs.material)

    api(libs.androidx.documentfile)

    implementation(libs.androidx.gridlayout)
    api(libs.androidx.preference)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.drawer)

    implementation(libs.flexbox)

    implementation(libs.copper.flow)
    implementation(libs.okhttp)

    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.sqlite.framework)

    implementation(libs.kotlinx.html)

    // Lifecycle components
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ViewModel Kotlin support
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.android.image.cropper)
    api(libs.androidx.core.ktx)
    implementation(libs.androidx.core.remoteviews)
    implementation(libs.kotlin.reflect)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)



    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.reorderable)
    implementation(libs.text.recognition.devanagari)

    debugImplementation(libs.androidx.fragment.testing.manifest)
    testImplementation(libs.androidx.fragment.testing)
    androidTestImplementation(libs.androidx.fragment.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.screengrab)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.espresso.idling.resource)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.barista) {
        exclude(group = "org.jetbrains.kotlin")
    }
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.core.testing)
    androidTestImplementation(project(":shared-test"))

    testImplementation(libs.barista) {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation(libs.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.junitparams)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.rules)
    testImplementation(libs.androidx.junit)
    testImplementation(project(":shared-test"))
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.snakeyaml)
}

tasks.register("echoPublishTag") {
    doLast {
        val currentBranch = try {
            ProcessBuilder("git", "branch", "--show-current").start().inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) {
            ""
        }

        if (currentBranch == "master") {
            val versionCode = rootProject.extra["versionCode"]
            val versionName = rootProject.extra["versionName"]
            println("git add metadata/*/changelogs/$versionCode.txt")
            println("git commit -m \"Release $versionName\"")
            println("git push")
            println("git tag r$versionCode; git push origin r$versionCode")
        } else {
            println("Release should be built on master, current branch is $currentBranch")
        }
    }
}

configurations.configureEach {
    exclude(group = "com.google.j2objc", module = "j2objc-annotations")
}

tasks.register("r8Version") {
    doLast {
        println(com.android.tools.r8.Version.getVersionString())
    }
}
