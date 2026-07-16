package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionFinding
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DataQualityPipelineServiceTest {
    @Test
    fun `processes a new raw event in pipeline order and publishes a no-issue result`() {
        val calls = mutableListOf<String>()
        var published: QualityResult? = null
        var checkedRequest: QualityCheckRequest? = null
        val pipeline =
            DataQualityPipelineService(
                idempotencyChecker = {
                    calls += "idempotency"
                    true
                },
                rawResponseReader = {
                    calls += "reader"
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = { request, _ ->
                    calls += "checker"
                    checkedRequest = request
                    QualityScanResult("always-happy-v1")
                },
                resultStore = {
                    calls += "store"
                    it
                },
                resultPublisher = {
                    calls += "publisher"
                    published = it
                },
                clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC),
            )

        pipeline.process(rawEvent())

        assertThat(calls).containsExactly("idempotency", "reader", "checker", "store", "publisher")
        assertThat(checkedRequest)
            .isEqualTo(
                QualityCheckRequest(
                    provider = DataProvider.API_SPORTS,
                    endpointKey = "fixtureSingle",
                    parameters = mapOf("fixtureId" to listOf("123456")),
                ),
            )
        assertThat(published).isNotNull
        assertThat(published!!.resultId).matches("[0-9A-HJKMNP-TV-Z]{26}")
        assertThat(published!!.hasIssue).isFalse()
        assertThat(published!!.issueCount).isZero()
        assertThat(published!!.maxSeverity).isEqualTo(QualityMaxSeverity.NONE)
        assertThat(published!!.checkStatus).isEqualTo(QualityCheckStatus.NO_ISSUE)
    }

    @Test
    fun `does not continue when idempotency checker rejects the event`() {
        var readCalled = false
        val pipeline =
            DataQualityPipelineService(
                idempotencyChecker = { false },
                rawResponseReader = {
                    readCalled = true
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = { _, _ -> QualityScanResult("test") },
                resultStore = NoopQualityResultStore(),
                resultPublisher = { },
                clock = Clock.systemUTC(),
            )

        pipeline.process(rawEvent())

        assertThat(readCalled).isFalse()
    }

    @Test
    fun `materializes inspection findings as reviewable stored issues`() {
        var savedResult: QualityResult? = null
        val checkedAt = Instant.parse("2026-07-13T00:00:00Z")
        val pipeline =
            DataQualityPipelineService(
                idempotencyChecker = { true },
                rawResponseReader = { JsonNodeFactory.instance.objectNode() },
                qualityChecker = { _, _ ->
                    QualityScanResult(
                        scannerVersion = "test",
                        findings =
                            listOf(
                                InspectionFinding(
                                    suggestedTypeCode = "PLAYER_ID_NULL",
                                    severity = InspectionSeverity.CRITICAL,
                                    title = "Player ID is null",
                                    responseLocation = InspectionResponseLocation("events", "$.response[0].events[32]"),
                                    evidence = InspectionEvidence.of("eventType" to "subst", "playerId" to null),
                                ),
                            ),
                    )
                },
                resultStore = { it.also { savedResult = it } },
                resultPublisher = { },
                clock = Clock.fixed(checkedAt, ZoneOffset.UTC),
            )

        pipeline.process(rawEvent())

        val issue = savedResult!!.issues.single()
        assertThat(issue.issueInstanceId).matches("[0-9A-HJKMNP-TV-Z]{26}")
        assertThat(issue.confirmedTypeCode).isNull()
        assertThat(issue.checkStatus).isEqualTo(QualityIssueCheckStatus.NEED_CHECK)
        assertThat(issue.responseLocation).isEqualTo(QualityResponseLocation("events", "$.response[0].events[32]"))
        assertThat(issue.evidence?.values).containsEntry("playerId", null)
        assertThat(issue.createdAt).isEqualTo(checkedAt)
        assertThat(issue.updatedAt).isEqualTo(checkedAt)
    }

    private fun rawEvent() =
        RawCollectedEvent(
            rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "123456")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = "football-data/raw/example.json.gz",
            collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
        )
}
