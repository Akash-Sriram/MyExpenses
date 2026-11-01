plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

extra["resConfig"] = listOf("en")
extra["versionCode"] = 884
extra["versionName"] = "4.1.3-pre"
extra["compileSdkVersion"] = 37
extra["minSdkVersion"] = 35
extra["targetSdkVersion"] = 35

allprojects {
    configurations.all {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }
}

