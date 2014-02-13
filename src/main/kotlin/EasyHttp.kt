package org.easyHttp

import io.netty.bootstrap.Bootstrap
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.ChannelInitializer
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.logging.LogLevel
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.codec.http.HttpObject
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpContent
import io.netty.util.CharsetUtil
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponse
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.DefaultCookie
import io.netty.handler.codec.http.ClientCookieEncoder
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.DefaultFullHttpRequest
import java.net.URI
import java.net.URL
import io.netty.handler.codec.http.HttpRequest
import jet.Map

public class EasyHttp(private val enableLogging: Boolean = false,
                      val deserializers: List<ContentDeserializer> = listOf(JsonDeserializer()),
                      val serializers: List<ContentSerializer> = listOf(ApplicationWwwFormUrlEncodedSerializer())) {




    private fun setupBootStrap(eventLoopGroup: NioEventLoopGroup, callback: Response.() -> Unit): Bootstrap {
        val bootStrap = Bootstrap()
        bootStrap.group(eventLoopGroup)
                ?.channel(javaClass<NioSocketChannel>())
                ?.handler(HttpClientInitializer(enableLogging = false, useSSL = false, callback = callback, deserializers = deserializers))
        return bootStrap
    }

    fun executeRequest(url: String, headers: Headers, method: HttpMethod, callback: Response.() -> Unit, contents: Any? = null) {
        val eventLoopGroup = NioEventLoopGroup()
        try {
            val bootstrap = setupBootStrap(eventLoopGroup, callback)
            val uri = URI(url)
            val host = uri.getHost() ?: "localhost"
            var port = uri.getPort()
            if (port == -1) {
                port = 80;
            }
            val rawPath = URI(url).getRawPath()

            val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, rawPath)
            val requestHeaders = request.headers()!!
            requestHeaders.set(HttpHeaders.Names.HOST, host)
            requestHeaders.set(HttpHeaders.Names.ACCEPT, headers.accept)
            requestHeaders.set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
            requestHeaders.set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP)
            requestHeaders.set(HttpHeaders.Names.ACCEPT_CHARSET, headers.acceptCharSet)
            if (method == HttpMethod.POST || method == HttpMethod.PATCH) {
                requestHeaders.set(HttpHeaders.Names.CONTENT_TYPE, headers.contentType)
                val serializer = serializers.find { it.canSerialize(headers.contentType)}
                if (serializer != null) {
                    if (contents != null) {
                        // you know, we're not serializing. We're seriaizing and streaming. Rx would be a perfect
                        // example here for piping.
                    serializer.serialize(request, contents)

                    }
                } else
                    throw SerializationException("Cannot find serializer for content type")

            }
            requestHeaders.set(HttpHeaders.Names.FROM, headers.from)

            //   request.headers()!!.set(HttpHeaders.Names.COOKIE, ClientCookieEncoder.encode(DefaultCookie("my-cookie", "foo"), DefaultCookie("another-cookie", "bar")))
            val ch = bootstrap.connect(host, port)!!.sync()!!.channel()!!
            ch.write()
            ch.writeAndFlush(request)
            ch.closeFuture()?.sync()
        } finally {
            eventLoopGroup.shutdownGracefully()
        }
    }

    fun get(url: String, headers: Headers = Headers(), callback: Response.() -> Unit) {
        executeRequest(url, headers, HttpMethod.GET, callback)
    }

    fun post(url: String, headers: Headers = Headers(), contents: Any? = null, callback: Response.() -> Unit) {
        executeRequest(url, headers, HttpMethod.POST, callback, contents)
    }

    fun head(url: String, headers: Headers = Headers(), callback: Response.() -> Unit) {
        executeRequest(url, headers, HttpMethod.HEAD, callback)
    }
}

