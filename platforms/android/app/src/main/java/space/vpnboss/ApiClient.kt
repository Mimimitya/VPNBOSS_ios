package space.vpnboss

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthInit(val appCode: String, val authUrl: String)
data class RouteItem(
    val id: Long,
    val flag: String,
    val name: String,
    val location: String,
    val detail: String,
    val config: String,
)

class ApiClient(private val baseUrl: String = "https://vpnboss.space") {
    var token: String = ""

    fun appAuthInit(): AuthInit {
        val payload = request("POST", "/api/app-auth/init", JSONObject())
        return AuthInit(payload.getString("appCode"), payload.getString("authUrl"))
    }

    fun appAuthCheck(appCode: String): String {
        val payload = request("GET", "/api/app-auth/check/$appCode", null, authenticated = false)
        if (payload.optString("status") == "confirmed") token = payload.optString("token")
        return payload.optString("status", "pending")
    }

    fun loadRoutes(): List<RouteItem> {
        val bundle = request("GET", "/api/connect/configs", null)
        val configs = bundle.optJSONArray("configs") ?: JSONArray()
        val result = mutableListOf<RouteItem>()
        for (index in 0 until configs.length()) {
            val item = configs.optJSONObject(index) ?: continue
            val server = item.optJSONObject("server") ?: JSONObject()
            val rawName = server.optString("name").ifBlank { item.optString("name", "VPNBOSS") }
            val location = server.optString("location").ifBlank { rawName }
            val flag = flagFor(location, rawName)
            val config = item.optString("vlessKey")
            if (config.startsWith("vless://")) {
                result += RouteItem(
                    id = item.optLong("id", index.toLong()),
                    flag = flag,
                    name = cleanName(rawName, flag),
                    location = location,
                    detail = "VLESS Reality",
                    config = config,
                )
            }
        }
        return result
    }

    private fun cleanName(value: String, flag: String): String = value.replace(flag, "").trim().ifBlank { "VPNBOSS" }

    private fun flagFor(vararg values: String): String {
        val value = values.joinToString(" ").lowercase()
        return when {
            listOf("denmark", "дания", "copenhagen", "копенгаген", " dk").any(value::contains) -> "🇩🇰"
            listOf("germany", "германия", "frankfurt", "берлин", " de").any(value::contains) -> "🇩🇪"
            listOf("spain", "испания", "madrid", "мадрид", " es").any(value::contains) -> "🇪🇸"
            listOf("netherlands", "нидерланды", "amsterdam", "амстердам", " nl").any(value::contains) -> "🇳🇱"
            listOf("finland", "финляндия", "helsinki", "хельсинки", " fi").any(value::contains) -> "🇫🇮"
            listOf("france", "франция", "paris", "париж", " fr").any(value::contains) -> "🇫🇷"
            listOf("united kingdom", "london", "великобритания", "лондон", " uk").any(value::contains) -> "🇬🇧"
            listOf("usa", "united states", "сша", "new york", " us").any(value::contains) -> "🇺🇸"
            listOf("russia", "россия", "moscow", "москва", " ru").any(value::contains) -> "🇷🇺"
            else -> "🌐"
        }
    }

    private fun request(method: String, path: String, body: JSONObject?, authenticated: Boolean = true): JSONObject {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (authenticated && token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
            error(message.ifBlank { "Ошибка сервера ($code)" })
        }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
