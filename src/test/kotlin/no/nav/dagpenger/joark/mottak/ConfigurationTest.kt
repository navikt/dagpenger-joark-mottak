package no.nav.dagpenger.joark.mottak

import io.kotlintest.shouldBe
import org.junit.jupiter.api.Test

internal class ConfigurationTest {
    @Test
    fun `Configuration is loaded based on application profile`() {
        withProps(dummyConfigs + mapOf("NAIS_CLUSTER_NAME" to "dev-fss")) {
            with(Configuration()) {
                this.application.profile shouldBe Profile.DEV
                this.kafka.joarkTopic.name shouldBe "aapen-dok-journalfoering-v1-q1"
            }
        }

        withProps(dummyConfigs + mapOf("NAIS_CLUSTER_NAME" to "prod-fss")) {
            with(Configuration()) {
                this.application.profile shouldBe Profile.PROD
                this.kafka.joarkTopic.name shouldBe "aapen-dok-journalfoering-v1-p"
            }
        }
    }

    @Test
    fun `Default configuration is LOCAL `() {
        with(Configuration()) {
            this.application.profile shouldBe Profile.LOCAL
            this.kafka.joarkTopic.name shouldBe "aapen-dok-journalfoering-v1"
        }
    }

    fun withProps(props: Map<String, String>, test: () -> Unit) {
        for ((k, v) in props) {
            System.getProperties()[k] = v
        }
        test()
        for ((k, _) in props) {
            System.getProperties().remove(k)
        }
    }

    private val mockedConfigs = listOf(
        "kafka.schema.registry.url",
        "oidc.sts.issuerurl"
    )

    val dummyConfigs = mockedConfigs.associate { it to "test" }
}