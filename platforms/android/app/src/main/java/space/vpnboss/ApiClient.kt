package space.vpnboss

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import android.util.Base64

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
        val subscriptionUrl = bundle.optJSONObject("connect")?.optString("subUrl").orEmpty()
        if (subscriptionUrl.isNotBlank()) {
            val subscriptionRoutes = runCatching { loadSubscription(subscriptionUrl) }.getOrDefault(emptyList())
            if (subscriptionRoutes.isNotEmpty()) return subscriptionRoutes
        }
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

    private fun loadSubscription(rawUrl: String): List<RouteItem> {
        val url = if (rawUrl.startsWith("https://")) rawUrl else "https://sekretnik1.vps.webdock.cloud/sub/${rawUrl.removePrefix("/sub/").removePrefix("sub/").trim('/')}"
        val source = requestText(url)
        val decoded = decodeSubscription(source)
        return decoded.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("vless://", ignoreCase = true) }
            .mapIndexedNotNull { index, link -> routeFromLink(index, link) }
            .toList()
    }

    private fun decodeSubscription(source: String): String {
        val trimmed = source.trim()
        if (trimmed.contains("vless://", ignoreCase = true)) return trimmed
        val normalized = trimmed.replace('-', '+').replace('_', '/').filterNot(Char::isWhitespace)
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return runCatching { String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(trimmed)
    }

    private fun routeFromLink(index: Int, link: String): RouteItem? = runCatching {
        val uri = android.net.Uri.parse(link)
        val fragment = URLDecoder.decode(uri.fragment ?: uri.host ?: "VPNBOSS", "UTF-8")
        val flag = extractFlag(fragment).ifBlank { flagFor(fragment, uri.host.orEmpty()) }
        val name = cleanName(fragment, flag)
            .replace(Regex("(?i)\\s*\\|\\s*iPhone\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        RouteItem(
            id = index.toLong(),
            flag = flag,
            name = name,
            location = name,
            detail = "${uri.getQueryParameter("security")?.uppercase() ?: "REALITY"} · ${uri.getQueryParameter("type")?.uppercase() ?: "TCP"}",
            config = link,
        )
    }.getOrNull()

    private fun extractFlag(value: String): String {
        val chars = value.codePoints().toArray()
        for (index in 0 until chars.size - 1) {
            if (chars[index] in 0x1F1E6..0x1F1FF && chars[index + 1] in 0x1F1E6..0x1F1FF) {
                return String(chars, index, 2)
            }
        }
        return ""
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

    private fun requestText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "text/plain, */*")
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Подписка недоступна ($code)")
        return text
    }
}
