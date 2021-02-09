package no.nav.dagpenger.joark.mottak

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging

private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal fun merge(map: Map<String, Any?>, json: String): String {
    val mutableMap =
        jacksonJsonAdapter.readValue(json, object : TypeReference<Map<String, Any?>>() {}).toMutableMap().also {
            it.putAll(map)
        }
    return jacksonJsonAdapter.writeValueAsString(mutableMap)
}

internal val jacksonJsonAdapter = jacksonObjectMapper().also {
    it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

object PersonDeserializer : JsonDeserializer<Person>() {
    internal fun JsonNode.aktoerId() = this.ident("AKTORID")
    internal fun JsonNode.naturligIdent() = this.ident("FOLKEREGISTERIDENT")
    internal fun JsonNode.norskTilknyting(): Boolean = findValue("gtLand").isNull
    internal fun JsonNode.diskresjonsKode(): String? {
        return findValue("adressebeskyttelse").firstOrNull()?.path("gradering")?.asText(null)
    }

    internal fun JsonNode.personNavn(): String {
        return findValue("navn").first().let { node ->
            val fornavn = node.path("fornavn").asText()
            val mellomnavn = node.path("mellomnavn").asText("")
            val etternavn = node.path("etternavn").asText()

            when (mellomnavn.isEmpty()) {
                true -> "$fornavn $etternavn"
                else -> "$fornavn $mellomnavn $etternavn"
            }
        }
    }

    private fun JsonNode.ident(type: String): String {
        return findValue("identer").first { it.path("gruppe").asText() == type }.get("ident").asText()
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Person {
        val node: JsonNode = p.readValueAsTree()
        return kotlin.runCatching {
            Person(
                navn = node.personNavn(),
                aktoerId = node.aktoerId(),
                naturligIdent = node.naturligIdent(),
                norskTilknytning = node.norskTilknyting(),
                diskresjonskode = node.diskresjonsKode()
            )
        }.fold(
            onSuccess = {
                it
            },
            onFailure = {
                sikkerLogg.info(node.toString())
                throw it
            }
        )
    }
}
