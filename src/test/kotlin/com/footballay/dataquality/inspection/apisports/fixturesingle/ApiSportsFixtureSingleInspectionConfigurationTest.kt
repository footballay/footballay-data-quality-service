package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier

class ApiSportsFixtureSingleInspectionConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
            .withUserConfiguration(ApiSportsFixtureSingleInspectionConfiguration::class.java)
            .withPropertyValues("footballay.data-quality.pipeline.enabled=true")

    @Test
    fun `injects response and request-response inspectors by generic target type`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            val processor = context.getBean(QualityInspectionProcessor::class.java)
            val result =
                processor.inspect(
                    request =
                        QualityCheckRequest(
                            provider = DataProvider.API_SPORTS,
                            endpointKey = "fixtureSingle",
                            parameters = mapOf("fixtureId" to listOf("123456")),
                        ),
                    rawJson =
                        context.getBean(ObjectMapper::class.java).readTree(
                            """
                            {
                              "get":"fixtures",
                              "parameters":{},
                              "errors":[],
                              "results":1,
                              "paging":{"current":1,"total":1},
                              "response":[{
                                "fixture":{"id":654321},
                                "league":{"id":0,"name":"League","season":2026,"round":"Round 1"},
                                "teams":{"home":{"id":1,"name":"Home"},"away":null},
                                "goals":{"home":-1,"away":0},
                                "score":{"fulltime":{"home":0,"away":0}},
                                "events":[{"time":{"elapsed":1},"team":{"id":1},"type":"Provider Change","detail":"Value"}]
                              }]
                            }
                            """.trimIndent(),
                        ),
                )

            assertThat(result.map { it.inspectionRuleCode.value })
                .contains(
                    "PARAMETER_ID_MISSING",
                    "FIXTURE_ID_MISMATCH",
                    "LEAGUE_ID_MISSING_OR_INVALID",
                    "AWAY_TEAM_MISSING",
                    "GOALS_NEGATIVE",
                    "UNKNOWN_EVENT_TYPE",
                )
        }
    }
}
