package no.nav.dagpenger.joark.mottak

import io.prometheus.client.Counter
import mu.KotlinLogging
import no.finn.unleash.DefaultUnleash
import no.finn.unleash.Unleash
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.HealthCheck
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.consumeGenericTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import java.util.Properties

private val logger = KotlinLogging.logger {}
const val DAGPENGER_NAMESPACE = "dagpenger"
private val labelNames = listOf(
    "skjemaId",
    "brukerType",
    "henvendelsestype",
    "skjemaIdIsKnown",
    "numberOfDocuments",
    "kanal",
    "kanalnavn",
    "journalTilstand"

)
private val jpCounter = Counter
    .build()
    .namespace(DAGPENGER_NAMESPACE)
    .name("journalpost_received")
    .help("Number of Journalposts received on tema DAG")
    .labelNames(*labelNames.toTypedArray())
    .register()

private val jpMottatCounter = Counter
    .build()
    .namespace(DAGPENGER_NAMESPACE)
    .help("Antall journalposter dp joark mottak kan behandle (etter filtrering)")
    .name("journalpost_mottatt")
    .register()

internal object PacketKeys {
    const val DOKUMENTER: String = "dokumenter"
    const val AVSENDER_NAVN: String = "avsenderNavn"
    const val DATO_REGISTRERT: String = "datoRegistrert"
    const val HOVEDSKJEMA_ID: String = "hovedskjemaId"
    const val JOURNALPOST_ID: String = "journalpostId"
    const val AKTØR_ID: String = "aktørId"
    const val BEHANDLENDE_ENHET: String = "behandlendeEnhet"
    const val NATURLIG_IDENT: String = "naturligIdent"
    const val TOGGLE_BEHANDLE_NY_SØKNAD: String = "toggleBehandleNySøknad"
    const val HENVENDELSESTYPE: String = "henvendelsestype"
}

class JoarkMottak(
    val config: Configuration,
    val journalpostArkiv: JournalpostArkiv,
    val packetCreator: PacketCreator
) : Service() {
    override val healthChecks: List<HealthCheck> =
        listOf(journalpostArkiv as HealthCheck, packetCreator.personOppslag as HealthCheck)

    override val SERVICE_APP_ID =
        "dagpenger-joark-mottak-opprydder-v1"

    override val HTTP_PORT: Int = config.application.httpPort

    override fun buildTopology(): Topology {

        val builder = StreamsBuilder()

        logger.info { "Consuming topic ${config.kafka.joarkTopic.name}" }

        val inngåendeJournalposter = builder.consumeGenericTopic(
            config.kafka.joarkTopic, config.kafka.schemaRegisterUrl
        )

        inngåendeJournalposter
            .peek { _, record: GenericRecord ->
                logger.info(
                    "Handling: ${record[PacketKeys.JOURNALPOST_ID]} "
                )
            }
            .filter { _, jp -> journalpostIdsFrom21OfJanuary.contains(jp[PacketKeys.JOURNALPOST_ID]) }
            .filter { _, journalpostHendelse -> "DAG" == journalpostHendelse.get("temaNytt").toString() }
            .peek { _, record ->
                logger.info(
                    "Received journalpost with journalpost id: ${record[PacketKeys.JOURNALPOST_ID]} and tema: ${record["temaNytt"]}, hendelsesType: ${record["hendelsesType"]}, mottakskanal, ${record["mottaksKanal"]} "
                )
            }
            .filter { _, journalpostHendelse -> "MidlertidigJournalført" == journalpostHendelse.get("hendelsesType").toString() }
            .mapValues { _, record ->
                val journalpostId = record.get(PacketKeys.JOURNALPOST_ID).toString()

                journalpostArkiv.hentInngåendeJournalpost(journalpostId)
                    .also { logger.info { "Journalpost --> $it" } }
                    .also { registerMetrics(it) }
            }
            .filter { _, journalpost -> journalpost.journalstatus == Journalstatus.MOTTATT }
            .filter { _, journalpost -> journalpost.henvendelsestype.erStøttet() }
            .filter { _, journalpost -> toggleStøtte(journalpost.henvendelsestype) }
            .mapValues { _, journalpost -> packetCreator.createPacket(journalpost) }
            .peek { _, _ -> jpMottatCounter.inc() }
            .selectKey { _, value -> value.getStringValue(PacketKeys.JOURNALPOST_ID) }
            .peek { _, packet ->
                logger.info {
                    "Producing packet with journalpostid ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)} and henvendelsestype: ${packet.getStringValue(
                        PacketKeys.HENVENDELSESTYPE
                    )}"
                }
            }
            .toTopic(config.kafka.dagpengerJournalpostTopic)

        return builder.build()
    }

    private fun Henvendelsestype.erStøttet() = this in listOf(
        Henvendelsestype.NY_SØKNAD,
        Henvendelsestype.UTDANNING,
        Henvendelsestype.GJENOPPTAK,
        Henvendelsestype.ETABLERING,
        Henvendelsestype.KLAGE_ANKE
    )

    private fun toggleStøtte(henvendelsestype: Henvendelsestype): Boolean {
        return henvendelsestype == Henvendelsestype.NY_SØKNAD || packetCreator.unleash.isEnabled("dp.innlop.behandleNyBrevkode")
    }

    private fun registerMetrics(journalpost: Journalpost) {
        val skjemaId = journalpost.dokumenter.firstOrNull()?.brevkode ?: "ukjent"
        val brukerType = journalpost.bruker?.type?.toString() ?: "ukjent"
        val henvendelsestype = journalpost.henvendelsestype.toString()
        val skjemaIdKjent = HenvendelsesTypeMapper.isKnownSkjemaId(skjemaId).toString()
        val numberOfDocuments = journalpost.dokumenter.size.toString()
        val kanal = journalpost.kanal?.let { it } ?: "ukjent"
        val kanalnavn = journalpost.kanalnavn?.let { it } ?: "ukjent"
        val journalTilstand = journalpost.journalstatus?.name ?: "ukjent"

        jpCounter
            .labels(
                skjemaId,
                brukerType,
                henvendelsestype,
                skjemaIdKjent,
                numberOfDocuments,
                kanal,
                kanalnavn,
                journalTilstand
            )
            .inc()
    }

    override fun getConfig(): Properties {
        val properties = streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = config.kafka.brokers,
            credential = config.kafka.credential()
        )
        properties[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = config.kafka.processingGuarantee
        return properties
    }
}

