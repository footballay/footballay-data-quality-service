package com.footballay.footballaydataqualityservice.pipeline.processing

import com.github.f4b6a3.ulid.UlidCreator
import com.footballay.footballaydataqualityservice.logger
import com.footballay.footballaydataqualityservice.inspection.InspectionFinding
import com.footballay.footballaydataqualityservice.pipeline.QualityChecker
import com.footballay.footballaydataqualityservice.pipeline.QualityScanResult
import com.footballay.footballaydataqualityservice.pipeline.idempotency.RawEventIdempotencyChecker
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest
import com.footballay.footballaydataqualityservice.pipeline.input.QualityParameter
import com.footballay.footballaydataqualityservice.pipeline.input.RawCollectedEvent
import com.footballay.footballaydataqualityservice.pipeline.port.QualityResultPublisher
import com.footballay.footballaydataqualityservice.pipeline.port.QualityResultStore
import com.footballay.footballaydataqualityservice.pipeline.rawresponse.RawResponseReader
import com.footballay.footballaydataqualityservice.pipeline.result.QualityCheckStatus
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssue
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueCheckStatus
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueEvidence
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueSeverity
import com.footballay.footballaydataqualityservice.pipeline.result.QualityMaxSeverity
import com.footballay.footballaydataqualityservice.pipeline.result.QualityResponseLocation
import com.footballay.footballaydataqualityservice.pipeline.result.QualityResult
import java.time.Clock

class DataQualityPipelineService(
    private val idempotencyChecker: RawEventIdempotencyChecker,
    private val rawResponseReader: RawResponseReader,
    private val qualityChecker: QualityChecker,
    private val resultStore: QualityResultStore,
    private val resultPublisher: QualityResultPublisher,
    private val clock: Clock,
) {
    private val log = logger()

    fun process(event: RawCollectedEvent) {
        try {
            if (!idempotencyChecker.shouldProcess(event.rawEventId)) {
                return
            }

            val rawJson = rawResponseReader.read(event.rawJsonObjectKey)
            val scanResult = qualityChecker.check(event.toQualityCheckRequest(), rawJson)
            val savedResult = resultStore.save(createResult(event, scanResult))
            resultPublisher.publish(savedResult)
        } catch (exception: Exception) {
            log.error(
                "Data quality pipeline processing failed. rawEventId={}, provider={}, endpointKey={}",
                event.rawEventId,
                event.provider,
                event.endpointKey,
                exception,
            )
            throw exception
        }
    }

    private fun RawCollectedEvent.toQualityCheckRequest(): QualityCheckRequest =
        QualityCheckRequest(
            provider = provider,
            endpointKey = endpointKey,
            parameters = parameters.groupBy(QualityParameter::name, QualityParameter::value),
        )

    private fun createResult(
        event: RawCollectedEvent,
        scanResult: QualityScanResult,
    ): QualityResult {
        val checkedAt = clock.instant()
        val issues = scanResult.findings.map { it.toQualityIssue(checkedAt) }
        val maxSeverity = issues.maxOfOrNull { it.severity.toMaxSeverity() } ?: QualityMaxSeverity.NONE
        val hasIssue = issues.isNotEmpty()

        return QualityResult(
            resultId = UlidCreator.getMonotonicUlid(checkedAt.toEpochMilli()).toString(),
            rawEventId = event.rawEventId,
            provider = event.provider,
            endpointKey = event.endpointKey,
            parameters = event.parameters,
            canonicalHash = event.canonicalHash,
            rawJsonObjectKey = event.rawJsonObjectKey,
            checkedAt = checkedAt,
            scannerVersion = scanResult.scannerVersion,
            issues = issues,
            hasIssue = hasIssue,
            issueCount = issues.size,
            maxSeverity = maxSeverity,
            checkStatus = if (hasIssue) QualityCheckStatus.NEED_CHECK else QualityCheckStatus.NO_ISSUE,
        )
    }

    private fun InspectionFinding.toQualityIssue(checkedAt: java.time.Instant): QualityIssue =
        QualityIssue(
            issueInstanceId = UlidCreator.getMonotonicUlid(checkedAt.toEpochMilli()).toString(),
            suggestedTypeCode = suggestedTypeCode,
            checkStatus = QualityIssueCheckStatus.NEED_CHECK,
            severity = QualityIssueSeverity.valueOf(severity.name),
            title = title,
            responseLocation = QualityResponseLocation(responseLocation.section, responseLocation.path),
            evidence = evidence?.let { QualityIssueEvidence(it.values) },
            createdAt = checkedAt,
            updatedAt = checkedAt,
        )

    private fun QualityIssueSeverity.toMaxSeverity(): QualityMaxSeverity =
        when (this) {
            QualityIssueSeverity.INFO -> QualityMaxSeverity.INFO
            QualityIssueSeverity.WARN -> QualityMaxSeverity.WARN
            QualityIssueSeverity.ERROR -> QualityMaxSeverity.ERROR
            QualityIssueSeverity.CRITICAL -> QualityMaxSeverity.CRITICAL
        }

}
