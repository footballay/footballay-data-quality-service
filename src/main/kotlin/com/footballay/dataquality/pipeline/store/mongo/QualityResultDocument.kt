package com.footballay.dataquality.pipeline.store.mongo

import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/** `quality_results` 컬렉션의 DQ 서비스 소유 저장 계약이다. */
@Document(collection = "quality_results")
@CompoundIndexes(
    CompoundIndex(
        name = "provider_endpoint_parameter_idx",
        def = "{ 'provider': 1, 'endpointKey': 1, 'parameters.name': 1, 'parameters.value': 1 }",
    ),
    CompoundIndex(name = "has_issue_checked_at_idx", def = "{ 'hasIssue': 1, 'checkedAt': -1 }"),
    CompoundIndex(name = "max_severity_checked_at_idx", def = "{ 'maxSeverity': 1, 'checkedAt': -1 }"),
    CompoundIndex(name = "check_status_checked_at_idx", def = "{ 'checkStatus': 1, 'checkedAt': -1 }"),
    CompoundIndex(name = "archive_status_checked_at_idx", def = "{ 'archive.status': 1, 'checkedAt': -1 }"),
)
data class QualityResultDocument(
    @Id
    val resultId: String,
    @Indexed(name = "raw_event_id_idx")
    val rawEventId: String,
    val provider: DataProvider,
    val endpointKey: String,
    val parameters: List<QualityParameterDocument>,
    val canonicalHash: String,
    @Indexed(name = "raw_json_object_key_idx")
    val rawJsonObjectKey: String,
    val collectedAt: Instant,
    @Indexed(name = "checked_at_desc_idx", direction = DESCENDING)
    val checkedAt: Instant,
    val inspectionVersion: Int,
    val issues: List<QualityIssueDocument>,
    val hasIssue: Boolean,
    val issueCount: Int,
    val maxSeverity: QualityMaxSeverity,
    val checkStatus: QualityCheckStatus,
    val archive: QualityResultArchiveDocument,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QualityParameterDocument(
    val name: String,
    val value: String,
)

data class QualityIssueDocument(
    val issueInstanceId: String,
    @Indexed(name = "suggested_type_code_idx")
    val suggestedTypeCode: QualityIssueType,
    @Indexed(name = "confirmed_type_code_idx")
    val confirmedTypeCode: QualityIssueType?,
    val inspectionCode: String,
    val checkStatus: QualityIssueCheckStatus,
    val severity: QualityIssueSeverity,
    val title: String,
    val responseLocation: QualityResponseLocationDocument,
    val evidence: QualityIssueEvidenceDocument?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QualityResponseLocationDocument(
    val section: String,
    val path: String,
)

data class QualityIssueEvidenceDocument(
    val values: Map<String, String?>,
)

data class QualityResultArchiveDocument(
    val status: QualityResultArchiveStatus,
    val objectKey: String?,
    val archivedAt: Instant?,
    val expiredAt: Instant?,
)

enum class QualityResultArchiveStatus {
    NONE,
    ARCHIVED,
    EXPIRED,
    FAILED,
}
