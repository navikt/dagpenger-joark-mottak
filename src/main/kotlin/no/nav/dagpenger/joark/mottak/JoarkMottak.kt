package no.nav.dagpenger.joark.mottak

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.routing
import no.nav.dagpenger.joark.mottak.KafkaConfig.aivenProducer
import no.nav.dagpenger.joark.mottak.KafkaConfig.joarkAivenConsumer
import no.nav.dagpenger.streams.healthRoutes

fun main() {
    val config = Configuration()
    val aivenJournalfoeringReplicator = JournalfoeringReplicator(
        joarkAivenConsumer(
            config.kafka.journalføringTopic,
            System.getenv(),
        ),
        aivenProducer(System.getenv()),
    ).also { it.start() }

    val server = embeddedServer(Netty, config.application.httpPort) {
        install(DefaultHeaders)
        routing {
            healthRoutes(listOf(aivenJournalfoeringReplicator))
        }
    }.start(wait = true)

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(gracePeriodMillis = 3000, timeoutMillis = 5000)
        },
    )
}
