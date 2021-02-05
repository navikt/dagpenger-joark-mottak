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
import mu.KotlinLogging
import no.nav.dagpenger.streams.HealthStatus
import java.time.Duration

private val logger = KotlinLogging.logger { }

internal fun httpClient(): HttpClient = httpClient()

@KtorExperimentalAPI
internal fun httpClient(
    credentials: Pair<String, String>? = null,
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

internal fun healthStatus(urlString: String): HealthStatus {
    return runBlocking {
        kotlin.runCatching {
            httpClient().use {
                it.get<String>(urlString)
            }
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
