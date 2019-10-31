package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.moshiInstance

internal val adapter = moshiInstance.adapter(GraphqlQuery::class.java).serializeNulls()

internal open class GraphqlQuery(val query: String, val variables: Any?)