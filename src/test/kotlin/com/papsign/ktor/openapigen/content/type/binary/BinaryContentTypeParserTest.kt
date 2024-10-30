package com.papsign.ktor.openapigen.content.type.binary

import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import installOpenAPI
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.io.InputStream
import kotlin.random.Random
import kotlin.test.assertEquals

const val contentType = "image/png"

class BinaryContentTypeParserTest {


    @BinaryRequest([contentType])
    @BinaryResponse([contentType])
    data class Stream(val stream: InputStream)

    @Test
    fun testBinaryParsing() {
        val route = "/test"
        val bytes = Random.nextBytes(20)
        testApplication {
            application {
                installOpenAPI()
                apiRouting {
                    //(this.ktorRoute as Routing).trace { println(it.buildText()) }
                    route(route) {
                        post<Unit, Stream, Stream> { _, body ->
                            val actual = body.stream.readBytes()
                            assertArrayEquals(bytes, actual)
                            respond(Stream(actual.inputStream()))
                        }
                    }
                }
            }


            println("Test: Normal")
            client.post(route) {
                header(HttpHeaders.ContentType, contentType)
                header(HttpHeaders.Accept, contentType)
                setBody(bytes)
            }.let { response ->
                assertEquals(ContentType.parse(contentType), response.contentType())
                assertArrayEquals(bytes, response.readRawBytes())
            }

            println("Test: Missing Accept")
            client.post(route) {
                header(HttpHeaders.ContentType, contentType)
                setBody(bytes)
            }.let { response ->
                assertEquals(ContentType.parse(contentType), response.contentType())
                assertArrayEquals(bytes, response.readRawBytes())
            }

            println("Test: Missing Content-Type")
            client.post(route) {
                header(HttpHeaders.Accept, contentType)
                setBody(bytes)
            }.let { response ->
                assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            }

            println("Test: Bad Accept")
            client.post(route) {
                header(HttpHeaders.ContentType, contentType)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(bytes)
            }.let { response ->
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }

            println("Test: Bad Content-Type")
            client.post(route) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Accept, contentType)
                setBody(bytes)
            }.let { response ->
                assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            }
        }
    }
}
