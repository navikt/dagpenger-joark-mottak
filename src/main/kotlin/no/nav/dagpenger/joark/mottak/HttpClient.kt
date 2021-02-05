package no.nav.dagpenger.joark.mottak

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.streams.HealthStatus
import java.time.Duration

internal fun httpClientProvider(): HttpClient = httpClient()

internal fun httpClient(
    engine: HttpClientEngine = CIO.create { },
): HttpClient {
    return HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = Duration.ofSeconds(1).toMillis()
            requestTimeoutMillis = Duration.ofSeconds(1).toMillis()
            socketTimeoutMillis = Duration.ofSeconds(1).toMillis()
        }

        install(Logging) {
            level = LogLevel.INFO
        }

        install(JsonFeature) {
            serializer = JacksonSerializer(jacksonJsonAdapter) {
            }
        }
    }
}

fun HttpClient.healthStatus(urlString: String): HealthStatus {
    return runBlocking {
        kotlin.runCatching {
            this@healthStatus.get<String>(urlString)
        }.fold(
            onSuccess = {
                HealthStatus.UP
            },
            onFailure = {
                HealthStatus.DOWN
            }
        )
    }
}
