package no.nav.dagpenger.joark.mottak

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.PacketDeserializer
import no.nav.dagpenger.streams.PacketSerializer
import no.nav.dagpenger.streams.Topic
import no.nav.dagpenger.streams.Topics
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Serdes

private val localProperties = ConfigurationMap(
    mapOf(
        "kafka.bootstrap.servers" to "localhost:9092",
        "kafka.schema.registry.url" to "localhost:8081",
        "kafka.aapen.dok.journalfoering.topic" to "aapen-dok-journalfoering-v1",
        "joark.journalpostarkiv.url" to "localhost:8089",
        "oidc.sts.issuerurl" to "localhost:8082",
        "application.profile" to Profile.LOCAL.toString(),
        "application.httpPort" to "8080",
        "kafka.schema.registry.url" to "http://localhost:8081",
        "oidc.sts.issuerurl" to "https://localhost:8082",
        "personoppslag.url" to "https://localhost:1010",
            "graphql.apikey" to "hunter2"
    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "kafka.bootstrap.servers" to "b27apvl00045.preprod.local:8443,b27apvl00046.preprod.local:8443,b27apvl00047.preprod.local:8443",
        "kafka.aapen.dok.journalfoering.topic" to "aapen-dok-journalfoering-v1-q1",
        "joark.journalpostarkiv.url" to "https://saf-q1.nais.preprod.local/graphiq",
        "application.profile" to Profile.DEV.toString(),
        "application.httpPort" to "8080",
        "kafka.schema.registry.url" to "https://kafka-schema-registry.nais.preprod.local",
        "oidc.sts.issuerurl" to "https://security-token-service.nais.preprod.local",
        "personoppslag.url" to "https://dp-graphql.nais.preprod.local/graphql",
            "graphql.apikey" to "hunter2"
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "kafka.bootstrap.servers" to "a01apvl00145.adeo.no:8443,a01apvl00146.adeo.no:8443,a01apvl00147.adeo.no:8443,a01apvl00148.adeo.no:8443,a01apvl00149.adeo.no:8443,a01apvl00150.adeo.no:8443",
        "kafka.aapen.dok.journalfoering.topic" to "aapen-dok-journalfoering-v1-p",
        "joark.journalpostarkiv.url" to "https://saf.nais.adeo.no/graphql",
        "application.profile" to Profile.PROD.toString(),
        "application.httpPort" to "8080",
        "kafka.schema.registry.url" to "https://kafka-schema-registry.nais.adeo.no",
        "oidc.sts.issuerurl" to "https://security-token-service.nais.adeo.no",
        "personoppslag.url" to "https://dp-graphql.nais.adeo.no/graphql",
            "graphql.apikey" to "hunter2"
    )
)

private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
    "dev-fss" -> systemProperties() overriding EnvironmentVariables overriding devProperties
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
        val joarkTopic: Topic<String, GenericRecord> = Topics.JOARK_EVENTS.copy(name = config()[Key("kafka.aapen.dok.journalfoering.topic", stringType)]),
        val dagpengerJournalpostTopic: Topic<String, Packet> = Topic(
            "privat-dagpenger-journalpost-mottatt-v1",
            keySerde = Serdes.String(),
            valueSerde = Serdes.serdeFrom(PacketSerializer(), PacketDeserializer())
        ),
        val brokers: String = config()[Key("kafka.bootstrap.servers", stringType)],
        val schemaRegisterUrl: String = config()[Key("kafka.schema.registry.url", stringType)],
        val user: String? = config().getOrNull(Key("srvdagpenger.joark.mottak.username", stringType)),
        val password: String? = config().getOrNull(Key("srvdagpenger.joark.mottak.password", stringType))
    ) {
        fun credential(): KafkaCredential? {
            return if (user != null && password != null) {
                KafkaCredential(user, password)
            } else null
        }
    }

    data class Application(
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)],
        val oidcStsUrl: String = config()[Key("oidc.sts.issuerurl", stringType)],
        val joarkJournalpostArkivUrl: String = config()[Key("joark.journalpostarkiv.url", stringType)],
        val personOppslagUrl: String = config()[Key("personoppslag.url", stringType)],
        val graphQlApiKey: String = config()[Key("graphql.apikey", stringType)]
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}
