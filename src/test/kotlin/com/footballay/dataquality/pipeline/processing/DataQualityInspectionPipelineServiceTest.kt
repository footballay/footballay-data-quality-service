package com.footballay.dataquality.pipeline.processing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionResponseLocation
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.QualityChecker
import com.footballay.dataquality.pipeline.idempotency.NoopRawEventIdempotencyChecker
import com.footballay.dataquality.pipeline.idempotency.RawEventIdempotencyChecker
import com.footballay.dataquality.pipeline.idempotency.RawEventIdempotencyClaim
import com.footballay.dataquality.pipeline.port.NoopQualityResultStore
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueCheckStatus
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import com.footballay.dataquality.pipeline.result.QualityResponseLocation
import com.footballay.dataquality.pipeline.result.QualityResult
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DataQualityInspectionPipelineServiceTest {
    @Test
    fun `processes a new raw event and requests publication after storage`() {
        val calls = mutableListOf<String>()
        var saved: QualityResult? = null
        var checkedRequest: QualityCheckRequest? = null
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = RecordingIdempotencyChecker(calls),
                rawResponseReader = {
                    calls += "reader"
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = qualityChecker { request, _ ->
                    calls += "checker"
                    checkedRequest = request
                    emptyList()
                },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = {
                    calls += "store"
                    NoopQualityResultStore().save(it).also { result -> saved = result }
                },
                resultPublisher = {
                    calls += "publisher"
                    assertThat(it).isSameAs(saved)
                },
                clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC),
            )

        pipeline.process(rawEvent())

        assertThat(calls).containsExactly("tryClaim", "reader", "checker", "store", "publisher", "complete")
        assertThat(checkedRequest)
            .isEqualTo(
                QualityCheckRequest(
                    provider = DataProvider.API_SPORTS,
                    endpointKey = "fixtureSingle",
                    parameters = mapOf("fixtureId" to listOf("123456")),
                    collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
                ),
            )
        assertThat(saved).isNotNull
        assertThat(saved!!.resultId).matches("[0-9A-HJKMNP-TV-Z]{26}")
        assertThat(saved!!.hasIssue).isFalse()
        assertThat(saved!!.issueCount).isZero()
        assertThat(saved!!.maxSeverity).isEqualTo(QualityMaxSeverity.NONE)
        assertThat(saved!!.checkStatus).isEqualTo(QualityCheckStatus.NO_ISSUE)
        assertThat(saved!!.inspectionVersion).isEqualTo(37)
    }

    @Test
    fun `does not continue when idempotency checker rejects the event`() {
        var readCalled = false
        val calls = mutableListOf<String>()
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = RecordingIdempotencyChecker(calls, allowClaim = false),
                rawResponseReader = {
                    readCalled = true
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = qualityChecker { _, _ -> emptyList() },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = NoopQualityResultStore(),
                resultPublisher = { },
                clock = Clock.systemUTC(),
            )

        pipeline.process(rawEvent())

        assertThat(readCalled).isFalse()
        assertThat(calls).containsExactly("tryClaim")
    }

    @Test
    fun `releases the claim and rethrows when raw response reading fails`() {
        val calls = mutableListOf<String>()
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = RecordingIdempotencyChecker(calls),
                rawResponseReader = {
                    calls += "reader"
                    throw IllegalStateException("Raw response is unavailable")
                },
                qualityChecker = qualityChecker { _, _ -> emptyList() },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = NoopQualityResultStore(),
                resultPublisher = { },
                clock = Clock.systemUTC(),
            )

        org.assertj.core.api.Assertions.assertThatIllegalStateException()
            .isThrownBy { pipeline.process(rawEvent()) }
            .withMessage("Raw response is unavailable")

        assertThat(calls).containsExactly("tryClaim", "reader", "release")
    }

    @Test
    fun `does not request publication when result storage fails`() {
        val calls = mutableListOf<String>()
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = RecordingIdempotencyChecker(calls),
                rawResponseReader = {
                    calls += "reader"
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = qualityChecker { _, _ ->
                    calls += "checker"
                    emptyList()
                },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = { calls += "store"; throw IllegalStateException("MongoDB is unavailable") },
                resultPublisher = { calls += "publisher" },
                clock = Clock.systemUTC(),
            )

        org.assertj.core.api.Assertions.assertThatIllegalStateException()
            .isThrownBy { pipeline.process(rawEvent()) }
            .withMessage("MongoDB is unavailable")

        assertThat(calls).containsExactly("tryClaim", "reader", "checker", "store", "release")
    }

    @Test
    fun `completes idempotency when publication request throws`() {
        val calls = mutableListOf<String>()
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = RecordingIdempotencyChecker(calls),
                rawResponseReader = {
                    calls += "reader"
                    JsonNodeFactory.instance.objectNode()
                },
                qualityChecker = qualityChecker { _, _ ->
                    calls += "checker"
                    emptyList()
                },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = {
                    calls += "store"
                    NoopQualityResultStore().save(it)
                },
                resultPublisher = {
                    calls += "publisher"
                    throw IllegalStateException("Kafka send start failed")
                },
                clock = Clock.systemUTC(),
            )

        pipeline.process(rawEvent())

        assertThat(calls).containsExactly("tryClaim", "reader", "checker", "store", "publisher", "complete")
    }

    @Test
    fun `materializes inspection findings as reviewable stored issues`() {
        var savedResult: QualityResult? = null
        var savedWrite: QualityResultWrite? = null
        val checkedAt = Instant.parse("2026-07-13T00:00:00Z")
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = NoopRawEventIdempotencyChecker(),
                rawResponseReader = { JsonNodeFactory.instance.objectNode() },
                qualityChecker = qualityChecker { _, _ ->
                    listOf(
                        FoundIssue(
                            suggestedType = QualityIssueType.UNEXPECTED_NULL,
                            inspectionName = InspectionName.of("FIXTURE_STATUS"),
                            inspectionRuleCode = TestRuleCode.LONG_NULL,
                            severity = InspectionSeverity.CRITICAL,
                            title = "Player ID is null",
                            responseLocation = InspectionResponseLocation("events", "$.response[0].events[32]"),
                            evidence = InspectionEvidence.of("eventType" to "subst", "playerId" to null),
                        ),
                    )
                },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = { write ->
                    savedWrite = write
                    NoopQualityResultStore().save(write).also { result -> savedResult = result }
                },
                resultPublisher = { },
                clock = Clock.fixed(checkedAt, ZoneOffset.UTC),
            )

        pipeline.process(rawEvent())

        assertThat(savedWrite!!.issues.single().suggestedTypeCode).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(savedWrite!!.issues.single().inspectionCode)
            .isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE_STATUS:LONG_NULL")
        assertThat(savedWrite!!.inspectionVersion).isEqualTo(37)
        val issue = savedResult!!.issues.single()
        assertThat(issue.issueInstanceId).matches("[0-9A-HJKMNP-TV-Z]{26}")
        assertThat(issue.confirmedTypeCode).isNull()
        assertThat(issue.suggestedTypeCode).isEqualTo(QualityIssueType.UNEXPECTED_NULL)
        assertThat(issue.inspectionCode).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE_STATUS:LONG_NULL")
        assertThat(issue.checkStatus).isEqualTo(QualityIssueCheckStatus.NEED_CHECK)
        assertThat(issue.responseLocation).isEqualTo(QualityResponseLocation("events", "$.response[0].events[32]"))
        assertThat(issue.evidence?.values).containsEntry("playerId", null)
        assertThat(issue.createdAt).isEqualTo(checkedAt)
        assertThat(issue.updatedAt).isEqualTo(checkedAt)
    }

    @Test
    fun `materializes an inspector execution failure with its composite inspection code`() {
        var savedWrite: QualityResultWrite? = null
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = NoopRawEventIdempotencyChecker(),
                rawResponseReader = { JsonNodeFactory.instance.objectNode() },
                qualityChecker = qualityChecker { _, _ ->
                    listOf(
                        FoundIssue(
                            suggestedType = QualityIssueType.UNEXPECTED_RESPONSE,
                            inspectionName = InspectionName.of("FIXTURE_STATUS"),
                            inspectionRuleCode = TestRuleCode.INSPECTION_FAILED,
                            severity = InspectionSeverity.CRITICAL,
                            title = "Inspector failed while checking response",
                            responseLocation = InspectionResponseLocation("inspection", "$"),
                            evidence = InspectionEvidence.of("exceptionType" to "IllegalArgumentException", "message" to "Unexpected status"),
                        ),
                    )
                },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = { write ->
                    savedWrite = write
                    NoopQualityResultStore().save(write)
                },
                resultPublisher = { },
                clock = Clock.systemUTC(),
            )

        pipeline.process(rawEvent())

        assertThat(savedWrite!!.issues.single().inspectionCode)
            .isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE_STATUS:INSPECTION_FAILED")
    }

    @Test
    fun `rejects an inspection rule code outside uppercase snake case`() {
        val pipeline =
            DataQualityInspectionPipelineService(
                idempotencyChecker = NoopRawEventIdempotencyChecker(),
                rawResponseReader = { JsonNodeFactory.instance.objectNode() },
                qualityChecker = qualityChecker { _, _ ->
                    listOf(
                        FoundIssue(
                            suggestedType = QualityIssueType.INVALID_FORMAT,
                            inspectionName = InspectionName.of("FIXTURE_STATUS"),
                            inspectionRuleCode = InvalidRuleCode,
                            severity = InspectionSeverity.ERROR,
                            title = "Invalid rule code",
                            responseLocation = InspectionResponseLocation("fixture", "$.response[0]"),
                        ),
                    )
                },
                qualityResultWriteMapper = DefaultQualityResultWriteMapper(),
                resultStore = NoopQualityResultStore(),
                resultPublisher = { },
                clock = Clock.systemUTC(),
            )

        assertThatIllegalArgumentException()
            .isThrownBy { pipeline.process(rawEvent()) }
            .withMessage("Inspection rule code must use uppercase snake case: long-null")
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

    private fun qualityChecker(
        inspectionVersion: Int = 37,
        check: (QualityCheckRequest, JsonNode) -> List<FoundIssue>,
    ): QualityChecker =
        object : QualityChecker {
            override val inspectionVersion: Int = inspectionVersion

            override fun check(
                request: QualityCheckRequest,
                rawJson: JsonNode,
            ): List<FoundIssue> = check(request, rawJson)
        }

    private class RecordingIdempotencyChecker(
        private val calls: MutableList<String>,
        private val allowClaim: Boolean = true,
    ) : RawEventIdempotencyChecker {
        override fun tryClaim(rawEventId: String): RawEventIdempotencyClaim? {
            calls += "tryClaim"
            return if (allowClaim) RawEventIdempotencyClaim(rawEventId, "test-owner") else null
        }

        override fun complete(claim: RawEventIdempotencyClaim) {
            calls += "complete"
        }

        override fun release(claim: RawEventIdempotencyClaim) {
            calls += "release"
        }
    }

    private enum class TestRuleCode : InspectionRuleCode {
        LONG_NULL,
        INSPECTION_FAILED,
        ;

        override val value: String
            get() = name
    }

    private object InvalidRuleCode : InspectionRuleCode {
        override val value: String = "long-null"
    }
}
