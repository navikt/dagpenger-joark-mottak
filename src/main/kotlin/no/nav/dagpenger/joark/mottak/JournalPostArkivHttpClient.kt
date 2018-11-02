package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient

class JournalPostArkivHttpClient(private val joarkBaseUrl: String, private val oidcClient: OidcClient) : JournalpostArkiv {

    val joarkUrl = if (joarkBaseUrl.endsWith("rest/journalfoerinngaaende/v1")) "$joarkBaseUrl/journalposter" else "${joarkBaseUrl}rest/journalfoerinngaaende/v1/journalposter"

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        val url = "$joarkUrl/$journalpostId"
        val (_, response, result) = with(url.httpGet()) {
            header("Authorization" to oidcClient.oidcToken().access_token.toBearerToken())
            responseObject<Journalpost>()
        }
        return when (result) {
            is Result.Failure -> throw JournalPostArkivException(response.statusCode, response.responseMessage, result.getException())
            is Result.Success -> result.get()
        }
    }
}

fun String.toBearerToken() = "Bearer $this"

class JournalPostArkivException(val statusCode: Int, override val message: String, override val cause: Throwable) : RuntimeException(message, cause)
