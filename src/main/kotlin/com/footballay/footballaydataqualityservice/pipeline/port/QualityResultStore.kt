package com.footballay.footballaydataqualityservice.pipeline.port

import com.github.f4b6a3.ulid.UlidCreator
import com.footballay.footballaydataqualityservice.pipeline.result.QualityCheckStatus
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssue
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueCheckStatus
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueSeverity
import com.footballay.footballaydataqualityservice.pipeline.result.QualityIssueWrite
import com.footballay.footballaydataqualityservice.pipeline.result.QualityMaxSeverity
import com.footballay.footballaydataqualityservice.pipeline.result.QualityResult
import com.footballay.footballaydataqualityservice.pipeline.result.QualityResultWrite

fun interface QualityResultStore {
    fun save(write: QualityResultWrite): QualityResult
}

class NoopQualityResultStore : QualityResultStore {
    override fun save(write: QualityResultWrite): QualityResult {
        val issues = write.issues.map { it.toQualityIssue(write.checkedAt) }
        val hasIssue = issues.isNotEmpty()

        return QualityResult(
            resultId = newId(write.checkedAt.toEpochMilli()),
            rawEventId = write.rawEventId,
            provider = write.provider,
            endpointKey = write.endpointKey,
            parameters = write.parameters,
            canonicalHash = write.canonicalHash,
            rawJsonObjectKey = write.rawJsonObjectKey,
            checkedAt = write.checkedAt,
            inspectionVersion = write.inspectionVersion,
            issues = issues,
            hasIssue = hasIssue,
            issueCount = issues.size,
            maxSeverity = issues.maxOfOrNull { it.severity.toMaxSeverity() } ?: QualityMaxSeverity.NONE,
            checkStatus = if (hasIssue) QualityCheckStatus.NEED_CHECK else QualityCheckStatus.NO_ISSUE,
        )
    }

    private fun QualityIssueWrite.toQualityIssue(auditAt: java.time.Instant): QualityIssue =
        QualityIssue(
            issueInstanceId = newId(auditAt.toEpochMilli()),
            suggestedTypeCode = suggestedTypeCode,
            confirmedTypeCode = null,
            checkStatus = QualityIssueCheckStatus.NEED_CHECK,
            severity = severity,
            title = title,
            responseLocation = responseLocation,
            evidence = evidence,
            createdAt = auditAt,
            updatedAt = auditAt,
        )

    private fun QualityIssueSeverity.toMaxSeverity(): QualityMaxSeverity =
        when (this) {
            QualityIssueSeverity.INFO -> QualityMaxSeverity.INFO
            QualityIssueSeverity.WARN -> QualityMaxSeverity.WARN
            QualityIssueSeverity.ERROR -> QualityMaxSeverity.ERROR
            QualityIssueSeverity.CRITICAL -> QualityMaxSeverity.CRITICAL
        }

    private fun newId(timestamp: Long): String = UlidCreator.getMonotonicUlid(timestamp).toString()
}
