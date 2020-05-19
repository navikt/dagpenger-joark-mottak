package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthCheck
import no.nav.dagpenger.streams.HealthStatus

class PersonOppslag(private val personOppslagBaseUrl: String, private val oidcClient: OidcClient, private val apiKey: String) : HealthCheck {
    override fun status(): HealthStatus {
        val (_, _, result) = with("${personOppslagBaseUrl}isAlive".httpGet()) {
            responseString()
        }
        return when (result) {
            is Result.Failure -> HealthStatus.DOWN
            else -> HealthStatus.UP
        }
    }

    fun hentPerson(id: String, brukerType: BrukerType): Person {
        val (_, response, result) = with("${personOppslagBaseUrl}graphql".httpPost()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            header("Content-Type" to "application/json")
            header("X-API-KEY" to apiKey)
            body(
                adapter.toJson(
                    PersonQuery(id, mapBrukerTypeTilIdType[brukerType]
                        ?: throw PersonOppslagException(message = "Failed to map $brukerType"))
                )
            )
            responseObject<GraphQlPersonResponse>()
        }

        return when (result) {
            is Result.Failure ->
                throw PersonOppslagException(
                    response.statusCode,
                    "Failed to fetch person. Response message ${response.responseMessage}. Payload from server ${response.body().asString("application/json")}",
                    result.error
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
                    navn
                    aktoerId
                    naturligIdent
                    diskresjonskode
                }
            }
            """.trimIndent(),
    variables = null
)

class PersonOppslagException(val statusCode: Int = 500, override val message: String, override val cause: Throwable? = null) :
    RuntimeException(message, cause)
