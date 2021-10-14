package no.nav.dagpenger.joark.mottak

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Topic
import no.nav.dagpenger.streams.Topics
import org.apache.avro.generic.GenericRecord

private val localProperties = ConfigurationMap(
    mapOf(
        "kafka.bootstrap.servers" to "localhost:9092",
        "kafka.schema.registry.url" to "localhost:8081",
        "kafka.aapen.dok.journalfoering.topic" to "aapen-dok-journalfoering-v1",
        "application.profile" to Profile.LOCAL.toString(),
        "application.httpPort" to "8080",
        "kafka.schema.registry.url" to "http://localhost:8081",
        "srvdagpenger.joark.mottak.username" to "user",
        "srvdagpenger.joark.mottak.password" to "password",
        "AAPEN_DOK_JOURNALFORING_TOPIC" to "teamdokumenthandtering.aapen-dok-journalfoering-q1",
    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "kafka.bootstrap.servers" to "b27apvl00045.preprod.local:8443,b27apvl00046.preprod.local:8443,b27apvl00047.preprod.local:8443",
        "kafka.aapen.dok.journalfoering.topic" to "aapen-dok-journalfoering-v1-q1",
        "application.profile" to Profile.DEV.toString(),
        "application.httpPort" to "8080",
        "kafka.schema.registry.url" to "https://kafka-schema-registry.nais-q.adeo.no"
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "kafka.bootstrap.servers" to "a01apvl00145.adeo.no:8443,a01apvl00146.adeo.no:8443,a01apvl00147.adeo.no:8443,a01apvl00148.adeo.no:8443,a01apvl00149.adeo.no:8443,a01apvl00150.adeo.no:8443",
        "kafka.aapen.dok.journalfoering.topic" to "aapen-dok-journalfoering-v1-p",
        "application.profile" to Profile.PROD.toString(),
        "application.httpPort" to "8080",
        "kafka.schema.registry.url" to "https://kafka-schema-registry.nais.adeo.no",
        "AAPEN_DOK_JOURNALFORING_TOPIC" to "teamdokumenthandtering.aapen-dok-journalfoering",
    )
)

private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
    "dev-fss" -> systemProperties() overriding EnvironmentVariables overriding devProperties
    "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
    "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
    "prod-fss" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
    else -> {
        systemProperties() overriding EnvironmentVariables overriding localProperties
    }
}

data class Configuration(
    val kafka: Kafka = Kafka(),
    val application: Application = Application()
) {
    data class Kafka(
        val joarkTopic: Topic<String, GenericRecord> = Topics.JOARK_EVENTS.copy(
            name = config()[
                Key(
                    "kafka.aapen.dok.journalfoering.topic",
                    stringType
                )
            ]
        ),
        val deseralizationExceptionHandler: String? = config().getOrNull(
            Key(
                "deserialization.exception.handler",
                stringType
            )
        ),
        val journalføringTopic: String = config()[Key("AAPEN_DOK_JOURNALFORING_TOPIC", stringType)],
        val brokers: String = config()[Key("kafka.bootstrap.servers", stringType)],
        val schemaRegisterUrl: String = config()[Key("kafka.schema.registry.url", stringType)],
        val user: String = config()[Key("srvdagpenger.joark.mottak.username", stringType)],
        val password: String = config()[Key("srvdagpenger.joark.mottak.password", stringType)]
    ) {
        fun credential(): KafkaCredential {
            return KafkaCredential(user, password)
        }
    }

    data class Application(
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)],
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}

internal const val JOURNALFOERING_REPLICATOR_GROUPID = "dagpenger-journalfoering-aiven-replicator-v1"
