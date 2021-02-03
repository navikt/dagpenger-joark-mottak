package no.nav.dagpenger.joark.mottak

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize(using = PersonDeserializer::class)
data class Person(
    val navn: String,
    val aktoerId: String,
    val naturligIdent: String,
    val norskTilknytning: Boolean,
    val diskresjonskode: String?
)
