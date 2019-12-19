package no.nav.dagpenger.joark.mottak

data class GraphQlPersonResponse(val data: Data, val errors: List<String>?) {
    data class Data(val person: Person)
}

data class Person(
    val navn: String,
    val aktoerId: String,
    val naturligIdent: String,
    val diskresjonskode: String?
)
