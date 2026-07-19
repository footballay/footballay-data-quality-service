package com.footballay.dataquality.pipeline.port

import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityIssueWrite
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import com.footballay.dataquality.pipeline.result.QualityResponseLocation
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class NoopQualityResultStoreTest {
    private val checkedAt = Instant.parse("2026-07-13T00:00:00Z")

    @Test
    fun `materializes identifiers audit values and summaries for issue writes`() {
        val writes =
            listOf(
                issue("APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL", QualityIssueSeverity.WARN),
                issue("APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL", QualityIssueSeverity.CRITICAL),
            )

        val result = NoopQualityResultStore().save(write(writes))

        assertThat(result.resultId).matches("[0-9A-HJKMNP-TV-Z]{26}")
        assertThat(result.issues.map { it.issueInstanceId }).doesNotHaveDuplicates()
        assertThat(result.issues).allSatisfy { issue ->
            assertThat(issue.suggestedTypeCode).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
            assertThat(issue.inspectionCode).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL")
            assertThat(issue.confirmedTypeCode).isNull()
            assertThat(issue.checkStatus).isEqualTo(QualityIssueCheckStatus.NEED_CHECK)
            assertThat(issue.createdAt).isEqualTo(checkedAt)
            assertThat(issue.updatedAt).isEqualTo(checkedAt)
        }
        assertThat(result.hasIssue).isTrue()
        assertThat(result.issueCount).isEqualTo(2)
        assertThat(result.maxSeverity).isEqualTo(QualityMaxSeverity.CRITICAL)
        assertThat(result.checkStatus).isEqualTo(QualityCheckStatus.NEED_CHECK)
    }

    @Test
    fun `materializes no issue summaries for an empty write`() {
        val result = NoopQualityResultStore().save(write(emptyList()))

        assertThat(result.issues).isEmpty()
        assertThat(result.hasIssue).isFalse()
        assertThat(result.issueCount).isZero()
        assertThat(result.maxSeverity).isEqualTo(QualityMaxSeverity.NONE)
        assertThat(result.checkStatus).isEqualTo(QualityCheckStatus.NO_ISSUE)
    }

    private fun issue(
        inspectionCode: String,
        severity: QualityIssueSeverity,
    ) =
        QualityIssueWrite(
            suggestedTypeCode = QualityIssueType.UNEXPECTED_NULL,
            inspectionCode = inspectionCode,
            severity = severity,
            title = "Player ID is null",
            responseLocation = QualityResponseLocation("players", "$.response[0].player.id"),
        )

    private fun write(issues: List<QualityIssueWrite>) =
        QualityResultWrite(
            rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "123456")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = "football-data/raw/example.json.gz",
            checkedAt = checkedAt,
            inspectionVersion = "test-v1",
            issues = issues,
        )
}
