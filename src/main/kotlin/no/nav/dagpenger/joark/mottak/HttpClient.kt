package no.nav.dagpenger.joark.mottak

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.streams.HealthStatus
import java.time.Duration

@KtorExperimentalAPI
internal fun httpClient(
    credentials: Pair<String, String>? = null,
    engine: HttpClientEngine = CIO.create { requestTimeout = Long.MAX_VALUE },
): HttpClient {
    return HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = Duration.ofSeconds(30).toMillis()
            requestTimeoutMillis = Duration.ofSeconds(30).toMillis()
            socketTimeoutMillis = Duration.ofSeconds(30).toMillis()
        }

        install(Logging) {
            level = LogLevel.INFO
        }

        install(JsonFeature) {
            serializer = JacksonSerializer(jacksonJsonAdapter) {
            }
        }

        credentials?.let {
            install(Auth) {
                basic {
                    this.sendWithoutRequest = true
                    this.username = credentials.first
                    this.password = credentials.second
                }
            }
        }
    }
}

fun HttpClient.healthStatus(urlString: String): HealthStatus {
    return runBlocking {
        kotlin.runCatching {
            this@healthStatus.get<String>(urlString)
        }.fold(
            onSuccess = { HealthStatus.UP },
            onFailure = { HealthStatus.DOWN }
        )
    }
}
