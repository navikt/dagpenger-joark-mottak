package no.nav.dagpenger.joark.mottak

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat

internal fun Application.helse(journalfoeringReplicator: JournalfoeringReplicator) {
    routing {
        route("/metrics") {
            get {
                val names =
                    call.request.queryParameters
                        .getAll("name")
                        ?.toSet() ?: kotlin.collections.emptySet()
                call.respondTextWriter(
                    ContentType.parse(TextFormat.CONTENT_TYPE_004),
                    HttpStatusCode.OK,
                ) {
                    TextFormat.write004(this, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names))
                }
            }
        }

        route("/isAlive") {
            get {
                if (journalfoeringReplicator.isAlive()) {
                    call.respondText("ALIVE", ContentType.Text.Plain)
                } else {
                    log.warn("Health check failed")
                    call.respondText("ERROR", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
                }
            }
        }
        route("/isReady") {
            get {
                call.respondText(text = "READY", contentType = ContentType.Text.Plain)
            }
        }
    }
}
