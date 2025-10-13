package no.nav.dagpenger.joark.mottak

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

private val localProperties =
    ConfigurationMap(
        mapOf(
            "application.profile" to Profile.LOCAL.toString(),
            "application.httpPort" to "8080",
            "kafka.schema.registry.url" to "http://localhost:8081",
            "kafka.aapen.dok.journalfoering.topic" to "teamdokumenthandtering.aapen-dok-journalfoering-q1",
        ),
    )
private val devProperties =
    ConfigurationMap(
        mapOf(
            "application.profile" to Profile.DEV.toString(),
            "application.httpPort" to "8080",
            "kafka.aapen.dok.journalfoering.topic" to "teamdokumenthandtering.aapen-dok-journalfoering-q1, teamdokumenthandtering.aapen-dok-journalfoering",
        ),
    )
private val prodProperties =
    ConfigurationMap(
        mapOf(
            "application.profile" to Profile.PROD.toString(),
            "application.httpPort" to "8080",
            "kafka.aapen.dok.journalfoering.topic" to "teamdokumenthandtering.aapen-dok-journalfoering",
        ),
    )

private fun config() =
    when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
        "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
        else -> {
            systemProperties() overriding EnvironmentVariables overriding localProperties
        }
    }

data class Configuration(
    val kafka: Kafka = Kafka(),
    val application: Application = Application(),
) {
    data class Kafka(
        val journalføringTopic: String = config()[Key("kafka.aapen.dok.journalfoering.topic", stringType)],
    )

    data class Application(
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)],
    )
}

enum class Profile {
    LOCAL,
    DEV,
    PROD,
}

internal const val JOURNALFOERING_REPLICATOR_GROUPID = "dagpenger-journalfoering-aiven-replicator-v1"
