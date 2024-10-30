package origo.booking

import TestServerWithJwtAuth.testServerWithJwtAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


internal class JwtAuthDocumentationGenerationTest {
    @Test
    fun testRequest() = testApplication {
        application {
            testServerWithJwtAuth()
        }
        val response = client.get("/openapi.json")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(
            body.contains(
                """"securitySchemes" : {
      "ThisIsSchemeName" : {
        "in" : "cookie",
        "name" : "ThisIsCookieName",
        "type" : "apiKey"
      },
      "jwtAuth" : {
        "bearerFormat" : "JWT",
        "scheme" : "bearer",
        "type" : "http"
      }
    }"""
            )
        )

        assertTrue(
            body.contains(
                """"security" : [ {
          "jwtAuth" : [ ],
          "ThisIsSchemeName" : [ ]
        }"""
            )
        )
    }
}
