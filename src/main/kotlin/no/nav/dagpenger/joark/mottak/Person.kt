package no.nav.dagpenger.joark.mottak

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class GraphQlPersonResponse(val data: Data, val errors: List<String>?) {
    data class Data(val person: Person)
}

@JsonDeserialize(using = PersonDeserializer::class)
data class Person(
    val navn: String,
    val aktoerId: String,
    val naturligIdent: String,
    val norskTilknytning: Boolean,
    val diskresjonskode: String?
)
