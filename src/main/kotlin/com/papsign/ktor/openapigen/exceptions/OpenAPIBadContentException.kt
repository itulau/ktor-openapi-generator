package com.papsign.ktor.openapigen.exceptions

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class OpenAPIBadContentException(msg: String): Exception(msg)

@OptIn(ExperimentalContracts::class)
inline fun assertContent(value: Boolean, crossinline err: () -> String) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw OpenAPIBadContentException(err())
    }
}