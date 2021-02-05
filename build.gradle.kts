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

//    implementation(Dagpenger.Biblioteker.stsKlient)
//    todo Bruk global konstant
    implementation("com.github.navikt.dp-biblioteker:sts-klient:2021.02.04-18.04.66992d1db9a0")
    implementation("no.finn.unleash:unleash-client-java:3.2.9")
    implementation(Dagpenger.Events)
    implementation(Dagpenger.Streams)

    implementation(Jackson.core)
    implementation(Jackson.kotlin)

    implementation(Kafka.Confluent.avroStreamSerdes)
    implementation(Kafka.clients)
    implementation(Kafka.streams)

    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)
    implementation(Ktor.library("client-auth-jvm"))
    implementation(Ktor.library("client-cio-jvm"))
    implementation(Ktor.library("client-jackson"))
    implementation(Ktor.library("client-logging"))
    implementation(Ktor.serverNetty)

    implementation(Log4j2.Logstash.logstashLayout)
    implementation(Log4j2.api)
    implementation(Log4j2.core)
    implementation(Log4j2.slf4j)

    implementation(Prometheus.common)
    implementation(Prometheus.log4j2)

    testImplementation(Junit5.api)
    testImplementation(Kafka.streamTestUtils)
    testImplementation(KafkaEmbedded.env)
    testImplementation(KoTest.assertions)
    testImplementation(KoTest.runner)
    testImplementation(Ktor.library("client-mock"))
    testImplementation(Mockk.mockk)
    testImplementation(kotlin("test"))
    testRuntimeOnly(Junit5.engine)
}

spotless {
    kotlin {
        ktlint(Ktlint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.kt*")
        ktlint(Ktlint.version)
    }
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
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

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("jar") {
    dependsOn("test")
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}
