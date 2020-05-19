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
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthStatus

class JournalpostArkivJoark(
    private val joarkBaseUrl: String,
    private val oidcClient: OidcClient,
    private val profile: Profile
) :
    JournalpostArkiv {
    private val httpClient = HttpClient {
        install(JsonFeature) {
            serializer = JacksonSerializer() {
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                registerModule(JavaTimeModule())
            }
        }
    }

    override fun status(): HealthStatus {
        return runBlocking {
            val response = httpClient.get<HttpResponse>("${joarkBaseUrl}isAlive")

            if (response.status.isSuccess()) HealthStatus.UP else HealthStatus.DOWN
        }
    }

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        return runBlocking {
            val response: GraphQlJournalpostResponse = httpClient.post("${joarkBaseUrl}graphql") {
                header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
                contentType(ContentType.Application.Json)
                body = JournalPostQuery(journalpostId)
            }

            return@runBlocking response.data?.journalpost ?: throw JournalpostArkivException(
                null,
                response.errors?.joinToString { it.message } ?: "Ukjent feil")
        }
    }

    override fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata {
        val journalpostId = journalpost.journalpostId
        val dokumentId = journalpost.dokumenter.firstOrNull()?.dokumentInfoId ?: return emptySøknadsdata
        val url = "${joarkBaseUrl}rest/hentdokument/$journalpostId/$dokumentId/ORIGINAL"

        return runBlocking {
            val response: HttpResponse =
                httpClient.get(url) {
                    header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
                }

            return@runBlocking when {
                response.status.isSuccess() -> Søknadsdata(response.readText())
                response.status == NotFound && profile == Profile.DEV -> emptySøknadsdata
                else -> throw JournalpostArkivException(
                    response.status.value,
                    "Failed to fetch søknadsdata for id: $journalpostId. Response message ${response.readText()}"
                )
            }
        }
    }
}

internal data class JournalPostQuery(val journalpostId: String) : GraphqlQuery(
    query = """ 
            query {
                journalpost(journalpostId: "$journalpostId") {
                    journalstatus
                    journalpostId
                    journalfoerendeEnhet
                    datoOpprettet
                    bruker {
                      type
                      id
                    }
                    kanal
                    kanalnavn
                    relevanteDatoer {
                      dato
                      datotype
                    }
                    dokumenter {
                      tittel
                      dokumentInfoId
                      brevkode
                    }
                }
            }
            """.trimIndent(),
    variables = null
)

class JournalpostArkivException(val statusCode: Int?, override val message: String) :
    RuntimeException(message)
