package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient

class JournalpostArkivJoark(private val joarkBaseUrl: String, private val oidcClient: OidcClient) :
    JournalpostArkiv {

    val joarkUrl =
        if (joarkBaseUrl.endsWith("rest/journalfoerinngaaende/v1")) "$joarkBaseUrl/journalposter" else "${joarkBaseUrl}rest/journalfoerinngaaende/v1/journalposter"

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        val url = "$joarkUrl/$journalpostId"
        val (_, response, result) = with(url.httpGet()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            responseObject<Journalpost>()
        }
        return when (result) {
            is Result.Failure -> throw JournalpostArkivException(
                response.statusCode,
                "Failed to fetch journalpost id: $journalpostId. Response message ${response.responseMessage}",
                result.getException()
            )
            is Result.Success -> result.get()
        }
    }
}

class JournalpostArkivException(val statusCode: Int, override val message: String, override val cause: Throwable) :
    RuntimeException(message, cause)
