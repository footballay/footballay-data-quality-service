package com.footballay.dataquality.pipeline.processing

import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionResponseLocation
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant

class DefaultQualityResultWriteMapperTest {
    private val mapper = DefaultQualityResultWriteMapper()
    private val event =
        RawCollectedEvent(
            rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "123456")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = "football-data/raw/example.json.gz",
            collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
        )

    @Test
    fun `maps an empty finding list with event context and inspection version`() {
        val checkedAt = Instant.parse("2026-07-13T00:00:00Z")

        val write =
            mapper.map(
                event = event,
                checkedAt = checkedAt,
                inspectionVersion = 7,
                foundIssues = emptyList(),
            )

        assertThat(write.rawEventId).isEqualTo(event.rawEventId)
        assertThat(write.provider).isEqualTo(event.provider)
        assertThat(write.endpointKey).isEqualTo(event.endpointKey)
        assertThat(write.parameters).isEqualTo(event.parameters)
        assertThat(write.canonicalHash).isEqualTo(event.canonicalHash)
        assertThat(write.rawJsonObjectKey).isEqualTo(event.rawJsonObjectKey)
        assertThat(write.collectedAt).isEqualTo(event.collectedAt)
        assertThat(write.checkedAt).isEqualTo(checkedAt)
        assertThat(write.inspectionVersion).isEqualTo(7)
        assertThat(write.issues).isEmpty()
    }

    @Test
    fun `maps event context version and inspection findings to a write`() {
        val write =
            mapper.map(
                event = event,
                checkedAt = Instant.parse("2026-07-13T00:00:00Z"),
                inspectionVersion = 7,
                foundIssues =
                    listOf(
                        FoundIssue(
                            suggestedType = QualityIssueType.UNEXPECTED_NULL,
                            inspectionName = InspectionName.of("FIXTURE_STATUS"),
                            inspectionRuleCode = TestRuleCode.LONG_NULL,
                            severity = InspectionSeverity.CRITICAL,
                            title = "Fixture status is null",
                            responseLocation = InspectionResponseLocation("fixture", "$.response[0].fixture.status"),
                            evidence = InspectionEvidence.of("status" to null),
                        ),
                    ),
            )

        assertThat(write.rawEventId).isEqualTo(event.rawEventId)
        assertThat(write.checkedAt).isEqualTo(Instant.parse("2026-07-13T00:00:00Z"))
        assertThat(write.inspectionVersion).isEqualTo(7)
        val issue = write.issues.single()
        assertThat(issue.suggestedTypeCode).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(issue.inspectionCode).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE_STATUS:LONG_NULL")
        assertThat(issue.severity).isEqualTo(QualityIssueSeverity.CRITICAL)
        assertThat(issue.title).isEqualTo("Fixture status is null")
        assertThat(issue.responseLocation.section).isEqualTo("fixture")
        assertThat(issue.evidence?.values).containsEntry("status", null)
    }

    @Test
    fun `rejects an inspection rule code outside uppercase snake case`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                mapper.map(
                    event = event,
                    checkedAt = Instant.parse("2026-07-13T00:00:00Z"),
                    inspectionVersion = 1,
                    foundIssues =
                        listOf(
                            FoundIssue(
                                suggestedType = QualityIssueType.INVALID_FORMAT,
                                inspectionName = InspectionName.of("FIXTURE_STATUS"),
                                inspectionRuleCode = InvalidRuleCode,
                                severity = InspectionSeverity.ERROR,
                                title = "Invalid rule code",
                                responseLocation = InspectionResponseLocation("fixture", "$.response[0]"),
                            ),
                        ),
                )
            }.withMessage("Inspection rule code must use uppercase snake case: long-null")
    }

    private enum class TestRuleCode : InspectionRuleCode {
        LONG_NULL,
        ;

        override val value: String
            get() = name
    }

    private object InvalidRuleCode : InspectionRuleCode {
        override val value: String = "long-null"
    }
}
