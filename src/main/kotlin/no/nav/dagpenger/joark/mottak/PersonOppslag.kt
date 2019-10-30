package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.moshi.responseObject
import com.github.kittinunf.result.Result

class PersonOppslag(private val personOppslagUrl: String) {
    fun hentPerson(id: String, brukerType: BrukerType): Person {
        val (_, response, result) = with(personOppslagUrl.httpPost()) {
            header("Content-Type" to "application/json")
            body(
                adapter.toJson(
                    PersonQuery(id, brukerType.toString())
                )
            )
            responseObject<GraphQlPersonResponse>()
        }

        return when (result) {
            is Result.Failure -> throw PersonOppslagException(
                response.statusCode,
                "Failed to fetch person. Response message ${response.responseMessage}",
                result.getException()
            )
            is Result.Success -> result.get().data.person
        }
    }
}

internal data class PersonQuery(val id: String, val aktørType: String) : GraphqlQuery(
    query = """ 
            query {
                person(id: "$id", aktoerType: $aktørType) {
                    aktoerId
                    naturligIdent
                    behandlendeEnheter
                }
            }
            """.trimIndent(),
    variables = null
)

data class GraphQlPersonResponse(val data: Data, val errors: List<String>?) {
    data class Data(val person: Person)
}

data class Person(
    val aktoerId: String,
    val naturligIdent: String,
    val behandlendeEnheter: List<BehandlendeEnhet>
)

data class BehandlendeEnhet(
    val enhetId: String,
    val enhetNavn: String
)

class PersonOppslagException(val statusCode: Int, override val message: String, override val cause: Throwable) :
    RuntimeException(message, cause)

