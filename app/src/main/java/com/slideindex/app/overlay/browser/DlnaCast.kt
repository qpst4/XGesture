package com.slideindex.app.overlay.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

/**
 * DLNA/UPnP 投屏（不依赖 Google Cast SDK）：
 * SSDP 发现局域网内的 MediaRenderer → 读设备描述拿 AVTransport 控制地址
 * → SOAP `SetAVTransportURI` + `Play`，让电视直接拉视频地址播放。
 */
internal object DlnaCast {

    data class Renderer(val name: String, val controlUrl: String)

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900

    /** 发现局域网内的可投屏设备（约 3 秒）。 */
    suspend fun discover(timeoutMs: Long = 3000): List<Renderer> = withContext(Dispatchers.IO) {
        val locations = linkedSetOf<String>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 800
                val message = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n")
                }.toByteArray()

                val group = InetAddress.getByName(SSDP_ADDRESS)
                repeat(2) {
                    socket.send(
                        DatagramPacket(message, message.size, InetSocketAddress(group, SSDP_PORT)),
                    )
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching { socket.receive(packet) }.getOrNull() ?: continue
                    val text = String(packet.data, 0, packet.length)
                    text.lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                        ?.let { if (it.startsWith("http")) locations.add(it) }
                }
            }
        }

        locations.mapNotNull { location -> runCatching { describe(location) }.getOrNull() }
            .distinctBy { it.controlUrl }
    }

    private fun describe(location: String): Renderer? {
        val xml = (URL(location).openConnection() as java.net.HttpURLConnection).run {
            connectTimeout = 3000
            readTimeout = 3000
            inputStream.bufferedReader().use { it.readText() }
        }
        val name = Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val controlPath = Regex(
            "<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>[\\s\\S]*?<controlURL>(.*?)</controlURL>",
            RegexOption.DOT_MATCHES_ALL,
        ).find(xml)?.groupValues?.getOrNull(1)?.trim()
        if (controlPath.isNullOrBlank()) return null
        val base = location.substringBefore("/", location)
            .let { location.substring(0, location.indexOf("/", location.indexOf("//") + 2).coerceAtLeast(0)) }
        val control = if (controlPath.startsWith("http")) controlPath else base + (if (controlPath.startsWith("/")) "" else "/") + controlPath
        return Renderer(name.ifBlank { "DLNA 设备" }, control)
    }

    /** 让设备播放指定地址。 */
    suspend fun play(renderer: Renderer, videoUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            soap(
                renderer.controlUrl,
                """
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>${escapeXml(videoUrl)}</CurrentURI>
                  <CurrentURIMetaData></CurrentURIMetaData>
                </u:SetAVTransportURI>
                """.trimIndent(),
            )
            soap(
                renderer.controlUrl,
                """
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <Speed>1</Speed>
                </u:Play>
                """.trimIndent(),
            )
        }
    }

    private fun soap(controlUrl: String, body: String) {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>$body</s:Body>
            </s:Envelope>
        """.trimIndent()
        val action = Regex("<u:(\\w+)").find(body)?.groupValues?.getOrNull(1).orEmpty()
        val conn = (URL(controlUrl).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 4000
            readTimeout = 6000
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
        }
        conn.outputStream.use { it.write(envelope.toByteArray()) }
        runCatching { conn.inputStream.close() }
        runCatching { conn.errorStream?.close() }
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
