import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.properties.description.Description
import com.papsign.ktor.openapigen.content.type.multipart.FormDataRequest
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.schema.namer.DefaultSchemaNamer
import com.papsign.ktor.openapigen.schema.namer.SchemaNamer
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import kotlin.reflect.KType

object ValueClassServer {
    @JvmInline
    @Description("Product ID")
    value class ProductId(val value: UUID = UUID.randomUUID())

    @JvmInline
    value class Currency(val value: Long)

    @Description("Our product")
    data class Product(
        val id: ProductId = ProductId(),
        val name: String,
        val price: Currency,
        val similarProducts: List<Product> = emptyList(),
    )

    data class ProductQuery(
        @PathParam("Product ID") val id: ProductId,
    )


    @FormDataRequest
    data class ProductQueryFormData(
        val id: ProductId,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(Netty, 8080, "localhost") {
            //define basic OpenAPI info
            install(OpenAPIGen) {
                // basic info
                info {
                    version = "0.0.1"
                    title = "Test API"
                    description = "The Test API"
                    contact {
                        name = "Support"
                        email = "support@test.com"
                    }
                }
                // describe the server, add as many as you want
                server("http://localhost:8080/") {
                    description = "Test server"
                }
                //optional
                replaceModule(DefaultSchemaNamer, object : SchemaNamer {
                    val regex = Regex("[A-Za-z0-9_.]+")
                    override fun get(type: KType): String {
                        return type.toString().replace(regex) { it.value.split(".").last() }
                            .replace(Regex(">|<|, "), "_")
                    }
                })
            }

            install(ContentNegotiation) {
                jackson()
            }

            // normal Ktor routing
            routing {
                get("/") {
                    call.respondRedirect("/swagger-ui/index.html", false)
                }
            }

            //Described routing
            apiRouting {
                route("products") {
                    val products = listOf(
                        Product(
                            name = "Product #1",
                            price = Currency(100),
                            similarProducts = listOf(
                                Product(
                                    name = "Product #3",
                                    price = Currency(500),
                                ),
                            )
                        ),
                        Product(
                            name = "Product #2",
                            price = Currency(150),
                        ),
                    )

                    route("/product") {
                        post<Unit, Product, ProductQueryFormData>() { _, body ->
                            val product = products.find { it.id == body.id }
                            if (product != null) {
                                respond(product)
                            } else {
                                pipeline.call.respond(HttpStatusCode.NotFound, "Product not found")
                            }
                        }
                    }

                    route("{id}") {
                        get<ProductQuery, Product>(
                            info("Products", "List of products."),
                        ) { params ->
                            val product = products.find { it.id == params.id }
                            if (product != null) {
                                respond(product)
                            } else {
                                pipeline.call.respond(HttpStatusCode.NotFound, "Product not found")
                            }
                        }
                    }

                    get<Unit, List<Product>>(
                        info("Products", "List of products."),
                    ) {
                        respond(products)
                    }
                }
            }
        }.start(true)
    }
}