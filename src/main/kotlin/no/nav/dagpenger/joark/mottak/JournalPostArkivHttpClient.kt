package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import java.util.UUID

class JournalPostArkivHttpClient(private val joarkBaseUrl: String, val stsOidcClient: StsOidcClient) : JournalpostArkiv {

    override fun hentInngåendeJournalpost(journalpostId: String): JournalPost? {
        val token = UUID.randomUUID().toString() //todo...
        val url = "${joarkBaseUrl}rest/journalfoerinngaaende/v1/journalposter/$journalpostId"
        val (_, response, result) = with(url.httpGet()) {
            header("Authorization" to token.toBearerToken())
            responseObject<JournalPost>()
        }
        return when (result) {
            is Result.Failure -> throw JournalPostArkivException(response.statusCode, response.responseMessage, result.getException())
            is Result.Success -> result.get()
        }
    }
}

fun String.toBearerToken() = "Bearer $this"

class JournalPostArkivException(val statusCode: Int, override val message: String, override val cause: Throwable) : RuntimeException(message, cause)
