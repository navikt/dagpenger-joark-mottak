package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient

class JournalpostArkivJoark(private val joarkUrl: String, private val oidcClient: OidcClient) :
    JournalpostArkiv {

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        val (_, response, result) = with(joarkUrl.httpPost()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            header(
                ("Content-Type" to "application/json")
            )
            body(
                """ "query": "${journalpostQuery(journalpostId)}" """.trimIndent()
            )
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

    private fun journalpostQuery(journalpostId: String) = """
        query {
          journalpost(journalpostId: \"$journalpostId\") {
            journalstatus
            journalfoerendeEnhet
            bruker {
              type
              id
            }
            kanalnavn
            dokumenter {
              dokumentInfoId
              brevkode
            }
          }
        }
    """.trimIndent()
}

class JournalpostArkivException(val statusCode: Int, override val message: String, override val cause: Throwable) :
    RuntimeException(message, cause)
