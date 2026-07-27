package com.footballay.dataquality.pipeline.store.mongo

import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import java.time.Instant
import java.util.Date

class QualityResultDocumentTest {
    @Test
    fun `maps result contract with bson dates and enum names`() {
        val converter = converter()
        val document = Document()

        converter.write(qualityResultDocument(), document)

        assertThat(document["_id"]).isEqualTo("01JZKT4NBQ9E2R4Y5A0K7J6F3M")
        assertThat(document["inspectionVersion"]).isEqualTo(3)
        assertThat(document["collectedAt"]).isInstanceOf(Date::class.java)
        assertThat(document["checkedAt"]).isInstanceOf(Date::class.java)
        assertThat(document["provider"]).isEqualTo("API_SPORTS")
        assertThat(document["maxSeverity"]).isEqualTo("WARN")
        assertThat(document["checkStatus"]).isEqualTo("NEED_CHECK")
        assertThat(document.get("archive", Document::class.java)["status"]).isEqualTo("NONE")
        val issue = document.getList("issues", Document::class.java).single()
        assertThat(issue["suggestedTypeCode"]).isEqualTo("UNEXPECTED_NULL")
        assertThat(issue["inspectionCode"]).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL")
        assertThat(issue["checkStatus"]).isEqualTo("NEED_CHECK")
        assertThat(issue["severity"]).isEqualTo("WARN")
    }

    @Test
    fun `declares the required admin query indexes without reinspection constraints`() {
        val indexResolver = MongoPersistentEntityIndexResolver(mappingContext())

        val indexes = indexResolver.resolveIndexFor(QualityResultDocument::class.java).toList()
        val indexNames = indexes.mapNotNull { it.indexOptions.getString("name") }

        assertThat(indexNames).containsExactlyInAnyOrder(
            "raw_event_id_idx",
            "raw_json_object_key_idx",
            "checked_at_desc_idx",
            "provider_endpoint_parameter_idx",
            "has_issue_checked_at_idx",
            "max_severity_checked_at_idx",
            "check_status_checked_at_idx",
            "archive_status_checked_at_idx",
            "issues.suggested_type_code_idx",
            "issues.confirmed_type_code_idx",
        )
        assertThat(indexes).noneMatch { it.indexOptions.getBoolean("unique", false) }
        assertThat(indexNames).noneMatch { it.contains("inspection_version") || it.contains("reinspection") }
    }

    private fun converter(): MappingMongoConverter {
        val customConversions = MongoCustomConversions.create { }

        return MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext(customConversions)).apply {
            setCustomConversions(customConversions)
            afterPropertiesSet()
        }
    }

    private fun mappingContext(
        customConversions: MongoCustomConversions = MongoCustomConversions.create { },
    ): MongoMappingContext =
        MongoMappingContext().apply {
            setSimpleTypeHolder(customConversions.simpleTypeHolder)
            afterPropertiesSet()
        }

    private fun qualityResultDocument(): QualityResultDocument {
        val timestamp = Instant.parse("2026-07-13T00:00:00Z")

        return QualityResultDocument(
            resultId = "01JZKT4NBQ9E2R4Y5A0K7J6F3M",
            rawEventId = "01JZKT4M7QG3R6P2W8Y1V9N5XC",
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameterDocument("fixtureId", "123456")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = "football-data/raw/example.json.gz",
            collectedAt = timestamp,
            checkedAt = timestamp,
            inspectionVersion = 3,
            issues = listOf(
                QualityIssueDocument(
                    issueInstanceId = "01JZKT52A2S7KQZ3H8P4D6M1RB",
                    suggestedTypeCode = QualityIssueType.UNEXPECTED_NULL,
                    confirmedTypeCode = null,
                    inspectionCode = "APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL",
                    checkStatus = QualityIssueCheckStatus.NEED_CHECK,
                    severity = QualityIssueSeverity.WARN,
                    title = "Fixture ID is null",
                    responseLocation = QualityResponseLocationDocument("fixture", "$.response[0].fixture.id"),
                    evidence = QualityIssueEvidenceDocument(mapOf("fixtureId" to null)),
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            ),
            hasIssue = true,
            issueCount = 1,
            maxSeverity = QualityMaxSeverity.WARN,
            checkStatus = QualityCheckStatus.NEED_CHECK,
            archive = QualityResultArchiveDocument(QualityResultArchiveStatus.NONE, null, null, null),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }
}
