package no.nav.dagpenger.joark.mottak

data class Environment(
    val username: String = getEnvVar("SRVDAGPENGER_JOARK_MOTTAK_USERNAME"),
    val password: String = getEnvVar("SRVDAGPENGER_JOARK_MOTTAK_PASSWORD"),
    val oicdStsUrl: String = getEnvVar("OIDC_STS_ISSUERURL"),
    val journalfoerinngaaendeV1Url: String = getEnvVar("JOURNALFOERINNGAAENDE_V1_URL"),
    val bootstrapServersUrl: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
    val schemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_URL", "localhost:8081"),
    val fasitEnvironmentName: String = getEnvVar("FASIT_ENVIRONMENT_NAME", "")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
