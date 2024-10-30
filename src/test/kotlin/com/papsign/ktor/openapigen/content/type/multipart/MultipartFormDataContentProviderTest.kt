package com.papsign.ktor.openapigen.content.type.multipart

import com.papsign.ktor.openapigen.isValue
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import installJackson
import installOpenAPI
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals

class MultipartFormDataContentProviderTest {

    @JvmInline
    value class IntValue(val value: Int)

    @JvmInline
    value class UuidValue(val uuid: UUID)

    @FormDataRequest
    data class SimpleRequest(
        val str: String,
        val int: Int,
        val flt: Float,
        val bl: Boolean,
        val strn: String?,
        val intn: Int?,
        val fltn: Float?,
        val bln: Boolean?,
        val uuid: UUID?,
        val intValue: IntValue?,
        val uuidValue: UuidValue?,
        val localDate: LocalDate?,
        val localTime: LocalTime?,
        val offsetTime: OffsetTime?,
        val localDateTime: LocalDateTime?,
        val offsetDateTime: OffsetDateTime?,
        val zonedDateTime: ZonedDateTime?,
        val instant: Instant?,
    ) {
        fun toParts(): List<PartData> {
            return this::class.declaredMemberProperties.mapNotNull {
                val prop = it as KProperty1<SimpleRequest, Any?>
                var res = prop.get(this) ?: return@mapNotNull null
                if (it.returnType.isValue) {
                    val valueProp = res.javaClass.kotlin.memberProperties.first()
                    res = valueProp.get(res) ?: return@mapNotNull null
                }

                PartData.FormItem(
                    res.toString(),
                    { },
                    headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Inline
                            .withParameter(ContentDisposition.Parameters.Name, it.name)
                            .toString()
                    )
                )
            }
        }
    }


    @Test
    fun testMultipartParsing() {
        val requests = mapOf(
            "/1" to SimpleRequest(
                str = "Test",
                int = 300,
                flt = 26.95f,
                bl = true,
                strn = null,
                intn = null,
                fltn = null,
                bln = null,
                intValue = IntValue(300),
                uuidValue = UuidValue(UUID.fromString("b6c903d1-efdb-47a9-9e17-463b2a7b1011")),
                uuid = UUID.fromString("b6c903d1-efdb-47a9-9e17-463b2a7b1011"),
                localDate = LocalDate.of(2024, 1, 1),
                localTime = LocalTime.of(10, 0),
                offsetTime = OffsetTime.of(LocalTime.of(10, 0), ZoneOffset.ofHours(2)),
                localDateTime = LocalDateTime.parse("2021-02-27T10:30:00"),
                offsetDateTime = OffsetDateTime.parse("2021-02-27T10:30:00+02:00"),
                zonedDateTime = ZonedDateTime.parse("2021-02-27T10:30:00+02:00[Europe/Berlin]"),
                instant = Instant.parse("2021-02-27T10:30:00Z"),
            ).let { Pair(it, it.toParts()) },
            "/2" to SimpleRequest(
                str = "Test",
                int = 300,
                flt = 26.95f,
                bl = true,
                strn = "Test",
                intn = 300,
                fltn = 26.95f,
                bln = true,
                intValue = IntValue(300),
                uuidValue = UuidValue(UUID.fromString("b6c903d1-efdb-47a9-9e17-463b2a7b1011")),
                uuid = UUID.fromString("b6c903d1-efdb-47a9-9e17-463b2a7b1011"),
                localDate = LocalDate.of(2024, 1, 1),
                localTime = LocalTime.of(10, 0),
                offsetTime = OffsetTime.of(LocalTime.of(10, 0), ZoneOffset.UTC),
                localDateTime = LocalDateTime.parse("2021-02-27T10:30:00"),
                offsetDateTime = OffsetDateTime.parse("2021-02-27T10:30:00Z"),
                zonedDateTime = ZonedDateTime.parse("2021-02-27T10:30:00+02:00"),
                instant = Instant.parse("2021-02-27T10:30:00Z"),
            ).let { Pair(it, it.toParts()) },
            "/3" to Pair(
                SimpleRequest(
                    str = "",
                    int = 0,
                    flt = 0f,
                    bl = false,
                    strn = null,
                    intn = null,
                    fltn = null,
                    bln = null,
                    intValue = null,
                    uuidValue = null,
                    uuid = null,
                    localDate = null,
                    localTime = null,
                    offsetTime = null,
                    localDateTime = null,
                    offsetDateTime = null,
                    zonedDateTime = null,
                    instant = null,
                ),
                listOf(
                    PartData.FormItem(
                        "yolo", { }, headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Inline
                                .withParameter(ContentDisposition.Parameters.Name, "yolo")
                                .toString()
                        )
                    )
                )
            )
        )
        testApplication {
            application {
                installOpenAPI()
                installJackson()
                apiRouting {
                    requests.forEach { (t, u) ->
                        route(t) {
                            post<Unit, Boolean, SimpleRequest> { _, body ->
                                assertEquals(u.first, body)
                                respond(true)
                            }
                        }
                    }
                }
            }
            requests.forEach { (t, u) ->
                println("Test: $t")

                client.post(t) {
                    val boundary = "***bbb***"
                    header(
                        HttpHeaders.ContentType,
                        ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
                    )

                    setBody(
                        MultiPartFormDataContent(
                            boundary = boundary,
                            parts = u.second,
                        )
                    )
                }.let { response ->
                    assertEquals(true, response.bodyAsText().toBoolean())
                }
                println("Test: $t success")
            }
        }
    }
}
