package com.henrycourse.jetty

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EngineSSLConnectorConfig
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http3.server.HTTP3ServerConnectionFactory
import org.eclipse.jetty.http3.server.HTTP3ServerConnector
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.nio.file.Paths

internal fun Server.initializeServer(configuration: Jetty12ApplicationEngineBase.Configuration) {

    configuration.connectors.forEach { ktorConnector ->

        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
            sendDateHeader = false
        }

        when (ktorConnector.type) {

            ConnectorType.HTTP -> {

                ServerConnector(this, HttpConnectionFactory(httpConfig)).apply {
                    port = ktorConnector.port
                    host = ktorConnector.host
                    server.addConnector(this)
                }
            }

            ConnectorType.HTTPS -> {

                httpConfig.addCustomizer(SecureRequestCustomizer())
                val sslContextFactory = SslContextFactory.Server().apply {

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
                }

                val http = HttpConnectionFactory(httpConfig)
                val ssl = SslConnectionFactory(sslContextFactory, http.protocol)
                val alpn = ALPNServerConnectionFactory(http.protocol.toString()).apply {
                    defaultProtocol = http.protocol
                }

                ServerConnector(server, 1, 1, ssl, alpn, http).apply {
                    port = ktorConnector.port
                    host = ktorConnector.host
                    server.addConnector(this)
                }

                HTTP3ServerConnector(server, sslContextFactory, HTTP3ServerConnectionFactory(httpConfig)).apply {
                    quicConfiguration.pemWorkDirectory = Paths.get(System.getProperty("java.io.tmpdir"))
                    port = ktorConnector.port
                    host = ktorConnector.host
                    server.addConnector(this)
                }
            }

            else -> throw IllegalArgumentException(
                "Connector type ${ktorConnector.type} is not supported by Jetty engine implementation"
            )
        }
    }
}
