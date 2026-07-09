package space.vpnboss

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import android.util.Base64

data class AuthInit(val appCode: String, val authUrl: String)
data class AuthCheck(val status: String, val subscriptionUrl: String?)
data class ProfileState(
    val email: String,
    val needsCompletion: Boolean,
    val hasSubscription: Boolean,
    val trialUsed: Boolean,
)
data class RouteItem(
    val id: Long,
    val flag: String,
    val countryCode: String,
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

    fun appAuthCheck(appCode: String): AuthCheck {
        val payload = request("GET", "/api/app-auth/check/$appCode", null, authenticated = false)
        if (payload.optString("status") == "confirmed") token = payload.optString("token")
        return AuthCheck(
            status = payload.optString("status", "pending"),
            subscriptionUrl = payload.optJSONObject("connect")?.optString("subUrl")?.takeIf(String::isNotBlank),
        )
    }

    fun profile(): ProfileState {
        val payload = request("GET", "/api/auth/me", null)
        return ProfileState(
            email = payload.optString("email"),
            needsCompletion = payload.optBoolean("needsProfileCompletion", false),
            hasSubscription = payload.optJSONObject("sub") != null,
            trialUsed = payload.optBoolean("trialUsed", false),
        )
    }

    fun connectionUrl(): String {
        return request("GET", "/api/connect", null).getString("subUrl")
    }

    fun loadRoutes(subscriptionUrl: String): List<RouteItem> {
        require(subscriptionUrl.startsWith("https://")) { "Ссылка подписки должна использовать HTTPS" }
        return loadSubscription(subscriptionUrl).also {
            require(it.isNotEmpty()) { "Подписка пока не содержит доступных серверов" }
        }
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
        val serviceText = fragment.lowercase()
        if (
            listOf(
                "лимит устройств",
                "удалите одно устройство",
                "device limit",
                "remove one device",
            ).any(serviceText::contains)
        ) return null
        val flag = extractFlag(fragment).ifBlank { flagFor(fragment, uri.host.orEmpty()) }
        val countryCode = countryCodeFromFlag(flag).ifBlank { countryCodeFor(fragment, uri.host.orEmpty()) }
        val name = cleanName(fragment, flag)
            .replace(Regex("(?i)\\s*\\|\\s*iPhone\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        RouteItem(
            id = index.toLong(),
            flag = flag,
            countryCode = countryCode,
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

    private fun countryCodeFromFlag(flag: String): String {
        val points = flag.codePoints().toArray()
        if (points.size < 2 || points[0] !in 0x1F1E6..0x1F1FF || points[1] !in 0x1F1E6..0x1F1FF) return ""
        return "${('a'.code + points[0] - 0x1F1E6).toChar()}${('a'.code + points[1] - 0x1F1E6).toChar()}"
    }

    private fun countryCodeFor(vararg values: String): String {
        val value = values.joinToString(" ").lowercase()
        val fixed = when {
            listOf("europe", "europa", "европа", "евросоюз", "european union").any(value::contains) -> "eu"
            listOf("denmark", "дания", "copenhagen", "копенгаген").any(value::contains) -> "dk"
            listOf("germany", "германия", "frankfurt", "берлин").any(value::contains) -> "de"
            listOf("spain", "испания", "madrid", "мадрид").any(value::contains) -> "es"
            listOf("netherlands", "нидерланды", "amsterdam", "амстердам").any(value::contains) -> "nl"
            listOf("finland", "финляндия", "helsinki", "хельсинки").any(value::contains) -> "fi"
            listOf("france", "франция", "paris", "париж").any(value::contains) -> "fr"
            listOf("united kingdom", "great britain", "london", "великобритания", "лондон").any(value::contains) -> "gb"
            listOf("united states", "usa", "new york", "сша").any(value::contains) -> "us"
            listOf("russia", "россия", "moscow", "москва").any(value::contains) -> "ru"
            else -> ""
        }
        if (fixed.isNotEmpty()) return fixed

        val languages = listOf(Locale.ENGLISH, Locale("ru"), Locale("es"))
        for (code in Locale.getISOCountries()) {
            val lowerCode = code.lowercase()
            if (Regex("(^|[^a-z])${Regex.escape(lowerCode)}([^a-z]|$)").containsMatchIn(value)) return lowerCode
            val region = Locale.Builder().setRegion(code).build()
            if (languages.any { locale ->
                    region.getDisplayCountry(locale).lowercase(locale).takeIf { it.length >= 4 }?.let(value::contains) == true
                }) return lowerCode
        }
        return "xx"
    }

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
