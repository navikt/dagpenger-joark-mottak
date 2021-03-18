package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.streams.HealthStatus

class JournalpostArkivJoark(
    private val joarkBaseUrl: String,
    private val oidcClient: OidcClient,
) : JournalpostArkiv {

    override fun status(): HealthStatus {
        val (_, _, result) = with("${joarkBaseUrl}isAlive".httpGet()) {
            responseString()
        }
        return when (result) {
            is Result.Failure -> HealthStatus.DOWN
            else -> HealthStatus.UP
        }
    }

    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        val (_, response, result) = with("${joarkBaseUrl}graphql".httpPost()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            header("Content-Type" to "application/json")
            body(jacksonJsonAdapter.writeValueAsString(JournalPostQuery(journalpostId)))
            responseObject<GraphQlJournalpostResponse>()
        }

        return when (result) {
            is Result.Failure -> throw JournalpostArkivException(
                response.statusCode,
                "Feil ved henting av journalpost med id $journalpostId. Response message ${response.responseMessage}",
                result.getException()
            )
            is Result.Success -> result.get().data.journalpost
        }
    }

    private fun _hentSøknadsdata(journalpost: Journalpost): Søknadsdata {
        val journalpostId = journalpost.journalpostId
        val dokumentId = journalpost.dokumenter.firstOrNull()?.dokumentInfoId ?: return emptySøknadsdata

        val url = "${joarkBaseUrl}rest/hentdokument/$journalpostId/$dokumentId/ORIGINAL"
        val (_, response, result) = with(url.httpGet()) {
            authentication().bearer(oidcClient.oidcToken().access_token)
            header("Content-Type" to "application/json")
            responseString()
        }

        return result.fold(
            {
                Søknadsdata(
                    it,
                    journalpost.journalpostId,
                    journalpost.registrertDato()
                )
            },
            { error ->
                throw JournalpostArkivException(
                    response.statusCode,
                    "Feil ved henting av søknadsdata for journalpost med id: $journalpostId. Melding fra response ${response.responseMessage}",
                    error.exception
                )
            }
        )
    }

    private val henvendelserMedData = listOf(
        Henvendelsestype.NY_SØKNAD,
        Henvendelsestype.GJENOPPTAK,
        Henvendelsestype.ETTERSENDELSE
    )

    override fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata? {
        return if (journalpost.henvendelsestype in henvendelserMedData && journalpost.kanal == "NAV_NO") {
            _hentSøknadsdata(journalpost)
        } else {
            null
        }
    }
}

internal data class JournalPostQuery(val journalpostId: String) : GraphqlQuery(
    query =
        """ 
        query {
            journalpost(journalpostId: "$journalpostId") {
                journalstatus
                journalpostId
                journalfoerendeEnhet
                datoOpprettet
                behandlingstema
                bruker {
                  type
                  id
                }
                kanal
                kanalnavn
                relevanteDatoer {
                  dato
                  datotype
                }
                dokumenter {
                  tittel
                  dokumentInfoId
                  brevkode
                }
            }
        }
        """.trimIndent(),
    variables = null
)

class JournalpostArkivException(val statusCode: Int, override val message: String, override val cause: Throwable) :
    RuntimeException(message, cause)
