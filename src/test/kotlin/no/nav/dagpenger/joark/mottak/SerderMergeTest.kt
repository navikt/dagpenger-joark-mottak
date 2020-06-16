package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class SerderMergeTest {
    @Test
    fun `does not preserve null values`() {
        merge(mapOf("nullable" to null), """{"prot1": null }""") shouldBe """{}"""
    }

    @Test
    fun `handles empty json`() {
        merge(mapOf("integer" to 1), """{}""") shouldBe """{"integer":1}"""
    }

    @Test
    fun `handles empty map`() {
        merge(emptyMap(), """{}""") shouldBe """{}"""
    }

    @Test
    fun `overwrites on identical keys`() {
        merge(mapOf("key" to "newvalue"), """{"key":  "value"}""") shouldBe """{"key":"newvalue"}"""
    }

    @Test
    fun `handles list of objects`() {
        merge(mapOf("list" to listOf(mapOf("obj1" to 1), mapOf("obj2" to 2))), """{"key":  "value"}""") shouldBe """{"key":"value","list":[{"obj1":1},{"obj2":2}]}"""
    }

    @Test
    fun `handles nested maps `() {
        merge(mapOf("maps" to mapOf("obj1" to mapOf("obj2" to 2))), """{"key":  "value"}""") shouldBe """{"key":"value","maps":{"obj1":{"obj2":2}}}"""
    }
}
