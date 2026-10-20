plugins {
    application
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("org.totschnig.changelog.Main")
}

dependencies {
    implementation("net.sf.saxon:Saxon-HE:10.5")
}
