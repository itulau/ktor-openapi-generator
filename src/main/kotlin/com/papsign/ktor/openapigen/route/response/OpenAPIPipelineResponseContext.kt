package com.papsign.ktor.openapigen.route.response

import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.getKType
import com.papsign.ktor.openapigen.modules.ofType
import com.papsign.ktor.openapigen.modules.providers.AuthProvider
import com.papsign.ktor.openapigen.modules.providers.StatusProvider
import com.papsign.ktor.openapigen.route.OpenAPIRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.RoutingContext
import kotlin.reflect.full.findAnnotation

interface Responder {
    suspend fun <TResponse: Any> respond(response: TResponse, request: RoutingContext)
    suspend fun <TResponse: Any> respond(statusCode: HttpStatusCode, response: TResponse, request: RoutingContext)
}

interface OpenAPIPipelineContext {
    val route: OpenAPIRoute<*>
    val pipeline: RoutingContext
    val responder: Responder
}

interface OpenAPIPipelineResponseContext<TResponse> : OpenAPIPipelineContext
interface OpenAPIPipelineAuthContext<TAuth, TResponse> : OpenAPIPipelineResponseContext<TResponse> {
    val authProvider: AuthProvider<TAuth>
}

class ResponseContextImpl<TResponse>(
        override val pipeline: RoutingContext,
        override val route: OpenAPIRoute<*>,
        override val responder: Responder
) : OpenAPIPipelineResponseContext<TResponse>

class AuthResponseContextImpl<TAuth, TResponse>(
        override val pipeline: RoutingContext,
        override val authProvider: AuthProvider<TAuth>,
        override val route: OpenAPIRoute<*>,
        override val responder: Responder
) : OpenAPIPipelineAuthContext<TAuth, TResponse>


suspend inline fun <reified TResponse : Any> OpenAPIPipelineResponseContext<TResponse>.respond(response: TResponse) {
    val statusCode = route.provider.ofType<StatusProvider>().lastOrNull()?.getStatusForType(getKType<TResponse>()) ?: TResponse::class.findAnnotation<Response>()?.statusCode?.let { HttpStatusCode.fromValue(it) } ?: HttpStatusCode.OK
    responder.respond(statusCode, response as Any, pipeline)
}
