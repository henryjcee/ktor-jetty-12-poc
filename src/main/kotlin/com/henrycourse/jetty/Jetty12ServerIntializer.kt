package com.henrycourse.jetty

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EngineSSLConnectorConfig
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.http2.HTTP2Cipher
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.http3.server.HTTP3ServerConnectionFactory
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.util.ssl.SslContextFactory

// I think there's a lot of dodgy stuff in here and it's a bit of a mess.
internal fun Server.initializeServer(configuration: Jetty12ApplicationEngineBase.Configuration) {

    configuration.connectors.map { ktorConnector ->
        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
            sendDateHeader = false

            if (ktorConnector.type == ConnectorType.HTTPS) {
                addCustomizer(SecureRequestCustomizer())
            }
        }

        var alpnAvailable = false
        var alpnConnectionFactory: ALPNServerConnectionFactory?
        var http2ConnectionFactory: HTTP2ServerConnectionFactory?
        var http3ConnectionFactory: HTTP3ServerConnectionFactory?

        try {
            alpnConnectionFactory = ALPNServerConnectionFactory().apply {
                defaultProtocol = HttpVersion.HTTP_1_1.asString()
            }
            http2ConnectionFactory = HTTP2ServerConnectionFactory(httpConfig)
            http3ConnectionFactory = HTTP3ServerConnectionFactory(httpConfig)
            alpnAvailable = true
        } catch (t: Throwable) {
            // ALPN or HTTP/2 implemented is not available
            alpnConnectionFactory = null
            http2ConnectionFactory = null
            http3ConnectionFactory = null
        }

        val connectionFactories = when (ktorConnector.type) {
            ConnectorType.HTTP -> arrayOf(HttpConnectionFactory(httpConfig))
            ConnectorType.HTTPS -> arrayOf(
                SslConnectionFactory(
                    SslContextFactory.Server().apply {
                        if (alpnAvailable) {
                            cipherComparator = HTTP2Cipher.COMPARATOR
                            isUseCipherSuitesOrder = true
                        }

                        keyStore = (ktorConnector as EngineSSLConnectorConfig).keyStore
                        keyManagerPassword = String(ktorConnector.privateKeyPassword())
                        keyStorePassword = String(ktorConnector.keyStorePassword())

                        needClientAuth = when {
                            ktorConnector.trustStore != null -> {
                                trustStore = ktorConnector.trustStore
                                true
                            }
                            ktorConnector.trustStorePath != null -> {
                                trustStorePath = ktorConnector.trustStorePath!!.absolutePath
                                true
                            }
                            else -> false
                        }

                        ktorConnector.enabledProtocols?.let {
                            setIncludeProtocols(*it.toTypedArray())
                        }

                        addExcludeCipherSuites(
                            "SSL_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
                        )
                    },
                    if (alpnAvailable) "alpn" else HttpVersion.HTTP_1_1.asString()
                ),
                alpnConnectionFactory,
                http2ConnectionFactory ?: HTTP2CServerConnectionFactory(httpConfig),
                http3ConnectionFactory,
                HttpConnectionFactory(httpConfig)
            ).filterNotNull().toTypedArray()
            else -> throw IllegalArgumentException(
                "Connector type ${ktorConnector.type} is not supported by Jetty engine implementation"
            )
        }

        ServerConnector(this, *connectionFactories).apply {
            host = ktorConnector.host
            port = ktorConnector.port
        }
    }.forEach { this.addConnector(it) }
}
