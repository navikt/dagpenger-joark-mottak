package no.nav.dagpenger.joark.mottak

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

private val jsonMapAdapter = moshiInstance.adapter<Map<String, Any?>>(
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
).serializeNulls()

internal fun merge(map: Map<String, Any?>, json: String): String {
    val mutableMap = jsonMapAdapter.fromJson(json)?.toMutableMap()?.also {
        it.putAll(map)
    } ?: throw JsonDataException("Unable to merge $map with $json")
    return jsonMapAdapter.toJson(mutableMap) ?: throw JsonDataException("Unable to deserialize $mutableMap")
}
