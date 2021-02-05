package no.nav.dagpenger.joark.mottak

import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.readText
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthCheck
import no.nav.dagpenger.streams.HealthStatus

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

class PersonOppslag(
    private val personOppslagBaseUrl: String,
    private val oidcClient: OidcClient,
    private val httpClientProvider: () -> HttpClient = ::httpClientProvider
) : HealthCheck {
    override fun status(): HealthStatus {
        return httpClientProvider().use {
            it.healthStatus("${personOppslagBaseUrl}internal/health/readiness")
        }
    }

    fun hentPerson(id: String): Person {
        return runBlocking {
            kotlin.runCatching {
                val token = oidcClient.oidcToken().access_token
                httpClientProvider().use {
                    it.post<Person>("${personOppslagBaseUrl}graphql") {
                        header("Authorization", "Bearer $token")
                        header("Content-Type", "application/json")
                        header("TEMA", "DAG")
                        header("Nav-Consumer-Token", "Bearer $token")
                        body = PersonQuery(id).also {
                            sikkerLogg.info { "Query: $it" }
                        }
                    }
                }
            }.fold(
                onSuccess = { it },
                onFailure = {
                    logger.error(it.message)
                    when (it) {
                        is ResponseException -> {
                            throw PersonOppslagException(it.response?.status?.value, it.response?.readText(), it)
                        }
                        else -> {
                            throw PersonOppslagException(cause = it)
                        }
                    }
                }
            )
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
    val statusCode: Int? = 500,
    override val message: String? = "",
    override val cause: Throwable? = null
) : RuntimeException(message, cause)
