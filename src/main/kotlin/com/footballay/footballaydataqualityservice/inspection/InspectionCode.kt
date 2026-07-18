package com.footballay.footballaydataqualityservice.inspection

/** Provider와 Endpoint 안에서 정의된 검사 코드의 안정적인 식별자를 표현한다. */
data class InspectionCode internal constructor(
    val provider: String,
    val endpoint: String,
    val value: String,
)

/** 동일 Provider와 Endpoint에 속한 검사 코드 정의의 중복을 차단한다. */
class InspectionCodeGroup(
    val provider: String,
    val endpoint: String,
) {
    private val registeredCodes = mutableSetOf<String>()

    init {
        require(CODE_PATTERN.matches(provider)) {
            "Inspection code provider must use uppercase snake case: $provider"
        }
        require(CODE_PATTERN.matches(endpoint)) {
            "Inspection code endpoint must use uppercase snake case: $endpoint"
        }
    }

    fun define(value: String): InspectionCode {
        require(CODE_PATTERN.matches(value)) {
            "Inspection code must use uppercase snake case: $value"
        }
        check(registeredCodes.add(value)) {
            "Duplicated inspection code: $provider:$endpoint:$value"
        }

        return InspectionCode(provider, endpoint, value)
    }

    private companion object {
        val CODE_PATTERN = Regex("[A-Z][A-Z0-9_]*")
    }
}
