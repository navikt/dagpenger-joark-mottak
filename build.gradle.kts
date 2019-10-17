import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

buildscript {
    repositories {
        jcenter()
    }
}

apply {
    plugin(Spotless.spotless)
}

repositories {
    mavenCentral()
    jcenter()
    maven("http://packages.confluent.io/maven/")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

application {
    applicationName = "dagpenger-joark-mottak"
    mainClassName = "no.nav.dagpenger.joark.mottak.JoarkMottakKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions.jvmTarget = "1.8" }

configurations {
    all {
        resolutionStrategy {
            force("com.fasterxml.jackson.core:jackson-databind:2.10.0")
            force("com.fasterxml.jackson.core:jackson-core:2.10.0")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(Dagpenger.Streams)
    implementation(Dagpenger.Events)

    implementation(Dagpenger.Biblioteker.stsKlient)

    implementation(Prometheus.common)

    implementation(Konfig.konfig)

    implementation(Fuel.fuel)
    implementation(Fuel.library("moshi"))
    implementation(Moshi.moshi)
    implementation(Moshi.moshiKotlin)
    implementation(Moshi.moshiAdapters)

    implementation(Log4j2.api)
    implementation(Log4j2.core)
    implementation(Log4j2.slf4j)
    implementation(Log4j2.Logstash.logstashLayout)
    implementation(Kotlin.Logging.kotlinLogging)

    implementation(Kafka.clients)
    implementation(Kafka.streams)
    implementation(Kafka.Confluent.avroStreamSerdes)

    implementation(Ktor.serverNetty)

    testImplementation(kotlin("test"))
    testImplementation(Junit5.api)
    testImplementation(Junit5.kotlinRunner)
    testRuntimeOnly(Junit5.engine)
    testImplementation(Wiremock.standalone)
    testImplementation(KafkaEmbedded.env)
    testImplementation(Kafka.streamTestUtils)
    testImplementation(Mockk.mockk)
}

spotless {
    kotlin {
        ktlint(Klint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint(Klint.version)
    }
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
