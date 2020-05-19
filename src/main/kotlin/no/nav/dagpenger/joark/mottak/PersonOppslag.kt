package no.nav.dagpenger.joark.mottak

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthCheck
import no.nav.dagpenger.streams.HealthStatus

class PersonOppslag(
    private val personOppslagBaseUrl: String,
    private val oidcClient: OidcClient,
    private val apiKey: String
) : HealthCheck {
    private val httpClient = HttpClient {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                registerModule(JavaTimeModule())
            }
        }
    }

    override fun status(): HealthStatus {
        return runBlocking {
            val response = httpClient.get<HttpResponse>("${personOppslagBaseUrl}isAlive")

            if (response.status.isSuccess()) HealthStatus.UP else HealthStatus.DOWN
        }
    }

    fun hentPerson(id: String, brukerType: BrukerType): Person {
        return runBlocking {
            val response: GraphQlPersonResponse = httpClient.post("${personOppslagBaseUrl}graphql") {
                header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
                header("X-API-KEY", apiKey)
                contentType(ContentType.Application.Json)
                body = PersonQuery(
                    id,
                    mapBrukerTypeTilIdType[brukerType]
                        ?: throw PersonOppslagException(message = "Failed to map $brukerType")
                )
            }

            return@runBlocking response.data?.person ?: throw PersonOppslagException(message = response.errors?.joinToString { it.message }
                ?: "Ukjent feil")
        }
    }
}

val mapBrukerTypeTilIdType = mapOf(
    BrukerType.AKTOERID to IdType.AKTOER_ID,
    BrukerType.FNR to IdType.NATURLIG_IDENT
)

enum class IdType {
    AKTOER_ID,
    NATURLIG_IDENT
}

internal data class PersonQuery(val id: String, val idType: IdType) : GraphqlQuery(
    query = """ 
            query {
                person(id: "$id", idType: ${idType.name}) {
                    navn
                    aktoerId
                    naturligIdent
                    diskresjonskode
                }
            }
            """.trimIndent(),
    variables = null
)

class PersonOppslagException(
    val statusCode: Int = 500,
    override val message: String,
    override val cause: Throwable? = null
) :
    RuntimeException(message, cause)
