plugins {
    `kotlin-dsl`
    id("com.diffplug.spotless") version "6.23.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.23.0")
}

spotless {
    kotlinGradle {
        ktlint()
    }
}
