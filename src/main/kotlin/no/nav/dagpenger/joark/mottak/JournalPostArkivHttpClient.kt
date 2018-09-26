package no.nav.dagpenger.joark.mottak

import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.RuntimeException
import java.util.UUID

class JournalPostArkivHttpClient(val joarkBaseUrl: String) : JournalpostArkiv {

    companion object {
        val okHttpClient: OkHttpClient = OkHttpClient()
    }

    override fun hentInngåendeJournalpost(journalpostId: String): JournalPost? {
        val token = UUID.randomUUID().toString() //todo...
        val request: Request = Request.Builder()
                .url("$joarkBaseUrl/rest/journalfoerinngaaende/v1/journalposter/$journalpostId")
                .header("Authorization", BearerToken(token).value())
                .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                JournalPostParser.parse(response.body()!!.byteStream())
            } else {
                throw JournalPostArkivException(response.code(), response.message())
            }
        }
    }
}

data class BearerToken(val token: String) {
    fun value(): String = "Bearer $token"
}

class JournalPostArkivException(val statusCode: Int, override val message: String) : RuntimeException(message)
