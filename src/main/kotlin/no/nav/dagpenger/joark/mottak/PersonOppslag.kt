package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.response
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthCheck
import no.nav.dagpenger.streams.HealthStatus

class PersonOppslag(
    private val personOppslagBaseUrl: String,
    private val oidcClient: OidcClient,
) : HealthCheck {
    override fun status(): HealthStatus {
        val (_, _, result) = with("${personOppslagBaseUrl}isAlive".httpGet()) {
            responseString()
        }
        return when (result) {
            is Result.Failure -> HealthStatus.DOWN
            else -> HealthStatus.UP
        }
    }

    fun hentPerson(id: String): Person {
        val token = oidcClient.oidcToken().access_token
        val (_, response, result) = with("${personOppslagBaseUrl}graphql".httpPost()) {
            authentication().bearer(token)
            header("Content-Type" to "application/json")
            header("accept" to "application/json")
            header("TEMA" to "DAG")
            header("Nav-Consumer-Token" to "Bearer $token")
            body(
                adapter.toJson(PersonQuery(id))
            )
            response(PersonDeserializer)
        }
        return when (result) {
            is Result.Failure ->
                throw PersonOppslagException(
                    response.statusCode,
                    "Failed to fetch person. Response message ${response.responseMessage}. Payload from server ${
                    response.body().asString("application/json")
                    }",
                    result.error
                )
            is Result.Success -> result.get()
        }
    }
}

internal data class PersonQuery(val id: String) : GraphqlQuery(
    query =
        """ 
            query {
  hentPerson(ident: $id) {
      navn {
        fornavn,
        mellomnavn,
        etternavn
      },
    adressebeskyttelse{
     gradering 
    }
    }
    hentGeografiskTilknytning(ident: $id){
    gtLand
  }
      hentIdenter(ident: $id, grupper: [AKTORID,FOLKEREGISTERIDENT]) {
    identer {
      ident,
      gruppe
    }
    }                }
            
        """.trimIndent(),
    variables = null
)

class PersonOppslagException(
    val statusCode: Int = 500,
    override val message: String,
    override val cause: Throwable? = null
) :
    RuntimeException(message, cause)
