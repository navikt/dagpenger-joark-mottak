package no.nav.dagpenger.joark.mottak

interface OidcClient {
    fun oidcToken(): OidcToken
}
