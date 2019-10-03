package no.nav.dagpenger.joark.mottak

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData

// From http://stash.devillo.no/projects/BOAF/repos/dok-avro/browse/dok-journalfoering-hendelse-v1/src/main/avro/schema/v1
private val schemaSource = """

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

fun lagJoarkHendelse(journalpostId: Long, tema: String, hendelsesType: String): GenericData.Record {
    val avroSchema = Schema.Parser().parse(schemaSource)
    return GenericData.Record(avroSchema).apply {
        put("journalpostId", journalpostId)
        put("hendelsesId", journalpostId.toString())
        put("versjon", journalpostId)
        put("hendelsesType", hendelsesType)
        put("journalpostStatus", "journalpostStatus")
        put("temaGammelt", tema)
        put("temaNytt", tema)
        put("mottaksKanal", "mottaksKanal")
        put("kanalReferanseId", "kanalReferanseId")
        put("behandlingstema", tema)
    }
}
