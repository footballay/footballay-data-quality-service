package com.footballay.dataquality.pipeline.store.mongo

import com.github.f4b6a3.ulid.UlidCreator
import com.footballay.dataquality.pipeline.port.QualityResultStore
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssue
import com.footballay.dataquality.pipeline.result.QualityIssueCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueEvidence
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityIssueWrite
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import com.footballay.dataquality.pipeline.result.QualityResponseLocation
import com.footballay.dataquality.pipeline.result.QualityResult
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import org.springframework.data.mongodb.core.MongoOperations
import java.time.Instant

/** 검사 결과를 MongoDB에 단순 insert로 보존하는 저장소다. */
class MongoQualityResultStore(
    private val mongoOperations: MongoOperations,
) : QualityResultStore {
    override fun save(write: QualityResultWrite): QualityResult {
        val result = materializeResult(write)
        mongoOperations.insert(result.toDocument())
        return result
    }

    private fun materializeResult(write: QualityResultWrite): QualityResult {
        val issues = write.issues.map { it.toQualityIssue(write.checkedAt) }
        val hasIssue = issues.isNotEmpty()

        return QualityResult(
            resultId = newId(write.checkedAt),
            rawEventId = write.rawEventId,
            provider = write.provider,
            endpointKey = write.endpointKey,
            parameters = write.parameters,
            canonicalHash = write.canonicalHash,
            rawJsonObjectKey = write.rawJsonObjectKey,
            collectedAt = write.collectedAt,
            checkedAt = write.checkedAt,
            inspectionVersion = write.inspectionVersion,
            issues = issues,
            hasIssue = hasIssue,
            issueCount = issues.size,
            maxSeverity = issues.maxOfOrNull { it.severity.toMaxSeverity() } ?: QualityMaxSeverity.NONE,
            checkStatus = if (hasIssue) QualityCheckStatus.NEED_CHECK else QualityCheckStatus.NO_ISSUE,
        )
    }

    private fun QualityIssueWrite.toQualityIssue(auditAt: Instant): QualityIssue =
        QualityIssue(
            issueInstanceId = newId(auditAt),
            suggestedTypeCode = suggestedTypeCode,
            inspectionCode = inspectionCode,
            confirmedTypeCode = null,
            checkStatus = QualityIssueCheckStatus.NEED_CHECK,
            severity = severity,
            title = title,
            responseLocation = responseLocation,
            evidence = evidence,
            createdAt = auditAt,
            updatedAt = auditAt,
        )

    private fun QualityResult.toDocument(): QualityResultDocument =
        QualityResultDocument(
            resultId = resultId,
            rawEventId = rawEventId,
            provider = provider,
            endpointKey = endpointKey,
            parameters = parameters.map { QualityParameterDocument(it.name, it.value) },
            canonicalHash = canonicalHash,
            rawJsonObjectKey = rawJsonObjectKey,
            collectedAt = collectedAt,
            checkedAt = checkedAt,
            inspectionVersion = inspectionVersion,
            issues = issues.map { it.toDocument() },
            hasIssue = hasIssue,
            issueCount = issueCount,
            maxSeverity = maxSeverity,
            checkStatus = checkStatus,
            archive = QualityResultArchiveDocument(QualityResultArchiveStatus.NONE, null, null, null),
            createdAt = checkedAt,
            updatedAt = checkedAt,
        )

    private fun QualityIssue.toDocument(): QualityIssueDocument =
        QualityIssueDocument(
            issueInstanceId = issueInstanceId,
            suggestedTypeCode = suggestedTypeCode,
            confirmedTypeCode = confirmedTypeCode,
            inspectionCode = inspectionCode,
            checkStatus = checkStatus,
            severity = severity,
            title = title,
            responseLocation = responseLocation.toDocument(),
            evidence = evidence?.toDocument(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun QualityResponseLocation.toDocument(): QualityResponseLocationDocument =
        QualityResponseLocationDocument(section = section, path = path)

    private fun QualityIssueEvidence.toDocument(): QualityIssueEvidenceDocument =
        QualityIssueEvidenceDocument(values = values)

    private fun QualityIssueSeverity.toMaxSeverity(): QualityMaxSeverity =
        when (this) {
            QualityIssueSeverity.INFO -> QualityMaxSeverity.INFO
            QualityIssueSeverity.WARN -> QualityMaxSeverity.WARN
            QualityIssueSeverity.ERROR -> QualityMaxSeverity.ERROR
            QualityIssueSeverity.CRITICAL -> QualityMaxSeverity.CRITICAL
        }

    private fun newId(at: Instant): String = UlidCreator.getMonotonicUlid(at.toEpochMilli()).toString()
}
