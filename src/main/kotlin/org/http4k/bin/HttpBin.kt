package org.http4k.bin

import org.http4k.bin.Responses.getParameters
import org.http4k.bin.Responses.headerResponse
import org.http4k.bin.Responses.ip
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.cookie.invalidateCookie
import org.http4k.core.queries
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson.asJsonString
import org.http4k.format.Jackson.auto
import org.http4k.routing.by
import org.http4k.routing.path
import org.http4k.routing.routes

object Responses {
    val getParameters = Body.auto<GetParametersResponse>().toLens()
    val ip = Body.auto<IpResponse>().toLens()
    val headerResponse = Body.auto<HeaderResponse>().toLens()
}

fun HttpBin(): HttpHandler = routes(
    "/ip" to GET by { request: Request -> Response(OK).with(ip of request.ipResponse()) },
    "/get" to GET by { request: Request -> Response(OK).with(getParameters of request.getParametersResponse()) },
    "/headers" to GET by { request: Request -> Response(OK).with(headerResponse of request.headerResponse()) },
    "/basic-auth/{user}/{pass}" to GET by { request: Request ->
        val protectedHandler = ServerFilters.BasicAuth("http4k-bin", request.user(), request.password())
            .then(protectedResource(request.path("user").orEmpty()))
        protectedHandler(request)
    },
    "/cookies/set" to GET by { request ->
        request.uri.queries()
            .fold(redirectionResponseTo("/cookies"),
                { response, cookie -> response.cookie(Cookie(cookie.first, cookie.second.orEmpty())) })
    },
    "/cookies/delete" to GET by { request ->
        request.uri.queries()
            .fold(redirectionResponseTo("/cookies"),
                { response, cookie -> response.invalidateCookie(cookie.first) })
    },
    "/cookies" to GET by { request -> Response(OK).json(request.cookieResponse()) },
    "/relative-redirect/{times:\\d+}" to GET by { request ->
        val counter = request.path("times")?.toInt() ?: 5
        redirectionResponseTo(if (counter > 1) "/relative-redirect/${counter - 1}" else "/get")
    }
)

private fun redirectionResponseTo(target: String) = Response(TEMPORARY_REDIRECT).header("location", target)

fun protectedResource(user: String): HttpHandler = { Response(OK).json(AuthorizationResponse(user)) }

private fun Request.headerResponse(): HeaderResponse = HeaderResponse(mapOf(*headers.toTypedArray()))

private fun Request.ipResponse() = IpResponse(headerValues("x-forwarded-for").joinToString(", "))

private fun Request.cookieResponse() = CookieResponse(cookies().map { it.name to it.value }.toMap())

private fun Request.getParametersResponse() = GetParametersResponse(uri.queries().map { it.first to it.second.orEmpty() }.toMap())

private fun Request.user() = path("user").orEmpty()

private fun Request.password() = path("pass").orEmpty()

data class IpResponse(val origin: String)

data class GetParametersResponse(val args: Map<String, String>)

data class HeaderResponse(val headers: Map<String, String?>)

data class AuthorizationResponse(val user: String, val authenticated: Boolean = true)

data class CookieResponse(val cookies: Map<String, String>)

private fun Response.json(value: Any): Response = body(value.asJsonString())
    .header("content-type", "application/json")
