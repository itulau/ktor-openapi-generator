package com.papsign.ktor.openapigen.exceptions

import kotlin.reflect.KType

class OpenAPIParseException(
    val field: String,
    val content: String,
    val type: KType,
    cause: Throwable? = null
) : Exception("Could not parse field $field with value '$content'", cause)
