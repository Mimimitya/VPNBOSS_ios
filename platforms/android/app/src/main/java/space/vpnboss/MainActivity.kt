package space.vpnboss

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val api = ApiClient()
    private val handler = Handler(Looper.getMainLooper())
    private var routes = listOf(RouteItem("🇩🇰", "Дания", "Ожидание подписки"))
    private var selected = 0
    private var connected = false
    private lateinit var root: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api.token = getPreferences(MODE_PRIVATE).getString("token", "") ?: ""
        render()
        if (api.token.isNotBlank()) refreshRoutes()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 42, 28, 28)
            setBackgroundColor(0xFFF6F6F6.toInt())
        }
        val logo = ImageView(this).apply {
            setImageResource(resources.getIdentifier("vpnboss_logo", "drawable", packageName))
            adjustViewBounds = true
            maxHeight = 54
        }
        root.addView(logo, LinearLayout.LayoutParams(240, 54))
        Space(this).also { root.addView(it, LinearLayout.LayoutParams(1, 120)) }
        if (api.token.isBlank()) renderAuth() else renderHome()
        setContentView(root)
    }

    private fun renderAuth() {
        root.addView(title("Перед началом работы авторизуйтесь через сайт VPNBOSS", 26))
        root.addView(copy("Откроется официальный вход vpnboss.space. После подтверждения приложение автоматически подтянет подписку и серверы."))
        button("Войти через vpnboss.space") { startAuth() }.also { root.addView(it) }
        status = copy("Ожидание авторизации")
        root.addView(status)
    }

    private fun renderHome() {
        val power = ImageButton(this).apply {
            setImageResource(resources.getIdentifier(if (connected) "vpn_on" else "vpn_off", "drawable", packageName))
            background = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { toggleVpn() }
        }
        root.addView(power, LinearLayout.LayoutParams(260, 260).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = 88 })
        root.addView(copy("Выберите локацию сервера").apply { gravity = Gravity.CENTER_HORIZONTAL })
        val route = routes[selected.coerceIn(routes.indices)]
        root.addView(title(route.flag, 42).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(title(route.name, 24).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(copy(if (connected) "Подключено" else route.detail).apply { gravity = Gravity.CENTER_HORIZONTAL })
        button(if (connected) "ОТКЛЮЧИТЬ VPN" else "АВТОПОДКЛЮЧЕНИЕ") { toggleVpn() }.also { root.addView(it) }
    }

    private fun title(text: String, size: Int) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(0xFF000000.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    private fun copy(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(0xFF555555.toInt())
        setPadding(0, 18, 0, 0)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF000000.toInt())
        setOnClickListener { action() }
    }

    private fun startAuth() {
        status.text = "Открываю официальный вход..."
        thread {
            runCatching { api.tgInit() }.onSuccess { init ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(init.link)))
                pollAuth(init.webToken)
            }.onFailure { showError(it.message) }
        }
    }

    private fun pollAuth(webToken: String) {
        thread {
            repeat(120) {
                Thread.sleep(2200)
                val confirmed = runCatching { api.tgCheck(webToken) }.getOrDefault("pending") == "confirmed"
                if (confirmed) {
                    getPreferences(MODE_PRIVATE).edit().putString("token", api.token).apply()
                    refreshRoutes()
                    return@thread
                }
            }
        }
    }

    private fun refreshRoutes() {
        thread {
            val loaded = runCatching { api.loadRoutes() }.getOrElse { routes }
            handler.post {
                routes = loaded
                render()
            }
        }
    }

    private fun toggleVpn() {
        if (!connected) {
            val prepare = VpnService.prepare(this)
            if (prepare != null) startActivityForResult(prepare, 77) else connected = true
        } else {
            connected = false
        }
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            connected = true
            render()
        }
    }

    private fun showError(text: String?) {
        handler.post { Toast.makeText(this, text ?: "Ошибка", Toast.LENGTH_LONG).show() }
    }
}