class UnsupportedBehandlendeEnhetException(override val message: String) : RuntimeException(message)

fun main(args: Array<String>) {
    val config = Configuration()
    val oidcClient = StsOidcClient(
        config.application.oidcStsUrl,
        config.kafka.user, config.kafka.password
    )

    val unleash: Unleash = DefaultUnleash(config.unleashConfig)

    val journalpostArkiv = JournalpostArkivJoark(
        config.application.joarkJournalpostArkivBaseUrl,
        oidcClient
    )

    val personOppslag = PersonOppslag(
        config.application.personOppslagBaseUrl,
        oidcClient,
        config.application.graphQlApiKey
    )

    val packetCreator = PacketCreator(personOppslag, unleash)

    val service = JoarkMottak(config, journalpostArkiv, packetCreator)
    service.start()
}

val journalpostIdsFrom21OfJanuary = setOf<Long>(
    468016607L,
    468016568L,
    468016220L,
    468015943L,
    468015860L,
    468015745L,
    468015610L,
    468014912L,
    468014797L,
    468014383L,
    468014208L,
    468014103L,
    468014101L,
    468013987L,
    468013955L,
    468013618L,
    468013590L,
    468013486L,
    468013027L,
    468013020L,
    468012822L,
    468012562L,
    468012281L,
    468011335L,
    468011281L,
    468010951L,
    468010880L,
    468010655L,
    468010489L,
    468010440L,
    468010261L,
    468010258L,
    468010050L,
    468010030L,
    468009993L,
    468009804L,
    468009754L,
    468008991L,
    468008716L,
    468008713L,
    468008553L,
    468008513L,
    468008294L,
    468008322L,
    468008110L,
    468007999L,
    468007796L,
    468007789L,
    468007653L,
    468007344L,
    468007094L,
    468007086L,
    468006986L,
    468006754L,
    468006752L,
    468006643L,
    468006547L,
    468006609L,
    468006594L,
    468006457L,
    468006368L,
    468006216L,
    468006098L,
    468006060L,
    468005862L,
    468005587L,
    468005618L,
    468005546L,
    468005189L,
    468005209L,
    468005141L,
    468005080L,
    468004966L,
    468004897L,
    468004860L,
    468004823L,
    468004751L,
    468004587L,
    468004481L,
    468004432L,
    468004469L,
    468004430L,
    468004356L,
    468004380L,
    468004104L,
    468003970L,
    468003669L,
    468003589L,
    468003534L,
    468003446L,
    468003381L,
    468003176L,
    468003154L,
    468003043L,
    468003041L,
    468002763L,
    468002686L,
    468002657L,
    468002216L,
    468002162L,
    468002111L,
    468002019L,
    468001980L,
    468001958L,
    468001871L,
    468001817L,
    468001486L,
    468001385L,
    468001404L,
    468001319L,
    468001269L,
    468001191L,
    468001156L,
    468001134L,
    468001126L,
    468001054L,
    468001035L,
    468000910L,
    468000743L,
    468000651L,
    468000527L,
    468000480L,
    468000416L,
    468000353L,
    468000220L,
    468000080L,
    468000055L,
    468000032L,
    467999899L,
    467999844L,
    467999837L,
    467999472L,
    467999475L,
    467999178L,
    467998591L,
    467998227L,
    467998140L,
    467998088L,
    467998018L,
    467997858L,
    467997792L,
    467997795L,
    467997480L,
    467997312L,
    467997335L,
    467996847L,
    467996629L,
    467996546L,
    467996346L,
    467996035L,
    467995903L,
    467995623L,
    467995648L,
    467995599L,
    467994793L,
    467994669L,
    467994664L,
    467994665L,
    467994420L,
    467994127L,
    467993915L,
    467993586L,
    467993528L,
    467993288L,
    467993210L,
    467993061L,
    467992881L,
    467992819L,
    467992774L,
    467992449L,
    467992422L,
    467992154L,
    467992142L,
    467992039L,
    467991949L,
    467991815L,
    467991780L,
    467991691L,
    467991464L,
    467991385L,
    467991341L,
    467991303L,
    467991245L

)