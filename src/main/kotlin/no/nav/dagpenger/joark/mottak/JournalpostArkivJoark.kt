package no.nav.dagpenger.joark.mottak

import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.readText
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthStatus

private val logger = KotlinLogging.logger { }

class JournalpostArkivJoark(
    private val joarkBaseUrl: String,
    private val oidcClient: OidcClient,
    private val httpClientProvider: () -> HttpClient = ::httpClientProvider
) : JournalpostArkiv {

    override fun status(): HealthStatus {
        return httpClientProvider().use {
            it.healthStatus("${joarkBaseUrl}isAlive")
        }
    }

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        return runBlocking {

            kotlin.runCatching {
                val token = oidcClient.oidcToken().access_token
                httpClientProvider().use {
                    it.post<GraphQlJournalpostResponse>("${joarkBaseUrl}graphql") {
                        header("Authorization", "Bearer $token")
                        header("Content-Type", "application/json")
                        body = JournalPostQuery(journalpostId)
                    }
                }
            }.fold(
                onSuccess = { it.data.journalpost },
                onFailure = {
                    when (it) {
                        is ResponseException -> {
                            throw JournalpostArkivException(
                                it.response?.status?.value,
                                "Feil ved henting av journalpost med id: $journalpostId. Response message ${it.response?.readText()}",
                                it
                            )
                        }
                        else -> {
                            throw JournalpostArkivException(
                                message = "Feil ved henting av journalpost med id: $journalpostId.",
                                cause = it
                            )
                        }
                    }
                }
            )
        }
    }

    private fun _hentSøknadsdata(journalpost: Journalpost): Søknadsdata? {
        val journalpostId = journalpost.journalpostId
        val dokumentId = journalpost.dokumenter.firstOrNull()?.dokumentInfoId ?: return emptySøknadsdata
        return runBlocking {
            val token = oidcClient.oidcToken().access_token
            kotlin.runCatching {
                httpClientProvider().use {
                    it.get<String>("${joarkBaseUrl}rest/hentdokument/$journalpostId/$dokumentId/ORIGINAL") {
                        header("Authorization", "Bearer $token")
                    }
                }
            }.fold(
                onSuccess = { Søknadsdata(it, journalpostId, journalpost.registrertDato()) },
                onFailure = {
                    when (it) {
                        is ResponseException -> {
                            throw JournalpostArkivException(
                                it.response?.status?.value,
                                "Feil ved henting av søknadsdata for journalpost med id: $journalpostId. Response message ${it.response?.readText()}",
                                it
                            )
                        }
                        else -> {
                            throw JournalpostArkivException(
                                message = "Feil ved henting av søknadsdata for journalpost med id: $journalpostId.",
                                cause = it
                            )
                        }
                    }
                }
            )
        }
    }

    override fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata? {
        return if (
            (journalpost.henvendelsestype in listOf(Henvendelsestype.NY_SØKNAD, Henvendelsestype.GJENOPPTAK) && journalpost.kanal == "NAV_NO")
        ) {
            _hentSøknadsdata(journalpost)
        } else {
            null
        }
    }
}

internal data class JournalPostQuery(val journalpostId: String) : GraphqlQuery(
    query =
        """ 
            query {
                journalpost(journalpostId: "$journalpostId") {
                    journalstatus
                    journalpostId
                    journalfoerendeEnhet
                    datoOpprettet
                    behandlingstema
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

class JournalpostArkivException(
    val statusCode: Int? = 500,
    override val message: String? = "",
    override val cause: Throwable
) :
    RuntimeException(message, cause)
