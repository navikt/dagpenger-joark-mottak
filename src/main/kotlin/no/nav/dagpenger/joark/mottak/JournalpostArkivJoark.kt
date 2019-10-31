package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.moshi.responseObject
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient

class JournalpostArkivJoark(private val joarkUrl: String, private val oidcClient: OidcClient) :
    JournalpostArkiv {

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        val (_, response, result) = with(joarkUrl.httpPost()) {
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
                    dokumenter {
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
