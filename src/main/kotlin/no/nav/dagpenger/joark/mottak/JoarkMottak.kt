package no.nav.dagpenger.joark.mottak

import io.ktor.application.install
import io.ktor.features.DefaultHeaders
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.joark.mottak.KafkaConfig.aivenProducer
import no.nav.dagpenger.joark.mottak.KafkaConfig.joarkAivenConsumer
import no.nav.dagpenger.joark.mottak.KafkaConfig.joarkConsumer
import no.nav.dagpenger.streams.healthRoutes

fun main() {

    val config = Configuration()
    val journalfoeringReplicator = JournalfoeringReplicator(
        joarkConsumer(
            config.kafka.brokers,
            config.kafka.credential(),
            config.kafka.schemaRegisterUrl,
            config.kafka.joarkTopic.name
        ),
        aivenProducer(System.getenv())
    ).also { it.start() }

    val aivenJournalfoeringReplicator = JournalfoeringReplicator(
        joarkAivenConsumer(
            config.kafka.journalføringTopic,
            System.getenv()
        ),
        aivenProducer(System.getenv())
    ).also { it.start() }

    val server = embeddedServer(Netty, config.application.httpPort) {
        install(DefaultHeaders)
        routing {
            healthRoutes(listOf(journalfoeringReplicator, aivenJournalfoeringReplicator))
        }
    }.start()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(gracePeriodMillis = 3000, timeoutMillis = 5000)
        }
    )
}
