plugins {
    `kotlin-dsl`
    id("com.diffplug.spotless") version "6.21.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.20.0")
}

spotless {
    kotlinGradle {
        ktlint()
    }
}
