package com.footballay.dataquality.poc

import com.footballay.dataquality.support.SeaweedFsMiniContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeaweedFsS3CompatibilityIntegrationTest {
    @Test
    @DisplayName("AWS SDK v2 기본 S3 API와 metadata, overwrite, gzip binary를 지원한다.")
    fun supportsRequiredS3Operations() = withBucket { bucket ->
        val key = "raw/fixture.json.gz"
        val original = gzip("{\"fixture\":1379192,\"source\":\"api-sports\"}")
        val overwritten = gzip("{\"fixture\":1379192,\"source\":\"api-sports-v2\"}")

        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).metadata(mapOf("content" to "json.gz", "source" to "poc")).build(),
            RequestBody.fromBytes(original),
        )
        val firstHead = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        assertThat(firstHead.metadata()).containsEntry("content", "json.gz").containsEntry("source", "poc")
        assertThat(s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray())
            .containsExactly(*original)

        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(overwritten))
        assertThat(s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray())
            .containsExactly(*overwritten)
        assertThat(s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix("raw/").build()).contents())
            .extracting<String> { it.key() }
            .containsExactly(key)

        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        assertThatThrownBy { s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()) }
            .isInstanceOf(S3Exception::class.java)
            .extracting("statusCode")
            .isEqualTo(404)
    }

    @Test
    @DisplayName("S3Presigner가 만든 path-style presigned GET URL로 원본 gzip byte를 다운로드한다.")
    fun downloadsOriginalBytesThroughPresignedGet() = withBucket { bucket ->
        val key = "raw/presigned.json.gz"
        val original = gzip("{\"presigned\":true}")
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(original))

        val request =
            s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(2))
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build(),
            )
        val response =
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(request.url().toURI()).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )

        assertThat(request.url().path).isEqualTo("/$bucket/$key")
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).containsExactly(*original)
    }

    @Test
    @DisplayName("고유 bucket을 사용한 병렬 작업을 서로 간섭 없이 처리한다.")
    fun isolatesParallelBuckets() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results =
                executor.invokeAll(
                    (1..4).map { index ->
                        Callable {
                            withBucket { bucket ->
                                val key = "parallel/$index.txt"
                                val body = "bucket=$bucket".toByteArray()
                                s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(body))
                                s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray()
                            }
                        }
                    },
                )
            results.forEach { assertThat(String(it.get())).startsWith("bucket=") }
        } finally {
            executor.shutdownNow()
        }
    }

    @AfterAll
    fun closeClients() {
        s3Presigner.close()
        s3Client.close()
    }

    private fun <T> withBucket(block: (String) -> T): T {
        val bucket = "seaweedfs-poc-${UUID.randomUUID().toString().replace("-", "")}".take(63)
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        try {
            return block(bucket)
        } finally {
            s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build()).contents().forEach { objectSummary ->
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectSummary.key()).build())
            }
            s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build())
        }
    }

    private fun gzip(value: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(value.toByteArray()) }
            output.toByteArray()
        }

    private companion object {
        private val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(SeaweedFsMiniContainer.ACCESS_KEY, SeaweedFsMiniContainer.SECRET_KEY))

        @Container
        @JvmStatic
        val seaweedFs = SeaweedFsMiniContainer()

        private val s3Client: S3Client by lazy { createS3Client() }
        private val s3Presigner: S3Presigner by lazy { createS3Presigner() }

        private fun createS3Client(): S3Client =
            S3Client.builder().endpointOverride(URI.create(seaweedFs.endpoint())).region(Region.of("ap-northeast-2"))
                .credentialsProvider(credentials).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()

        private fun createS3Presigner(): S3Presigner =
            S3Presigner.builder().endpointOverride(URI.create(seaweedFs.endpoint())).region(Region.of("ap-northeast-2"))
                .credentialsProvider(credentials).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()
    }
}
