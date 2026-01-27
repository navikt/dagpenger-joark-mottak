plugins {
    id("common")
    application
    alias(libs.plugins.shadow.jar)
}

buildscript {
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

application {
    applicationName = "dagpenger-joark-mottak"
    mainClass.set("no.nav.dagpenger.joark.mottak.JoarkMottakKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations {
    all {
        resolutionStrategy {
            force("com.fasterxml.jackson.core:jackson-databind:2.21.0")
            force("com.fasterxml.jackson.core:jackson-core:2.21.0")
        }
    }
}

val kafkaVersjon = "8.1.1-ce"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.prometheus:simpleclient_common:0.16.0")
    implementation("io.prometheus:simpleclient_log4j2:0.16.0")

    implementation(libs.konfig)

    implementation(libs.jackson.core)
    implementation(libs.jackson.kotlin)

    implementation("org.apache.kafka:kafka-clients:$kafkaVersjon")
    implementation("org.apache.kafka:kafka-streams:$kafkaVersjon")
    implementation("io.confluent:kafka-streams-avro-serde:8.1.1")

    implementation("io.ktor:ktor-server-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-default-headers:${libs.versions.ktor.get()}")

    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.core)
    runtimeOnly(libs.logback.classic)
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:9.0") {
        exclude("com.fasterxml.jackson.core")
    }

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
}

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
}
