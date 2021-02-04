package no.nav.dagpenger.joark.mottak

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.response
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import no.nav.dagpenger.events.moshiInstance

internal fun <T : Any> moshiDeserializerOf(clazz: Class<T>) = object : ResponseDeserializable<T> {
    override fun deserialize(content: String): T? = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(clazz)
        .fromJson(content)
}

internal inline fun <reified T : Any> Request.responseObject() = response(moshiDeserializerOf(T::class.java))

internal val jsonMapAdapter = moshiInstance.adapter<Map<String, Any?>>(
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
).serializeNulls()

internal fun merge(map: Map<String, Any?>, json: String): String {
    val mutableMap = jsonMapAdapter.fromJson(json)?.toMutableMap()?.also {
        it.putAll(map)
    } ?: throw JsonDataException("Unable to merge $map with $json")
    return jsonMapAdapter.toJson(mutableMap) ?: throw JsonDataException("Unable to deserialize $mutableMap")
}

internal val jacksonJsonAdapter = jacksonObjectMapper()

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

object PersonDeserializer : JsonDeserializer<Person>() {
    internal fun JsonNode.aktoerId() = this.ident("AKTORID")
    internal fun JsonNode.naturligIdent() = this.ident("FOLKEREGISTERIDENT")
    internal fun JsonNode.norskTilknyting(): Boolean = findValue("gtLand").isNull
    internal fun JsonNode.diskresjonsKode(): String? {
        return findValue("adressebeskyttelse").firstOrNull()?.path("gradering")?.asText(null)
    }

    private fun JsonNode.ident(type: String): String {
        return findValue("identer").first { it.path("gruppe").asText() == type }.get("ident").asText()
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Person {
        val node: JsonNode = p.readValueAsTree()
        return Person(
            navn = node.personNavn(),
            aktoerId = node.aktoerId(),
            naturligIdent = node.naturligIdent(),
            norskTilknytning = node.norskTilknyting(),
            diskresjonskode = node.diskresjonsKode()
        )
    }
}
