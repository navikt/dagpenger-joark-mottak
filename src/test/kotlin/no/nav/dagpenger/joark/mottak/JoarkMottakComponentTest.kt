package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.streams.Topics
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.streams.StreamsConfig
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoarkMottakComponentTest {

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
                users = listOf(JAASCredential(username, password)),
                autoStart = false,
                withSchemaRegistry = true,
                withSecurity = true,
                topics = listOf(Topics.JOARK_EVENTS.name, Topics.INNGÅENDE_JOURNALPOST.name)
        )

        @BeforeClass
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            embeddedEnvironment.tearDown()
        }
    }

    @Test
    fun ` embedded kafka cluseter is up and running `() {
        assertEquals(embeddedEnvironment.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }

    @Test
    fun ` Component test of JoarkMottak `() {

        //given an environment
        val env = Environment(
                username = username,
                password = password,
                bootstrapServersUrl = embeddedEnvironment.brokersURL,
                schemaRegistryUrl = embeddedEnvironment.schemaRegistry!!.url,
                oicdStsUrl = "localhost",
                journalfoerinngaaendeV1Url = "localhost"
        )

        // when
        val joarkMottak = JoarkMottak(env, JournalpostArkivDummy())
        joarkMottak.start()

        val dummyJoarkProducer = dummyJoarkProducer(env)
        dummyJoarkProducer.produceDummyMessage()

        val behovConsumer: KafkaConsumer<String, Behov> = behovConsumer(env)
        val behov = behovConsumer.poll(Duration.ofSeconds(5)).toList()

        joarkMottak.stop()

        // then
        assertTrue { behov.size == 1 }
        assertEquals("DAG", behov.get(0).value().getJournalpost().getTema())
    }

    private fun behovConsumer(env: Environment): KafkaConsumer<String, Behov> {
        val consumer: KafkaConsumer<String, Behov> = KafkaConsumer(Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
            put(ConsumerConfig.GROUP_ID_CONFIG, "dummy-dagpenger-innkomne-jp")
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, INNGÅENDE_JOURNALPOST.keySerde.deserializer().javaClass.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, INNGÅENDE_JOURNALPOST.valueSerde.deserializer().javaClass.name)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.username}\" password=\"${env.password}\";")
        })

        consumer.subscribe(listOf(INNGÅENDE_JOURNALPOST.name))
        return consumer
    }

    private fun dummyJoarkProducer(env: Environment): DummyJoarkProducer {
        val props = Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(StreamsConfig.CLIENT_ID_CONFIG, "dummy-joark-producer")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.username}\" password=\"${env.password}\";")
        }

        val dummyJoarkProducer = DummyJoarkProducer(props)
        return dummyJoarkProducer
    }
}