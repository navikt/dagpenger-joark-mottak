package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.moshi.responseObject
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient

class PersonOppslag(private val personOppslagUrl: String, private val oidcClient: OidcClient) {
    fun hentPerson(id: String, brukerType: BrukerType): Person {
        val (_, response, result) = with(personOppslagUrl.httpPost()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            header("Content-Type" to "application/json")
            body(
                adapter.toJson(
                    PersonQuery(id, mapBrukerTypeTilIdType[brukerType] ?: throw PersonOppslagException(message = "Failed to map $brukerType"))
                )
            )
            responseObject<GraphQlPersonResponse>()
        }

        return when (result) {
            is Result.Failure ->
                throw PersonOppslagException(
                response.statusCode,
                "Failed to fetch person. Response message ${response.responseMessage}. Payload from server ${response.body().asString("application/json")}"
            )
            is Result.Success -> result.get().data.person
        }
    }
}

val mapBrukerTypeTilIdType = mapOf(
    BrukerType.AKTOERID to IdType.AKTOER_ID,
    BrukerType.FNR to IdType.NATURLIG_IDENT
)

enum class IdType {
    AKTOER_ID,
    NATURLIG_IDENT
}

internal data class PersonQuery(val id: String, val idType: IdType) : GraphqlQuery(
    query = """ 
            query {
                person(id: "$id", idType: ${idType.name}) {
                    aktoerId
                    naturligIdent
                    behandlendeEnheter {
                        enhetId
                        enhetNavn
                    }
                }
            }
            """.trimIndent(),
    variables = null
)

fun main() {
    val p = PersonQuery("123", IdType.AKTOER_ID)
    println(adapter.toJson(p))
}

class PersonOppslagException(val statusCode: Int = 500, override val message: String, override val cause: Throwable? = null) :
    RuntimeException(message, cause)
