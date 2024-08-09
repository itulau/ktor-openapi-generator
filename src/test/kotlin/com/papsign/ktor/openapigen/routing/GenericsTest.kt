package com.papsign.ktor.openapigen.routing

import com.papsign.ktor.openapigen.annotations.parameters.HeaderParam
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import installJackson
import installOpenAPI
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.routing.Routing
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericsTest {

    data class TestHeaderParams(@HeaderParam("test param") val `Test-Header`: MutableList<Long>)

    @Test
    fun testTypedMap() {
        val route = "/test"
        withTestApplication({
            installOpenAPI()
            installJackson()
            apiRouting {
                (this.ktorRoute as Routing).trace { println(it.buildText()) }
                route(route) {
                    post<TestHeaderParams, List<String>, Map<String, String>> { params, body ->
                        respond(mutableListOf(params.toString(), body.toString()))
                    }
                }
            }
        }) {
            handleRequest(HttpMethod.Post, route) {
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.Accept, "application/json")
                addHeader("Test-Header", "1,2,3")
                setBody("{\"xyz\":456}")
            }.apply {
                assertTrue { response.contentType().match("application/json") }
                assertEquals(
                    "[\"TestHeaderParams(Test-Header=[1, 2, 3])\",\"{xyz=456}\"]",
                    response.content
                )
            }
        }
    }

    @Test
    fun testTypedList() {
        val route = "/test"
        withTestApplication({
            installOpenAPI()
            installJackson()
            apiRouting {
                (this.ktorRoute as Routing).trace { println(it.buildText()) }
                route(route) {
                    post<TestHeaderParams, List<String>, List<String>> { params, body ->
                        respond(mutableListOf(params.toString(), body.toString()))
                    }
                }
            }
        }) {
            handleRequest(HttpMethod.Post, route) {
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.Accept, "application/json")
                addHeader("Test-Header", "1,2,3")
                setBody("[\"a\",\"b\",\"c\"]")
            }.apply {
                assertTrue { response.contentType().match("application/json") }
                assertEquals(
                    "[\"TestHeaderParams(Test-Header=[1, 2, 3])\",\"[a, b, c]\"]",
                    response.content
                )
            }
        }
    }

    @Test
    fun testTypedListSubclass() {
        val route = "/test"
        testApplication {
            application {
                installOpenAPI()
                installJackson()
                apiRouting {
                    (this.ktorRoute as Routing).trace { println(it.buildText()) }
                    route(route) {
                        post<Unit, StringList, StringList> { params, body ->
                            respond(body)
                        }
                    }
                }
            }

            client.post(route) {
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, "application/json")
                setBody("""["a","b","c"]""")
            }.also { response ->
                assertEquals(
                    """["a","b","c"]""",
                    response.bodyAsText()
                )
            }
        }
    }

    class StringList(private val list: List<String>): List<String> by list
}
