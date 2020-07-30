package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthStatus

class JournalpostArkivJoark(
    private val joarkBaseUrl: String,
    private val oidcClient: OidcClient,
    private val profile: Profile
) :
    JournalpostArkiv {

    override fun status(): HealthStatus {
        val (_, _, result) = with("${joarkBaseUrl}isAlive".httpGet()) {
            responseString()
        }
        return when (result) {
            is Result.Failure -> HealthStatus.DOWN
            else -> HealthStatus.UP
        }
    }

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        val (_, response, result) = with("${joarkBaseUrl}graphql".httpPost()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            header("Content-Type" to "application/json")
            body(adapter.toJson(JournalPostQuery(journalpostId)))
            responseObject<GraphQlJournalpostResponse>()
        }

        return when (result) {
            is Result.Failure -> throw JournalpostArkivException(
                response.statusCode,
                "Failed to fetch journalpost id: $journalpostId. Response message ${response.responseMessage}",
                result.getException()
            )
            is Result.Success -> result.get().data.journalpost
        }
    }

    override fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata {
        val journalpostId = journalpost.journalpostId
        val dokumentId = journalpost.dokumenter.firstOrNull()?.dokumentInfoId ?: return emptySøknadsdata

        val url = "${joarkBaseUrl}rest/hentdokument/$journalpostId/$dokumentId/ORIGINAL"
        val (_, response, result) = with(url.httpGet()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            header("Content-Type" to "application/json")
            responseString()
        }

        return result.fold(
            {
                Søknadsdata(
                    it, journalpost.journalpostId, journalpost.registrertDato()
                )
            },
            { error ->
                if (error.response.statusCode == 404 && profile == Profile.DEV) {
                    return emptySøknadsdata
                } else {
                    throw JournalpostArkivException(
                        response.statusCode,
                        "Failed to fetch søknadsdata for id: $journalpostId. Response message ${response.responseMessage}",
                        error.exception
                    )
                }
            }
        )
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

class JournalpostArkivException(val statusCode: Int, override val message: String, override val cause: Throwable) :
    RuntimeException(message, cause)
