package com.footballay.dataquality.inspection

/** Inspector 내부에서 정의하는 로컬 검사 규칙 식별자다. */
interface InspectionRuleCode {
    val value: String
}

/** Inspector 내부 규칙 코드가 합성 검사 코드에 사용 가능한 형식인지 검증한다. */
fun validateInspectionRuleCode(value: String) {
    require(UPPER_SNAKE_CASE.matches(value)) {
        "Inspection rule code must use uppercase snake case: $value"
    }
}

private val UPPER_SNAKE_CASE = Regex("[A-Z][A-Z0-9_]*")
