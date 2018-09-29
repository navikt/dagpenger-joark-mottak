plugins {
    id("application")
    kotlin("jvm") version "1.2.51"
    id("com.diffplug.gradle.spotless") version "3.13.0"
    id("com.palantir.docker") version "0.20.1"
    id("com.palantir.git-version") version "0.11.0"
    id("com.adarshr.test-logger") version "1.5.0"
}

apply {
    plugin("com.diffplug.gradle.spotless")
    plugin("com.adarshr.test-logger")
}

repositories {
    jcenter()
    maven(url = "http://packages.confluent.io/maven/")
    maven(url = "https://repo.adeo.no/repository/maven-releases/")
}

val gitVersion: groovy.lang.Closure<Any> by extra
version = gitVersion()
group = "no.nav.dagpenger"

application {
    applicationName = "dagpenger-joark-mottak"
    mainClassName = "no.nav.dagpenger.joark.mottak.JoarkMottak"
}

docker {
    name = "navikt/${application.applicationName}"
    buildArgs(mapOf(
        "APP_NAME" to application.applicationName,
        "DIST_TAR" to "${application.applicationName}-${project.version}"
    ))
    files(tasks.findByName("distTar")?.outputs)
    pull(true)
    tags(project.version.toString())
}

val kotlinLoggingVersion = "1.4.9"
val fuelVersion = "1.15.0"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("no.nav.dagpenger:streams:0.1.1")
    implementation("no.nav.dagpenger:events:0.1.3")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("com.github.tomakehurst:wiremock:2.18.0")
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
