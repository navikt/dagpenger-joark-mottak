package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.kotest.matchers.shouldBe
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.oidc.StsOidcClient
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Properties
import java.util.Random
import kotlin.test.assertEquals

class JoarkMottakComponentTest {

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topicInfos = listOf(
                KafkaEnvironment.TopicInfo("aapen-dok-journalfoering-v1"),
                KafkaEnvironment.TopicInfo("privat-dagpenger-journalpost-mottatt-v1"),
                KafkaEnvironment.TopicInfo("privat-dagpenger-soknadsdata-v1")
            )
        )

        val wireMock by lazy {
            WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).also {
                it.start()
            }
        }

        val configuration = Configuration().copy(
            kafka = Configuration.Kafka(
                brokers = embeddedEnvironment.brokersURL,
                schemaRegisterUrl = embeddedEnvironment.schemaRegistry!!.url,
                password = password,
                user = username
            ),
            application = Configuration.Application(
                httpPort = getAvailablePort(),
                oidcStsUrl = wireMock.baseUrl(),
                personOppslagBaseUrl = "${wireMock.baseUrl()}/pdl-api/",
                joarkJournalpostArkivBaseUrl = "${wireMock.baseUrl()}/saf/",
            )

        )

        val stsOidcClient =
            StsOidcClient(
                configuration.application.oidcStsUrl,
                configuration.kafka.user,
                configuration.kafka.password
            )

        val joarkMottak = JoarkMottak(
            configuration,
            DummyJournalpostArkiv(),
            InnløpPacketCreator(PersonOppslag(configuration.application.personOppslagBaseUrl, stsOidcClient))

        )

        @BeforeAll
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
            joarkMottak.start()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            joarkMottak.stop()
            embeddedEnvironment.tearDown()
        }
    }

    @Test
    fun ` embedded kafka cluster is up and running `() {
        assertEquals(embeddedEnvironment.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }

    @Test
    @Disabled
    fun ` Component test of JoarkMottak  where hendelsesType is 'MidlertidigJournalført'`() {

        wireMock.addStubMapping(
            WireMock.post(WireMock.urlEqualTo("/pdl-api/graphql"))
                .willReturn(
                    WireMock.okJson("/test-data/example-person-payload.json".getResouce())
                )
                .build()
        )

        wireMock.addStubMapping(
            WireMock.get(WireMock.urlEqualTo("/rest/v1/sts/token/?grant_type=client_credentials&scope=openid"))
                .willReturn(
                    WireMock.okJson(
                        """
                   {
                     "access_token": "token",
                     "token_type": "Bearer",
                     "expires_in": 3600
                    } 
                        """.trimIndent()
                    )
                )
                .build()
        )

        val kjoarkEvents = mapOf(
            Random().nextLong() to "DAG",
            Random().nextLong() to "SOMETHING",
            Random().nextLong() to "DAG",
            Random().nextLong() to "DAG",
            Random().nextLong() to "DAG",
            Random().nextLong() to "DAG",
            Random().nextLong() to "DAG",
            Random().nextLong() to "JP",
            Random().nextLong() to "DAG",
            Random().nextLong() to "DAG"
        )

        val dummyJoarkProducer = dummyJoarkProducer(configuration)

        kjoarkEvents.forEach { id, tema ->
            dummyJoarkProducer.produceEvent(journalpostId = id, tema = tema, hendelsesType = "MidlertidigJournalført")
        }

        val behovConsumer: KafkaConsumer<String, Packet> = behovConsumer(configuration)

        Thread.sleep(1000)
        val behov = behovConsumer.poll(Duration.ofSeconds(5)).toList()

        behov.size shouldBe kjoarkEvents.filterValues { it == "DAG" }.size
    }

    private fun behovConsumer(config: Configuration): KafkaConsumer<String, Packet> {
        val consumer: KafkaConsumer<String, Packet> = KafkaConsumer(
            Properties().apply {
                put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, config.kafka.schemaRegisterUrl)
                put(ConsumerConfig.GROUP_ID_CONFIG, "dummy-dagpenger-innkomne-jp")
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafka.brokers)
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    configuration.kafka.dagpengerJournalpostTopic.keySerde.deserializer().javaClass.name
                )
                put(
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                    configuration.kafka.dagpengerJournalpostTopic.valueSerde.deserializer().javaClass.name
                )
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(
                    SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${config.kafka.user}\" password=\"${config.kafka.password}\";"
                )
            }
        )

        consumer.subscribe(listOf(config.kafka.dagpengerJournalpostTopic.name))
        return consumer
    }

    private fun dummyJoarkProducer(config: Configuration): DummyJoarkProducer {
        val props = Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, config.kafka.schemaRegisterUrl)
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafka.brokers)
            put(StreamsConfig.CLIENT_ID_CONFIG, "dummy-joark-producer")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java.name)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${config.kafka.user}\" password=\"${config.kafka.password}\";"
            )
        }

        val dummyJoarkProducer = DummyJoarkProducer(props)
        return dummyJoarkProducer
    }

    internal fun String.getResouce(): String = JoarkMottakComponentTest::class.java.getResource(this).readText()
}
