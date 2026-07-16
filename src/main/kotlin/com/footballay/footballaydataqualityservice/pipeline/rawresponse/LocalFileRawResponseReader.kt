package com.footballay.footballaydataqualityservice.pipeline.rawresponse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

class LocalFileRawResponseReader(
    private val objectMapper: ObjectMapper,
    baseDir: Path,
) : RawResponseReader {
    private val baseDir = baseDir.toRealPath()

    init {
        require(Files.isDirectory(baseDir)) {
            "Local raw response base directory must be a directory: $baseDir"
        }
    }

    override fun read(rawJsonObjectKey: String): JsonNode {
        require(rawJsonObjectKey.isNotBlank()) {
            "rawJsonObjectKey must not be blank"
        }

        val requestedPath = Path.of(rawJsonObjectKey)
        val candidatePath = if (requestedPath.isAbsolute) requestedPath else baseDir.resolve(requestedPath)
        val objectPath = candidatePath.toRealPath()

        require(objectPath.startsWith(baseDir)) {
            "rawJsonObjectKey must be under local storage base directory"
        }

        return Files.newInputStream(objectPath).use { input ->
            BufferedInputStream(input).use { bufferedInput ->
                if (isGzip(bufferedInput)) {
                    GZIPInputStream(bufferedInput).use(::readJson)
                } else {
                    readJson(bufferedInput)
                }
            }
        }
    }

    private fun isGzip(input: BufferedInputStream): Boolean {
        input.mark(GZIP_MAGIC_LENGTH)
        val firstByte = input.read()
        val secondByte = input.read()
        input.reset()
        return firstByte == GZIP_MAGIC_FIRST_BYTE && secondByte == GZIP_MAGIC_SECOND_BYTE
    }

    private fun readJson(input: java.io.InputStream): JsonNode =
        objectMapper.readTree(input) ?: throw IOException("Raw response file is empty")

    private companion object {
        const val GZIP_MAGIC_LENGTH = 2
        const val GZIP_MAGIC_FIRST_BYTE = 0x1F
        const val GZIP_MAGIC_SECOND_BYTE = 0x8B
    }
}
