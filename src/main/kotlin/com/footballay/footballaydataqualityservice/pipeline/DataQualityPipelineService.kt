package com.footballay.footballaydataqualityservice.pipeline

import com.github.f4b6a3.ulid.UlidCreator
import java.time.Clock

class DataQualityPipelineService(
    private val idempotencyChecker: RawEventIdempotencyChecker,
    private val rawResponseReader: RawResponseReader,
    private val qualityChecker: QualityChecker,
    private val resultStore: QualityResultStore,
    private val resultPublisher: QualityResultPublisher,
    private val clock: Clock,
) {
    fun process(event: RawCollectedEvent) {
        if (!idempotencyChecker.shouldProcess(event.rawEventId)) {
            return
        }

        val rawJson = rawResponseReader.read(event.rawJsonObjectKey)
        val scanResult = qualityChecker.check(event, rawJson)
        val savedResult = resultStore.save(createResult(event, scanResult))
        resultPublisher.publish(savedResult)
    }

    private fun createResult(
        event: RawCollectedEvent,
        scanResult: QualityScanResult,
    ): QualityResult {
        val checkedAt = clock.instant()
        val maxSeverity = scanResult.issues.maxOfOrNull { it.severity.toMaxSeverity() } ?: QualityMaxSeverity.NONE
        val hasIssue = scanResult.issues.isNotEmpty()

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
            issues = scanResult.issues,
            hasIssue = hasIssue,
            issueCount = scanResult.issues.size,
            maxSeverity = maxSeverity,
            checkStatus = if (hasIssue) QualityCheckStatus.NEED_CHECK else QualityCheckStatus.NO_ISSUE,
        )
    }

    private fun QualityIssueSeverity.toMaxSeverity(): QualityMaxSeverity =
        when (this) {
            QualityIssueSeverity.INFO -> QualityMaxSeverity.INFO
            QualityIssueSeverity.WARN -> QualityMaxSeverity.WARN
            QualityIssueSeverity.ERROR -> QualityMaxSeverity.ERROR
            QualityIssueSeverity.CRITICAL -> QualityMaxSeverity.CRITICAL
        }
}
