package com.papsign.ktor.openapigen

import com.papsign.ktor.openapigen.annotations.Path
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.annotations.type.enum.StrictEnumParsing
import com.papsign.ktor.openapigen.exceptions.OpenAPIBadContentException
import com.papsign.ktor.openapigen.exceptions.OpenAPIRequiredFieldException
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@StrictEnumParsing
enum class StrictTestEnum {
    VALID,
    ALSO_VALID,
}

@Path("/")
data class NullableStrictEnumParams(@QueryParam("") val type: StrictTestEnum? = null)

@Path("/")
data class NonNullableStrictEnumParams(@QueryParam("") val type: StrictTestEnum)

class EnumStrictTestServer {

    companion object {
        // test server for nullable enums
        private fun Application.nullableEnum() {
            install(OpenAPIGen)
            install(StatusPages) {
                exception<OpenAPIBadContentException> { call, e ->
                    call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
                }
            }
            apiRouting {
                get<NullableStrictEnumParams, String> { params ->
                    if (params.type != null)
                        assertTrue { StrictTestEnum.values().contains(params.type) }
                    respond(params.type?.toString() ?: "null")
                }
            }
        }

        // test server for non-nullable enums
        private fun Application.nonNullableEnum() {
            install(OpenAPIGen)
            install(StatusPages) {
                exception<OpenAPIRequiredFieldException> { call, e ->
                    call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
                }
                exception<OpenAPIBadContentException> { call, e ->
                    call.respond(HttpStatusCode.BadRequest, e.localizedMessage)
                }
            }
            apiRouting {
                get<NonNullableStrictEnumParams, String> { params ->
                    assertTrue { StrictTestEnum.values().contains(params.type) }
                    respond(params.type.toString())
                }
            }
        }
    }

    @Test
    fun `nullable enum could be omitted and it will be null`() = testApplication {
        application {
            nullableEnum()
        }

        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("null", bodyAsText())
        }
    }

    @Test
    fun `nullable enum should be parsed correctly`() = testApplication {
        application {
            nullableEnum()
        }

        client.get("/?type=VALID").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("VALID", bodyAsText())
        }
        client.get("/?type=ALSO_VALID").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("ALSO_VALID", bodyAsText())
        }
    }

    @Test
    fun `nullable enum parsing should be case-sensitive and should throw on passing wrong case`() = testApplication {
        application {
            nullableEnum()
        }

        client.get("/?type=valid").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("Invalid value [valid] for enum parameter of type StrictTestEnum. Expected: [VALID,ALSO_VALID]", bodyAsText())
        }
        client.get("/?type=also_valid").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("Invalid value [also_valid] for enum parameter of type StrictTestEnum. Expected: [VALID,ALSO_VALID]", bodyAsText())
        }
    }

    @Test
    fun `nullable enum parsing should not parse values outside of enum`() = testApplication {
        application {
            nullableEnum()
        }

        client.get("/?type=what").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("Invalid value [what] for enum parameter of type StrictTestEnum. Expected: [VALID,ALSO_VALID]", bodyAsText())
        }
    }

    @Test
    fun `non-nullable enum cannot be omitted`() = testApplication {
        application {
            nonNullableEnum()
        }

        client.get("/").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("The field type is required", bodyAsText())
        }
    }

    @Test
    fun `non-nullable enum should be parsed correctly`() = testApplication {
        application {
            nonNullableEnum()
        }

        client.get("/?type=VALID").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("VALID", bodyAsText())
        }

        client.get("/?type=ALSO_VALID").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("ALSO_VALID", bodyAsText())
        }
    }

    @Test
    fun `non-nullable enum parsing should be case-sensitive and should throw on passing wrong case`() = testApplication {
        application {
            nonNullableEnum()
        }

        client.get("/?type=valid").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("Invalid value [valid] for enum parameter of type StrictTestEnum. Expected: [VALID,ALSO_VALID]", bodyAsText())
        }
        client.get("/?type=also_valid").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("Invalid value [also_valid] for enum parameter of type StrictTestEnum. Expected: [VALID,ALSO_VALID]", bodyAsText())
        }
    }

    @Test
    fun `non-nullable enum parsing should not parse values outside of enum`() = testApplication {
        application {
            nonNullableEnum()
        }

        client.get("/?type=what").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("Invalid value [what] for enum parameter of type StrictTestEnum. Expected: [VALID,ALSO_VALID]", bodyAsText())
        }
    }
}
