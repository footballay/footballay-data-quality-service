package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

class LocalFileRawResponseReaderTest {
    @TempDir
    lateinit var baseDir: Path

    @Test
    fun `reads gzip JSON based on magic bytes regardless of file extension`() {
        val objectPath = baseDir.resolve("raw/response.data")
        writeGzip(objectPath, """{"get":"fixtures"}""")

        val result = reader().read("raw/response.data")

        assertThat(result.path("get").asText()).isEqualTo("fixtures")
    }

    @Test
    fun `reads the API Sports fixture-single gzip test resource`() {
        val objectKey = "raw/mock-apisports-fixturesingle.json.gz"
        val objectPath = baseDir.resolve(objectKey)
        Files.createDirectories(objectPath.parent)
        ClassPathResource("mock/data-quality/mock-apisports-fixturesingle.json.gz").inputStream.use { input ->
            Files.copy(input, objectPath)
        }

        val result = reader().read(objectKey)

        assertThat(result.path("get").asText()).isEqualTo("fixtures")
        assertThat(result.path("parameters").path("id").asText()).isEqualTo("1582681")
        assertThat(result.path("response").isArray).isTrue()
        assertThat(result.path("response").first().path("fixture").path("id").asLong()).isEqualTo(1582681)
    }

    @Test
    fun `reads plain JSON even when file has gzip extension`() {
        val objectPath = baseDir.resolve("raw/response.json.gz")
        writeText(objectPath, """{"get":"leagues"}""")

        val result = reader().read(objectPath.toString())

        assertThat(result.path("get").asText()).isEqualTo("leagues")
    }

    @Test
    fun `rejects a path outside the configured base directory`() {
        val outsidePath = Files.createTempFile("outside-raw-response", ".json")
        writeText(outsidePath, "{}")

        assertThatThrownBy { reader().read(outsidePath.toString()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("base directory")
    }

    @Test
    fun `rejects a relative path that escapes the configured base directory`() {
        val outsidePath = Files.createTempFile(baseDir.parent, "outside-raw-response", ".json")
        writeText(outsidePath, "{}")

        assertThatThrownBy { reader().read("../${outsidePath.fileName}") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("base directory")
    }

    @Test
    fun `rejects a symlink that escapes the configured base directory`() {
        val outsidePath = Files.createTempFile(baseDir.parent, "outside-raw-response", ".json")
        val symlinkPath = baseDir.resolve("linked-response.json")
        writeText(outsidePath, "{}")
        Files.createSymbolicLink(symlinkPath, outsidePath)

        assertThatThrownBy { reader().read("linked-response.json") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("base directory")
    }

    @Test
    fun `rejects invalid JSON and damaged gzip files`() {
        val invalidJson = baseDir.resolve("invalid.json")
        val damagedGzip = baseDir.resolve("damaged.raw")
        writeText(invalidJson, "not-json")
        Files.createDirectories(damagedGzip.parent)
        Files.write(damagedGzip, byteArrayOf(0x1F, 0x8B.toByte(), 0x00))

        assertThatThrownBy { reader().read("invalid.json") }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { reader().read("damaged.raw") }.isInstanceOf(Exception::class.java)
    }

    private fun reader() = LocalFileRawResponseReader(ObjectMapper().findAndRegisterModules(), baseDir)

    private fun writeText(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun writeGzip(path: Path, content: String) {
        Files.createDirectories(path.parent)
        GZIPOutputStream(Files.newOutputStream(path)).use { gzip ->
            gzip.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }
}
