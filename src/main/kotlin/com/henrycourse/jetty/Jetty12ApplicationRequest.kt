package com.henrycourse.jetty

import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.parseQueryString
import io.ktor.server.application.PipelineCall
import io.ktor.server.engine.BaseApplicationRequest
import io.ktor.server.request.RequestCookies
import io.ktor.server.request.encodeParameters
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import org.eclipse.jetty.io.Content
import org.eclipse.jetty.server.Request

class Jetty12RequestCookies(
    request: Jetty12ApplicationRequest,
    private val jettyRequest: Request
) : RequestCookies(request) {

    override fun fetchCookies(): Map<String, String> {
        return Request.getCookies(jettyRequest).associate { it.name to it.value }
    }
}

class Jetty12Headers(
    private val jettyRequest: Request
) : Headers {

    override val caseInsensitiveName: Boolean = true

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return jettyRequest.headers.fieldNamesCollection.map {
            object : Map.Entry<String, List<String>> {
                override val key: String = it
                override val value: List<String> = jettyRequest.headers.getValuesList(it)
            }
        }.toSet()
    }

    override fun getAll(name: String): List<String>? = jettyRequest.headers.getValuesList(name)

    override fun get(name: String): String? = jettyRequest.headers.get(name)

    override fun isEmpty(): Boolean = jettyRequest.headers.size() == 0

    override fun names(): Set<String> = jettyRequest.headers.fieldNamesCollection
}

class Jetty12ConnectionPoint(
    request: Request
) : RequestConnectionPoint {

    @Deprecated("Use localHost or serverHost instead")
    override val host: String = request.httpURI.host

    override val localAddress: String = Request.getLocalAddr(request)

    override val localHost: String = Request.getServerName(request)

    override val localPort: Int = Request.getLocalPort(request)

    override val method: HttpMethod = HttpMethod.parse(request.method)

    @Deprecated("Use localPort or serverPort instead", level = DeprecationLevel.ERROR)
    override val port = -1

    override val remoteAddress: String = Request.getRemoteAddr(request)

    override val remoteHost: String = Request.getServerName(request)

    override val remotePort: Int = Request.getRemotePort(request)

    override val scheme: String = request.connectionMetaData.protocol

    override val serverHost: String = Request.getServerName(request)

    override val serverPort: Int = Request.getRemotePort(request)

    override val uri: String = request.httpURI.pathQuery

    override val version: String = request.connectionMetaData.httpVersion.asString()
}

@Suppress("KDocMissingDocumentation")
class Jetty12ApplicationRequest(
    call: PipelineCall,
    val request: Request,
) : BaseApplicationRequest(call) {

    override val cookies: RequestCookies = Jetty12RequestCookies(this, request)

    override val engineHeaders: Headers = Jetty12Headers(request)

    override val engineReceiveChannel: ByteReadChannel = Content.Source.asInputStream(request).toByteReadChannel()

    override val local: RequestConnectionPoint = Jetty12ConnectionPoint(request)

    override val queryParameters: Parameters by lazy { encodeParameters(rawQueryParameters) }

    override val rawQueryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        val uri = request.httpURI.query ?: return@lazy Parameters.Empty
        parseQueryString(uri, decode = false)
    }
}