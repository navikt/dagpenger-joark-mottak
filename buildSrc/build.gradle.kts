plugins {
    `kotlin-dsl`
    id("com.diffplug.spotless") version "7.0.1"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
}

spotless {
    kotlinGradle {
        ktlint()
    }
}
