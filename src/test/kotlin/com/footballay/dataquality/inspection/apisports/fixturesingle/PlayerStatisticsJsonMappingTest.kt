package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import java.util.zip.GZIPInputStream

@JsonTest
class PlayerStatisticsJsonMappingTest {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `maps API Sports player statistics JSON including misspelled penalty field`() {
        val root = objectMapper.readValue(providerFixtureSingleJson, ApiSportsFixtureSingleRootDto::class.java)
        val entry = root.response!!.first()!!.players!!.first()!!.players!!.first()!!.statistics!!.first()!!

        assertThat(entry.passes?.accuracy).isEqualTo(121L)
        assertThat(entry.penalty?.committed).isEqualTo(1L)
        assertThat(entry.shots).isNull()
        assertThat(entry.goals?.assists).isNull()
    }

    @Test
    fun `accepts players from the normal fixture single response resource without player statistics issues`() {
        val root =
            GZIPInputStream(
                checkNotNull(javaClass.getResourceAsStream("/mock/data-quality/mock-apisports-fixturesingle.json.gz")),
            ).use { input ->
                objectMapper.readValue(input, ApiSportsFixtureSingleRootDto::class.java)
            }

        assertThat(PlayerStatisticsInspector().inspect(root)).isEmpty()
    }

    private companion object {
        val providerFixtureSingleJson =
            """
            {
              "response":[{
                "teams":{"home":{"id":1},"away":{"id":2}},
                "players":[
                  {"team":{"id":1},"players":[{"player":{"id":11,"name":"Home Player"},"statistics":[{"games":{"minutes":90,"number":9,"position":"F","rating":"7.2","captain":false,"substitute":false},"offsides":null,"shots":null,"goals":{"total":1,"assists":null,"saves":0},"passes":{"total":150,"key":2,"accuracy":"121"},"duels":{"total":8,"won":5},"dribbles":{"attempts":2,"success":1},"fouls":null,"cards":null,"penalty":{"won":null,"commited":1,"scored":0,"missed":0,"saved":0}}]}]},
                  {"team":{"id":2},"players":[{"player":{"id":21,"name":"Away Player"},"statistics":[{"games":{"minutes":90,"number":1,"position":"G","rating":null,"captain":false,"substitute":false},"goals":{"saves":3},"passes":{"total":28,"accuracy":"10"},"duels":{"total":1,"won":1},"dribbles":null,"penalty":{"commited":null}}]}]}
                ]
              }]
            }
            """.trimIndent()
    }
}
