plugins {
    id("common")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
            force("com.fasterxml.jackson.core:jackson-databind:2.17.2")
            force("com.fasterxml.jackson.core:jackson-core:2.17.2")
        }
    }
}

val log4j2Versjon = "2.24.0"
val kafkaVersjon = "7.7.1-ce"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.navikt:dagpenger-streams:20230831.f3d785")

    implementation("io.prometheus:simpleclient_common:0.16.0")
    implementation("io.prometheus:simpleclient_log4j2:0.16.0")

    implementation(libs.konfig)

    implementation(libs.jackson.core)
    implementation(libs.jackson.kotlin)

    implementation("org.apache.logging.log4j:log4j-api:$log4j2Versjon")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Versjon")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j2Versjon")
    implementation("org.apache.logging.log4j:log4j-layout-template-json:$log4j2Versjon")

    implementation(libs.kotlin.logging)

    implementation("org.apache.kafka:kafka-clients:$kafkaVersjon")
    implementation("org.apache.kafka:kafka-streams:$kafkaVersjon")
    implementation("io.confluent:kafka-streams-avro-serde:7.7.1")

    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-default-headers:${libs.versions.ktor.get()}")

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
}

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
}
