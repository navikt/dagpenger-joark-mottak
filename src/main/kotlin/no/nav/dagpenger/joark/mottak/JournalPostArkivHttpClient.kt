package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.response
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Reader
import java.util.UUID

class JournalPostArkivHttpClient(val joarkBaseUrl: String) : JournalpostArkiv {

    override fun hentInngåendeJournalpost(journalpostId: String): JournalPost? {
        val token = UUID.randomUUID().toString() //todo...
        val url = "$joarkBaseUrl/rest/journalfoerinngaaende/v1/journalposter/$journalpostId"

        val (_, response, result) = url.httpGet()
                .header("Authorization" to token.toBearerToken())
                .response(journalPostDeserializer())
        return when (result) {
            is Result.Failure -> {
                throw JournalPostArkivException(response.statusCode, response.responseMessage, result.getException())
            }
            is Result.Success -> {
                result.get()
            }
        }
    }

    private fun journalPostDeserializer() = object : ResponseDeserializable<JournalPost> {
        override fun deserialize(reader: Reader): JournalPost? {
            return JournalPostParser.parse(reader.readText())
        }

        override fun deserialize(content: String): JournalPost? {
            return JournalPostParser.parse(content)
        }

        override fun deserialize(bytes: ByteArray): JournalPost? {
            return JournalPostParser.parse(ByteArrayInputStream(bytes))
        }

        override fun deserialize(inputStream: InputStream): JournalPost? {
            return JournalPostParser.parse(inputStream)
        }
    }
}

fun String.toBearerToken() = "Bearer $this"

class JournalPostArkivException(val statusCode: Int, override val message: String, override val cause: Throwable) : RuntimeException(message, cause)
