package com.footballay.dataquality.pipeline.store.mongo

import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityIssueWrite
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import com.footballay.dataquality.pipeline.result.QualityResponseLocation
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.data.mongodb.core.MongoOperations
import java.time.Instant

class MongoQualityResultStoreTest {
    private val mongoOperations = mock(MongoOperations::class.java)
    private val store = MongoQualityResultStore(mongoOperations)

    @Test
    fun `stores exactly the result it returns`() {
        val result = store.save(write(listOf(issue(QualityIssueSeverity.WARN), issue(QualityIssueSeverity.CRITICAL))))
        val documentCaptor = ArgumentCaptor.forClass(QualityResultDocument::class.java)

        verify(mongoOperations).insert(documentCaptor.capture())

        val document = documentCaptor.value
        assertThat(document.resultId).isEqualTo(result.resultId)
        assertThat(document.collectedAt).isEqualTo(result.collectedAt)
        assertThat(document.checkedAt).isEqualTo(result.checkedAt)
        assertThat(document.inspectionVersion).isEqualTo(result.inspectionVersion)
        assertThat(document.hasIssue).isEqualTo(result.hasIssue)
        assertThat(document.issueCount).isEqualTo(result.issueCount)
        assertThat(document.maxSeverity).isEqualTo(result.maxSeverity)
        assertThat(document.checkStatus).isEqualTo(result.checkStatus)
        assertThat(document.issues.map { it.issueInstanceId }).containsExactlyElementsOf(result.issues.map { it.issueInstanceId })
        assertThat(document.archive).isEqualTo(QualityResultArchiveDocument(QualityResultArchiveStatus.NONE, null, null, null))
        assertThat(document.createdAt).isEqualTo(result.checkedAt)
        assertThat(document.updatedAt).isEqualTo(result.checkedAt)
        assertThat(result.maxSeverity).isEqualTo(QualityMaxSeverity.CRITICAL)
        assertThat(result.checkStatus).isEqualTo(QualityCheckStatus.NEED_CHECK)
    }

    @Test
    fun `inserts identical raw event and inspection version independently`() {
        val write = write(emptyList())

        val first = store.save(write)
        val second = store.save(write)

        verify(mongoOperations, times(2)).insert(org.mockito.ArgumentMatchers.any(QualityResultDocument::class.java))
        assertThat(first.resultId).isNotEqualTo(second.resultId)
    }

    @Test
    fun `propagates insert failures`() {
        doThrow(IllegalStateException("MongoDB is unavailable"))
            .`when`(mongoOperations)
            .insert(org.mockito.ArgumentMatchers.any(QualityResultDocument::class.java))

        assertThatThrownBy { store.save(write(emptyList())) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("MongoDB is unavailable")
    }

    private fun issue(severity: QualityIssueSeverity): QualityIssueWrite =
        QualityIssueWrite(
            suggestedTypeCode = QualityIssueType.UNEXPECTED_NULL,
            inspectionCode = "APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL",
            severity = severity,
            title = "Fixture ID is null",
            responseLocation = QualityResponseLocation("fixture", "$.response[0].fixture.id"),
        )

    private fun write(issues: List<QualityIssueWrite>): QualityResultWrite =
        QualityResultWrite(
            rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "123456")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = "football-data/raw/example.json.gz",
            collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
            checkedAt = Instant.parse("2026-07-13T00:00:00Z"),
            inspectionVersion = 7,
            issues = issues,
        )
}
