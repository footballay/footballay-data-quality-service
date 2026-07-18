package com.footballay.footballaydataqualityservice.inspection

/** Inspector 내부에서 정의하는 로컬 검사 규칙 식별자다. */
interface InspectionRuleCode {
    val value: String
}

/** Inspector가 장기간 유지할 논리적인 검사 영역 식별자다. */
interface InspectionScope {
    val value: String
}

internal fun validateInspectionIdentifier(
    type: String,
    value: String,
) {
    require(IDENTIFIER_PATTERN.matches(value)) {
        "$type must use uppercase snake case: $value"
    }
}

private val IDENTIFIER_PATTERN = Regex("[A-Z][A-Z0-9_]*")
