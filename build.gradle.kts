plugins {
    id("common")
    application
    id(Shadow.shadow) version Shadow.version
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
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

configurations {
    all {
        resolutionStrategy {
            force("com.fasterxml.jackson.core:jackson-databind:2.15.2")
            force("com.fasterxml.jackson.core:jackson-core:2.15.3")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.navikt:dagpenger-streams:20230831.f3d785")

    implementation(Prometheus.common)
    implementation(Prometheus.log4j2)

    implementation(Konfig.konfig)

    implementation(Jackson.core)
    implementation(Jackson.kotlin)

    implementation(Log4j2.api)
    implementation(Log4j2.core)
    implementation(Log4j2.slf4j)
    implementation(Log4j2.library("layout-template-json"))
    implementation(Kotlin.Logging.kotlinLogging)

    implementation(Kafka.clients)
    implementation(Kafka.streams)
    implementation(Kafka.Confluent.avroStreamSerdes)

    implementation(Ktor2.Server.library("netty"))
    implementation(Ktor2.Server.library("default-headers"))

    testImplementation(kotlin("test"))
    testImplementation(Junit5.api)
    testImplementation(KoTest.runner)
    testImplementation(KoTest.assertions)
    testRuntimeOnly(Junit5.engine)
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
