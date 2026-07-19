package com.footballay.dataquality.inspection

/** Inspector가 발견한 규칙 집합을 안정적으로 식별하는 이름이다. */
@JvmInline
value class InspectionName private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): InspectionName {
            require(UPPER_SNAKE_CASE.matches(value)) {
                "Inspection name must use uppercase snake case: $value"
            }
            return InspectionName(value)
        }

        private val UPPER_SNAKE_CASE = Regex("[A-Z][A-Z0-9_]*")
    }
}
