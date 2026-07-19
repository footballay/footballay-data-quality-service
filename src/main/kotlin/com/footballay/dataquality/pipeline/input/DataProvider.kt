package com.footballay.dataquality.pipeline.input

import com.fasterxml.jackson.annotation.JsonCreator
import java.util.Locale

enum class DataProvider {
    API_SPORTS,
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromWireValue(value: String): DataProvider {
            val normalizedValue = value.normalize()
            return entries.singleOrNull { it.name.normalize() == normalizedValue }
                ?: throw IllegalArgumentException("Unsupported data provider: $value")
        }

        private fun String.normalize(): String =
            filterNot { it.isWhitespace() || it == '-' || it == '_' }.lowercase(Locale.ROOT)
    }
}
