package space.vpnboss

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthInit(val webToken: String, val link: String)
data class RouteItem(val flag: String, val name: String, val detail: String)

class ApiClient(private val baseUrl: String = "https://vpnboss.space") {
    var token: String = ""

    fun tgInit(): AuthInit {
        val payload = request("POST", "/api/auth/tg-init", JSONObject().put("mode", "login"))
        return AuthInit(
            payload.optString("webToken"),
            payload.optString("deepLink").ifBlank { payload.optString("botLink") }
        )
    }

    fun tgCheck(webToken: String): String {
        val payload = request("GET", "/api/auth/tg-check/$webToken", null)
        if (payload.optString("status") == "confirmed") token = payload.optString("token")
        return payload.optString("status")
    }

    fun loadRoutes(): List<RouteItem> {
        val bundle = request("GET", "/api/connect/configs", null)
        val configs = bundle.optJSONArray("configs") ?: JSONArray()
        val routes = mutableListOf<RouteItem>()
        for (index in 0 until configs.length()) {
            val item = configs.optJSONObject(index) ?: continue
            val name = item.optString("name").ifBlank { item.optString("displayName", "VPNBOSS") }
            val flag = item.optString("flag").ifBlank { "🌐" }
            val protocol = item.optString("protocol").ifBlank { "REALITY" }
            routes += RouteItem(flag, name.replace(flag, "").trim().ifBlank { "VPNBOSS" }, "$protocol • маршрут скрыт")
        }
        return routes.ifEmpty { listOf(RouteItem("🇩🇰", "Дания", "Ожидание подписки")) }
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        val text = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (error: Exception) {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: error.message.orEmpty()
        }
        if (connection.responseCode !in 200..299) error(text.ifBlank { "HTTP ${connection.responseCode}" })
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
