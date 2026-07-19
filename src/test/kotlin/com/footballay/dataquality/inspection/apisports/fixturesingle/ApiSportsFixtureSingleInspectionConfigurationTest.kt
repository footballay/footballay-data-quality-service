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
                            """{"get":"fixtures","parameters":{},"errors":[],"results":1,"paging":{"current":1,"total":1},"response":[{"fixture":{"id":654321}}]}""",
                        ),
                )

            assertThat(result.map { it.inspectionRuleCode.value })
                .contains("PARAMETER_ID_MISSING", "FIXTURE_ID_MISMATCH")
        }
    }
}
