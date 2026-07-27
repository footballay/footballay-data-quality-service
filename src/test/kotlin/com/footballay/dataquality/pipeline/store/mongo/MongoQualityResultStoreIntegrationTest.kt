package com.footballay.dataquality.pipeline.store.mongo

import com.mongodb.client.model.Filters.eq
import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.port.QualityResultStore
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityIssueWrite
import com.footballay.dataquality.pipeline.result.QualityIssueEvidence
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import com.footballay.dataquality.pipeline.result.QualityResponseLocation
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.Date

/** 실제 MongoDB 저장 계약과 인덱스 생성을 검증하는 통합 테스트다. */
@Testcontainers
@SpringBootTest(
    classes = [MongoQualityResultStoreIntegrationTestApplication::class],
    webEnvironment = NONE,
    properties = [
        "footballay.data-quality.pipeline.enabled=true",
        "footballay.data-quality.pipeline.store.type=mongo",
    ],
)
class MongoQualityResultStoreIntegrationTest {
    @Autowired
    private lateinit var store: QualityResultStore

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun clearQualityResults() {
        mongoTemplate.getCollection(COLLECTION_NAME).deleteMany(Document())
    }

    @Test
    fun `selects Mongo store for lowercase configuration and preserves documents with and without issues`() {
        assertThat(store).isInstanceOf(MongoQualityResultStore::class.java)

        val noIssueWrite = write(rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M", issues = emptyList())
        val issueWrite =
            write(
                rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9N",
                issues = listOf(issue(QualityIssueSeverity.CRITICAL)),
            )

        val noIssueResult = store.save(noIssueWrite)
        val issueResult = store.save(issueWrite)

        assertNoIssueDocument(noIssueResult, noIssueWrite)
        assertIssueDocument(issueResult, issueWrite)
    }

    @Test
    fun `inserts duplicate raw event and inspection version as separate documents`() {
        val write = write(rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M", issues = emptyList())

        val first = store.save(write)
        val second = store.save(write)

        val documents =
            mongoTemplate
                .getCollection(COLLECTION_NAME)
                .find(eq("rawEventId", write.rawEventId))
                .filter(eq("inspectionVersion", write.inspectionVersion))
                .into(mutableListOf())

        assertThat(first.resultId).isNotEqualTo(second.resultId)
        assertThat(documents).hasSize(2)
        assertThat(documents.map { it.getString("_id") })
            .containsExactlyInAnyOrder(first.resultId, second.resultId)
    }

    @Test
    fun `creates planned non-unique indexes with declared keys`() {
        val indexes = mongoTemplate.getCollection(COLLECTION_NAME).listIndexes().into(mutableListOf())
        val indexesByName = indexes.associateBy { it.getString("name") }

        assertThat(indexesByName.keys).containsAll(PLANNED_INDEX_KEYS.keys)
        assertThat(indexesByName.keys).noneMatch { it.contains("inspection_version") || it.contains("reinspection") }
        assertThat(indexes.filter { it.getString("name") != "_id_" })
            .allMatch { it.getBoolean("unique", false).not() }

        PLANNED_INDEX_KEYS.forEach { (name, expectedKeys) ->
            val actualKeys = indexesByName.getValue(name).get("key", Document::class.java)
            assertThat(actualKeys.entries.map { it.key to it.value }).containsExactlyElementsOf(expectedKeys)
        }

        assertThat(indexesByName.getValue("issues.suggested_type_code_idx").get("key", Document::class.java))
            .containsEntry("issues.suggestedTypeCode", 1)
        assertThat(indexesByName.getValue("issues.confirmed_type_code_idx").get("key", Document::class.java))
            .containsEntry("issues.confirmedTypeCode", 1)
    }

    private fun assertNoIssueDocument(result: com.footballay.dataquality.pipeline.result.QualityResult, write: QualityResultWrite) {
        val document = findDocument(result.resultId)

        assertThat(document.getString("_id")).isEqualTo(result.resultId)
        assertThat(document.getString("rawEventId")).isEqualTo(write.rawEventId)
        assertThat(document.getDate("collectedAt")).isEqualTo(Date.from(write.collectedAt))
        assertThat(document.getDate("checkedAt")).isEqualTo(Date.from(write.checkedAt))
        assertThat(document.getInteger("inspectionVersion")).isEqualTo(write.inspectionVersion)
        assertThat(document.getList("issues", Document::class.java)).isEmpty()
        assertThat(document.getBoolean("hasIssue")).isFalse()
        assertThat(document.getInteger("issueCount")).isZero()
        assertThat(document.getString("maxSeverity")).isEqualTo(QualityMaxSeverity.NONE.name)
        assertThat(document.getString("checkStatus")).isEqualTo(QualityCheckStatus.NO_ISSUE.name)
        assertDefaultAuditAndArchive(document, write.checkedAt)
    }

    private fun assertIssueDocument(result: com.footballay.dataquality.pipeline.result.QualityResult, write: QualityResultWrite) {
        val document = findDocument(result.resultId)
        val issue = document.getList("issues", Document::class.java).single()

        assertThat(document.getString("_id")).isEqualTo(result.resultId)
        assertThat(document.getString("rawEventId")).isEqualTo(write.rawEventId)
        assertThat(document.getDate("collectedAt")).isEqualTo(Date.from(write.collectedAt))
        assertThat(document.getDate("checkedAt")).isEqualTo(Date.from(write.checkedAt))
        assertThat(document.getInteger("inspectionVersion")).isEqualTo(write.inspectionVersion)
        assertThat(document.getBoolean("hasIssue")).isTrue()
        assertThat(document.getInteger("issueCount")).isEqualTo(1)
        assertThat(document.getString("maxSeverity")).isEqualTo(QualityMaxSeverity.CRITICAL.name)
        assertThat(document.getString("checkStatus")).isEqualTo(QualityCheckStatus.NEED_CHECK.name)
        assertThat(issue.getString("inspectionCode")).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL")
        assertThat(issue.get("evidence", Document::class.java))
            .containsEntry("values", Document("requestedFixtureId", "123456"))
        assertThat(issue.getString("checkStatus")).isEqualTo("NEED_CHECK")
        assertThat(issue.getDate("createdAt")).isEqualTo(Date.from(write.checkedAt))
        assertThat(issue.getDate("updatedAt")).isEqualTo(Date.from(write.checkedAt))
        assertDefaultAuditAndArchive(document, write.checkedAt)
    }

    private fun findDocument(resultId: String): Document =
        requireNotNull(mongoTemplate.getCollection(COLLECTION_NAME).find(eq("_id", resultId)).first())

    private fun assertDefaultAuditAndArchive(document: Document, checkedAt: Instant) {
        assertThat(document.getDate("createdAt")).isEqualTo(Date.from(checkedAt))
        assertThat(document.getDate("updatedAt")).isEqualTo(Date.from(checkedAt))
        assertThat(document.get("archive", Document::class.java))
            .containsOnly(org.assertj.core.api.Assertions.entry("status", QualityResultArchiveStatus.NONE.name))
    }

    private fun issue(severity: QualityIssueSeverity): QualityIssueWrite =
        QualityIssueWrite(
            suggestedTypeCode = QualityIssueType.UNEXPECTED_NULL,
            inspectionCode = "APISPORTS:FIXTURE_SINGLE:FIXTURE:ID_NULL",
            severity = severity,
            title = "Fixture ID is null",
            responseLocation = QualityResponseLocation("fixture", "$.response[0].fixture.id"),
            evidence = QualityIssueEvidence(mapOf("requestedFixtureId" to "123456")),
        )

    private fun write(rawEventId: String, issues: List<QualityIssueWrite>): QualityResultWrite =
        QualityResultWrite(
            rawEventId = rawEventId,
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

    companion object {
        private const val COLLECTION_NAME = "quality_results"

        private val PLANNED_INDEX_KEYS =
            linkedMapOf(
                "raw_event_id_idx" to listOf("rawEventId" to 1),
                "raw_json_object_key_idx" to listOf("rawJsonObjectKey" to 1),
                "checked_at_desc_idx" to listOf("checkedAt" to -1),
                "provider_endpoint_parameter_idx" to
                    listOf("provider" to 1, "endpointKey" to 1, "parameters.name" to 1, "parameters.value" to 1),
                "has_issue_checked_at_idx" to listOf("hasIssue" to 1, "checkedAt" to -1),
                "max_severity_checked_at_idx" to listOf("maxSeverity" to 1, "checkedAt" to -1),
                "check_status_checked_at_idx" to listOf("checkStatus" to 1, "checkedAt" to -1),
                "archive_status_checked_at_idx" to listOf("archive.status" to 1, "checkedAt" to -1),
                "issues.suggested_type_code_idx" to listOf("issues.suggestedTypeCode" to 1),
                "issues.confirmed_type_code_idx" to listOf("issues.confirmedTypeCode" to 1),
            )

        @Container
        @JvmStatic
        private val mongo = MongoDBContainer(DockerImageName.parse("mongo:8.0"))

        @DynamicPropertySource
        @JvmStatic
        fun configureMongo(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl)
        }
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MongoQualityResultStoreConfiguration::class)
class MongoQualityResultStoreIntegrationTestApplication
