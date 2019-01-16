import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("application")
    kotlin("jvm") version Versions.kotlin
    id("com.diffplug.gradle.spotless") version Versions.spotless
    id("info.solidsoft.pitest") version Versions.pitest
    id("com.github.johnrengelman.shadow") version Versions.shadow
}

buildscript {
    repositories {
        mavenCentral()
    }
}

apply {
    plugin("com.diffplug.gradle.spotless")
    plugin("info.solidsoft.pitest")
}

repositories {
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://dl.bintray.com/kittinunf/maven")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

application {
    applicationName = "dagpenger-joark-mottak"
    mainClassName = "no.nav.dagpenger.joark.mottak.JoarkMottak"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

configurations {
    all {
        resolutionStrategy {
            force("com.fasterxml.jackson.core:jackson-databind:2.9.7")
            force("com.fasterxml.jackson.core:jackson-core:2.9.7")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation(DagpengerLibs.events)
    implementation(DagpengerLibs.streams)
    implementation(DagpengerLibs.metrics)

    implementation(Libs.fuel)
    implementation(Libs.fuelGson)

    implementation(Libs.kotlinLogging)
    implementation(Libs.log4jApi)
    implementation(Libs.log4jCore)
    implementation(Libs.log4jOverSlf4j)
    implementation(Libs.logStashLogging)

    compile(Libs.kafkaClients)
    compile(Libs.kafkaStreams)
    compile(Libs.streamSerDes)

    compile(Libs.ktorNetty)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(TestLibs.junit5Api)
    testRuntimeOnly(TestLibs.junit5Engine)
    testImplementation(TestLibs.wiremock)
    testImplementation(TestLibs.kafkaEmbedded)
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint()
    }
}

pitest {
    threads = 4
    coverageThreshold = 77
    pitestVersion = "1.4.3"
    avoidCallsTo = setOf("kotlin.jvm.internal")
    timestampedReports = false
    targetClasses = setOf("no.nav.dagpenger.*")
}

tasks.getByName("test").finalizedBy("pitest")

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
