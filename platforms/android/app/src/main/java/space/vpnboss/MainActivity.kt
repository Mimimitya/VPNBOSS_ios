package space.vpnboss

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.github.tfox.flutter_vless.xray.service.XrayVPNService
import com.github.tfox.flutter_vless.xray.utils.AppConfigs
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val api = ApiClient()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("vpnboss", MODE_PRIVATE) }
    private var routes = emptyList<RouteItem>()
    private var selected = 0
    private var connecting = false
    private var connected = false
    private var autoConnect = false
    private lateinit var root: FrameLayout
    private var pulse: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api.token = prefs.getString("token", "").orEmpty()
        selected = prefs.getInt("selected", 0)
        render()
        if (api.token.isNotBlank()) refreshRoutes()
    }

    override fun onResume() {
        super.onResume()
        connected = AppConfigs.V2RAY_STATE == AppConfigs.V2RAY_STATES.V2RAY_CONNECTED
        if (api.token.isNotBlank() && routes.isEmpty()) refreshRoutes()
        else render()
    }

    private fun render() {
        pulse?.cancel()
        root = FrameLayout(this).apply { setBackgroundColor(PAPER) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(26))
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        page.addView(header())
        if (api.token.isBlank()) renderAuth(page) else renderHome(page)
        setContentView(root)
    }

    private fun header(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.vpnboss_logo)
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(172), dp(44)))
        addView(TextView(this@MainActivity).apply {
            text = if (api.token.isBlank()) "SECURE CLIENT" else "ONLINE"
            textSize = 10f
            setTextColor(MUTED)
            gravity = Gravity.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
    }

    private fun renderAuth(page: LinearLayout) {
        page.addView(space(0, 0), LinearLayout.LayoutParams(1, 0, 1f))
        page.addView(label("ПЕРВЫЙ ЗАПУСК", 11, MUTED, true))
        page.addView(label("Войдите через\nvpnboss.space", 34, INK, true).apply {
            setLineSpacing(0f, .94f)
        }, lp(top = 10))
        page.addView(label("Откроется официальный сайт. После подтверждения приложение само загрузит вашу подписку и доступные серверы.", 15, SECONDARY, false).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }, lp(top = 20))
        page.addView(primaryButton("ВОЙТИ ЧЕРЕЗ САЙТ") { startAuth() }, lp(top = 34, height = 58))
        page.addView(label("Сессия сохраняется на этом устройстве", 12, MUTED, false).apply { gravity = Gravity.CENTER }, lp(top = 14))
        page.addView(space(0, 0), LinearLayout.LayoutParams(1, 0, .55f))
    }

    private fun renderHome(page: LinearLayout) {
        val route = routes.getOrNull(selected.coerceIn(routes.indices))
        page.addView(space(0, 0), LinearLayout.LayoutParams(1, 0, .35f))

        val powerWrap = FrameLayout(this)
        val halo = View(this).apply { background = circle(if (connected) 0x16000000 else 0x0A000000) }
        val power = TextView(this).apply {
            text = "⏻"
            textSize = 88f
            gravity = Gravity.CENTER
            setTextColor(if (connected) Color.WHITE else INK)
            background = circle(if (connected) INK else Color.WHITE, if (connected) INK else 0x22000000)
            elevation = dp(connected.compareTo(false) * 10).toFloat()
            setOnClickListener { connectSelected() }
        }
        powerWrap.addView(halo, FrameLayout.LayoutParams(dp(236), dp(236), Gravity.CENTER))
        powerWrap.addView(power, FrameLayout.LayoutParams(dp(174), dp(174), Gravity.CENTER))
        page.addView(powerWrap, lp(height = 250))

        if (connecting) {
            pulse = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(halo, View.SCALE_X, .82f, 1.08f, .82f),
                    ObjectAnimator.ofFloat(halo, View.SCALE_Y, .82f, 1.08f, .82f),
                    ObjectAnimator.ofFloat(halo, View.ALPHA, .25f, 1f, .25f),
                )
                duration = 1300
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }

        page.addView(label(when { connecting -> "ПОДКЛЮЧАЕМ"; connected -> "ЗАЩИЩЕНО"; else -> "НЕ ПОДКЛЮЧЕНО" }, 11, MUTED, true).apply { gravity = Gravity.CENTER })
        page.addView(label(when { connecting -> "Проверяем маршрут…"; connected -> route?.name ?: "VPNBOSS"; else -> "Коснитесь кнопки" }, 23, INK, true).apply { gravity = Gravity.CENTER }, lp(top = 8))

        val serverCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(16), 0)
            background = rounded(Color.WHITE, 8, 0x18000000)
            elevation = dp(2).toFloat()
            setOnClickListener { showServers() }
        }
        serverCard.addView(label(route?.flag ?: "🌐", 28, INK, false), LinearLayout.LayoutParams(dp(46), -2))
        val serverText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        serverText.addView(label(route?.name ?: "Серверы загружаются", 16, INK, true))
        serverText.addView(label(route?.location ?: "Проверьте подписку", 12, MUTED, false), lp(top = 3))
        serverCard.addView(serverText, LinearLayout.LayoutParams(0, -2, 1f))
        serverCard.addView(label("⌄", 24, INK, false).apply { gravity = Gravity.CENTER })
        page.addView(serverCard, lp(top = 28, height = 76))

        val action = if (connected) "ОТКЛЮЧИТЬ" else "ПОДКЛЮЧИТЬ ВЫБРАННЫЙ"
        page.addView(primaryButton(action) { connectSelected() }, lp(top = 14, height = 56))
        page.addView(secondaryButton("АВТОПОДКЛЮЧЕНИЕ") { connectAutomatic() }, lp(top = 10, height = 50))
        page.addView(label("Автовыбор проверяет доступные маршруты и использует самый быстрый", 11, MUTED, false).apply { gravity = Gravity.CENTER }, lp(top = 12))
        page.addView(space(0, 0), LinearLayout.LayoutParams(1, 0, .15f))
    }

    private fun startAuth() {
        Toast.makeText(this, "Открываю защищённый вход", Toast.LENGTH_SHORT).show()
        thread {
            runCatching { api.appAuthInit() }.onSuccess { init ->
                handler.post { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(init.authUrl))) }
                pollAuth(init.appCode)
            }.onFailure { showError(it.message) }
        }
    }

    private fun pollAuth(code: String) = thread {
        repeat(150) {
            Thread.sleep(2_000)
            when (runCatching { api.appAuthCheck(code) }.getOrDefault("pending")) {
                "confirmed" -> {
                    prefs.edit().putString("token", api.token).apply()
                    refreshRoutes()
                    return@thread
                }
                "expired" -> {
                    showError("Ссылка входа истекла. Откройте её ещё раз.")
                    return@thread
                }
            }
        }
    }

    private fun refreshRoutes() = thread {
        runCatching { api.loadRoutes() }
            .onSuccess { loaded -> handler.post {
                routes = loaded
                selected = selected.coerceIn(0, (routes.size - 1).coerceAtLeast(0))
                render()
            } }
            .onFailure { error -> handler.post {
                if (error.message?.contains("401") == true || error.message?.contains("Unauthorized", true) == true) {
                    api.token = ""
                    prefs.edit().remove("token").apply()
                    render()
                } else showError(error.message)
            } }
    }

    private fun showServers() {
        if (routes.isEmpty()) return showError("В подписке пока нет доступных серверов")
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(20))
            background = rounded(PAPER, 8)
            addView(label("Выберите сервер", 23, INK, true))
            addView(label("Подключение пойдёт именно через выбранную локацию", 13, SECONDARY, false), lp(top = 8))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        routes.forEachIndexed { index, route ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                background = rounded(if (index == selected) 0xFFEAEAEA.toInt() else Color.WHITE, 7)
                addView(label(route.flag, 25, INK, false), LinearLayout.LayoutParams(dp(44), -2))
                addView(label(route.name, 15, INK, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(label(if (index == selected) "●" else "○", 15, INK, false))
                setOnClickListener {
                    selected = index
                    prefs.edit().putInt("selected", index).apply()
                    dialog.dismiss()
                    render()
                }
            }, lp(top = 8, height = 62))
        }
        content.addView(ScrollView(this).apply { addView(list) }, lp(top = 18, height = 330))
        dialog.setContentView(content)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((resources.displayMetrics.widthPixels * .92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun connectSelected() {
        if (connected) return stopVpn()
        if (routes.isEmpty()) return showError("Нет доступного сервера")
        autoConnect = false
        requestVpnPermission()
    }

    private fun connectAutomatic() {
        if (connected) return stopVpn()
        if (routes.isEmpty()) return showError("Нет доступных серверов")
        autoConnect = true
        connecting = true
        render()
        thread {
            val measured = routes.mapIndexed { index, route -> index to measureDelay(route.config) }
            val best = measured.filter { it.second >= 0 }.minByOrNull { it.second }?.first ?: selected
            handler.post {
                selected = best
                prefs.edit().putInt("selected", best).apply()
                requestVpnPermission()
            }
        }
    }

    private fun requestVpnPermission() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) startActivityForResult(prepare, VPN_PERMISSION) else startVpn()
    }

    private fun startVpn() {
        val route = routes.getOrNull(selected) ?: return
        connecting = true
        render()
        val config = runCatching { XrayConfigBuilder.build(route.config, route.name) }
            .getOrElse { connecting = false; render(); return showError(it.message) }
        startForegroundService(Intent(this, XrayVPNService::class.java).apply {
            putExtra("COMMAND", AppConfigs.V2RAY_SERVICE_COMMANDS.START_SERVICE)
            putExtra("V2RAY_CONFIG", config)
            putExtra("PROXY_ONLY", false)
        })
        handler.postDelayed({
            connecting = false
            connected = AppConfigs.V2RAY_STATE == AppConfigs.V2RAY_STATES.V2RAY_CONNECTED
            render()
            if (!connected) showError("Не удалось запустить VPN-туннель. Проверьте конфигурацию сервера.")
        }, 1_500)
    }

    private fun stopVpn() {
        startService(Intent(this, XrayVPNService::class.java).apply {
            putExtra("COMMAND", AppConfigs.V2RAY_SERVICE_COMMANDS.STOP_SERVICE)
        })
        connected = false
        connecting = false
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION) {
            if (resultCode == RESULT_OK) startVpn() else { connecting = false; render() }
        }
    }

    private fun primaryButton(text: String, action: () -> Unit) = label(text, 14, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = rounded(INK, 7)
        setOnClickListener { animate().scaleX(.97f).scaleY(.97f).setDuration(80).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(130).start(); action()
        }.start() }
    }

    private fun secondaryButton(text: String, action: () -> Unit) = label(text, 13, INK, true).apply {
        gravity = Gravity.CENTER
        background = rounded(Color.TRANSPARENT, 7, 0x30000000)
        setOnClickListener { action() }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        includeFontPadding = false
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setTextIsSelectable(false)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun circle(color: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun lp(top: Int = 0, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(-1, if (height > 0) dp(height) else height).apply { topMargin = dp(top) }
    private fun space(width: Int, height: Int) = View(this).apply { minimumWidth = dp(width); minimumHeight = dp(height) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun measureDelay(link: String): Long = runCatching {
        val endpoint = XrayConfigBuilder.endpoint(link)
        val started = System.currentTimeMillis()
        Socket().use { socket -> socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 1800) }
        System.currentTimeMillis() - started
    }.getOrDefault(-1L)
    private fun showError(message: String?) {
        handler.post { Toast.makeText(this, message ?: "Не удалось выполнить действие", Toast.LENGTH_LONG).show() }
    }

    companion object {
        private const val VPN_PERMISSION = 77
        private const val PAPER = 0xFFF6F6F4.toInt()
        private const val INK = 0xFF070707.toInt()
        private const val SECONDARY = 0xFF525252.toInt()
        private const val MUTED = 0xFF8A8A87.toInt()
    }
}
