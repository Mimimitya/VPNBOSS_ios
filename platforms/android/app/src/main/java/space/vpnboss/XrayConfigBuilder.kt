package space.vpnboss

import android.net.Uri
import com.github.tfox.flutter_vless.xray.dto.XrayConfig
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {
    data class Endpoint(val host: String, val port: Int)

    fun endpoint(link: String): Endpoint {
        val uri = Uri.parse(link)
        return Endpoint(uri.host ?: error("В конфигурации отсутствует адрес сервера"), uri.port.takeIf { it > 0 } ?: 443)
    }

    fun build(link: String, remark: String): XrayConfig {
        val uri = Uri.parse(link)
        val endpoint = endpoint(link)
        val uuid = uri.userInfo?.substringBefore(':').orEmpty()
        require(uuid.isNotBlank()) { "В конфигурации отсутствует UUID" }

        val security = uri.getQueryParameter("security") ?: "reality"
        val network = uri.getQueryParameter("type") ?: "tcp"
        val flow = uri.getQueryParameter("flow") ?: "xtls-rprx-vision"
        val user = JSONObject()
            .put("id", uuid)
            .put("encryption", uri.getQueryParameter("encryption") ?: "none")
            .put("flow", flow)
            .put("level", 8)
        val vnext = JSONObject()
            .put("address", endpoint.host)
            .put("port", endpoint.port)
            .put("users", JSONArray().put(user))

        val stream = JSONObject()
            .put("network", network)
            .put("security", security)
        if (security == "reality") {
            val reality = JSONObject()
                .put("serverName", uri.getQueryParameter("sni") ?: endpoint.host)
                .put("fingerprint", uri.getQueryParameter("fp") ?: "chrome")
                .put("publicKey", uri.getQueryParameter("pbk") ?: uri.getQueryParameter("pbK") ?: "")
                .put("shortId", uri.getQueryParameter("sid") ?: "")
                .put("spiderX", uri.getQueryParameter("spx") ?: "/")
                .put("show", false)
            uri.getQueryParameter("alpn")?.takeIf(String::isNotBlank)?.let {
                reality.put("alpn", JSONArray(it.split(',').map(String::trim)))
            }
            stream.put("realitySettings", reality)
        } else if (security == "tls") {
            stream.put("tlsSettings", JSONObject()
                .put("serverName", uri.getQueryParameter("sni") ?: endpoint.host)
                .put("fingerprint", uri.getQueryParameter("fp") ?: "chrome"))
        }
        when (network.lowercase()) {
            "ws" -> stream.put("wsSettings", JSONObject()
                .put("path", uri.getQueryParameter("path") ?: "/")
                .put("headers", JSONObject().put("Host", uri.getQueryParameter("host") ?: "")))
            "grpc" -> stream.put("grpcSettings", JSONObject()
                .put("serviceName", uri.getQueryParameter("serviceName") ?: ""))
            "xhttp" -> stream.put("xhttpSettings", JSONObject()
                .put("path", uri.getQueryParameter("path") ?: "/")
                .put("host", uri.getQueryParameter("host") ?: ""))
            "tcp" -> uri.getQueryParameter("headerType")?.takeIf(String::isNotBlank)?.let {
                stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", it)))
            }
        }

        val proxy = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
        val direct = JSONObject().put("tag", "direct").put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))
        val block = JSONObject().put("tag", "block").put("protocol", "blackhole")
        val config = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
            .put("outbounds", JSONArray().put(proxy).put(direct).put(block))
            .put("routing", JSONObject()
                .put("domainStrategy", "IPIfNonMatch")
                .put("rules", JSONArray()
                    .put(JSONObject()
                        .put("type", "field")
                        .put("ip", JSONArray().put("geoip:private").put("127.0.0.0/8"))
                        .put("outboundTag", "direct"))
                    .put(JSONObject()
                        .put("type", "field")
                        .put("network", "udp")
                        .put("port", "443")
                        .put("outboundTag", "block"))))

        return XrayConfig(
            CONNECTED_V2RAY_SERVER_ADDRESS = endpoint.host,
            CONNECTED_V2RAY_SERVER_PORT = endpoint.port.toString(),
            V2RAY_FULL_JSON_CONFIG = config.toString(),
            REMARK = remark,
            APPLICATION_NAME = "VPNBOSS",
            APPLICATION_ICON = R.mipmap.ic_launcher,
            NOTIFICATION_ICON_RESOURCE_NAME = "ic_launcher",
            NOTIFICATION_ICON_RESOURCE_TYPE = "mipmap",
            NOTIFICATION_DISCONNECT_BUTTON_NAME = "Отключить",
            ENABLE_TRAFFIC_STATICS = true,
        )
    }
}
