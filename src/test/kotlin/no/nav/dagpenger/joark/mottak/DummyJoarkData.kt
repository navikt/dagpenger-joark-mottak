package no.nav.dagpenger.joark.mottak

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData

// From https://stash.adeo.no/projects/BOAF/repos/dok-avro/browse/dok-journalfoering-hendelse-v1/src/main/avro/schema/v1/JournalfoeringHendelse.avsc
private val joarkjournalfoeringhendelserSchema =
    """

            {
              "namespace" : "no.nav.joarkjournalfoeringhendelser",
              "type" : "record",
              "name" : "JournalfoeringHendelseRecord",
              "fields" : [
                {"name": "hendelsesId", "type": "string"},
                {"name": "versjon", "type": "int"},
                {"name": "hendelsesType", "type": "string"},
                {"name": "journalpostId", "type": "long"},
                {"name": "journalpostStatus", "type": "string"},
                {"name": "temaGammelt", "type": "string"},
                {"name": "temaNytt", "type": "string"},
                {"name": "mottaksKanal", "type": "string"},
                {"name": "kanalReferanseId", "type": "string"},
                {"name": "behandlingstema", "type": "string", "default": ""}
              ]
            }

    """.trimIndent()

val joarkjournalfoeringhendelserAvroSchema = Schema.Parser().parse(joarkjournalfoeringhendelserSchema)

fun lagJoarkHendelse(journalpostId: Long, tema: String, hendelsesType: String, mottakskanal: String = "mottakskanal"): GenericData.Record {
    return GenericData.Record(joarkjournalfoeringhendelserAvroSchema).apply {
        put("journalpostId", journalpostId)
        put("hendelsesId", journalpostId.toString())
        put("versjon", journalpostId)
        put("hendelsesType", hendelsesType)
        put("journalpostStatus", "journalpostStatus")
        put("temaGammelt", tema)
        put("temaNytt", tema)
        put("mottaksKanal", mottakskanal)
        put("kanalReferanseId", "kanalReferanseId")
        put("behandlingstema", tema)
    }
}
