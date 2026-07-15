package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.pipeline.QualityIssue
import com.footballay.footballaydataqualityservice.pipeline.QualityIssueSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.util.zip.GZIPInputStream

class ApiSportsFixtureSingleInspectionProcessorTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `collects issues from inspectors for the fixture single root DTO`() {
        val inspector =
            QualityInspector<ApiSportsFixtureSingleRootDto> { dto ->
                if (dto.get == "fixtures") {
                    listOf(
                        QualityIssue(
                            suggestedTypeCode = "TEST_ROOT_ISSUE",
                            severity = QualityIssueSeverity.INFO,
                            title = "Test root issue",
                            section = "root",
                            path = "\$.get",
                        ),
                    )
                } else {
                    emptyList()
                }
            }
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, listOf(inspector))

        val result = processor.inspect(objectMapper.readTree("""{"get":"fixtures"}"""))

        assertThat(result.issues.map { it.suggestedTypeCode }).containsExactly("TEST_ROOT_ISSUE")
    }

    @Test
    fun `returns a critical issue when raw JSON cannot map to the root DTO`() {
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, emptyList())

        val result = processor.inspect(objectMapper.readTree("[]"))

        val issue = result.issues.single()
        assertThat(issue.suggestedTypeCode).isEqualTo("RAW_JSON_MAPPING_FAILED")
        assertThat(issue.severity).isEqualTo(QualityIssueSeverity.CRITICAL)
        assertThat(issue.section).isEqualTo("root")
        assertThat(issue.path).isEqualTo("$")
    }

    @Test
    fun `maps the API Sports fixture single gzip test resource without issues`() {
        val processor = ApiSportsFixtureSingleInspectionProcessor(objectMapper, emptyList())
        val rawJson =
            ClassPathResource("mock/data-quality/mock-apisports-fixturesingle.json.gz").inputStream.use { input ->
                GZIPInputStream(input).use(objectMapper::readTree)
            }

        val result = processor.inspect(rawJson)

        assertThat(result.issues).isEmpty()
    }
}
