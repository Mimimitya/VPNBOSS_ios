package space.vpnboss

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.caverock.androidsvg.SVG
import com.github.tfox.flutter_vless.xray.service.XrayVPNService
import com.github.tfox.flutter_vless.xray.utils.AppConfigs
import java.net.InetSocketAddress
import java.net.Socket
import org.json.JSONArray
import org.json.JSONObject
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
    private var playFirstConnectAnimation = false
    private var serverSelectorView: LinearLayout? = null
    private var serverSelectorFlagView: ImageView? = null
    private var serverNameView: TextView? = null
    private var serverDetailView: TextView? = null
    private var connectionTitleView: TextView? = null
    private var powerButtonView: PowerButtonView? = null
    private var powerHaloView: View? = null
    private var primaryActionView: TextView? = null
    private var accountLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api.token = prefs.getString("token", "").orEmpty()
        selected = prefs.getInt("selected", 0)
        routes = loadCachedRoutes()
        render()
        if (api.token.isNotBlank()) resumeAccount()
    }

    override fun onResume() {
        super.onResume()
        connected = AppConfigs.V2RAY_STATE == AppConfigs.V2RAY_STATES.V2RAY_CONNECTED
        if (api.token.isNotBlank() && routes.isEmpty()) resumeAccount()
        updateConnectionPresentation()
    }

    private fun render() {
        pulse?.cancel()
        root = FrameLayout(this).apply { setBackgroundColor(PAPER) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(26))
            setBackgroundColor(PAPER)
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        page.addView(header())
        if (api.token.isBlank()) renderAuth(page) else renderHome(page)
        setContentView(root)
        animateEntrance(page)
    }

    private fun header(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.vpnboss_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "VPNBOSS"
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(View(this@MainActivity).apply {
                background = circle(if (connected) 0xFF111111.toInt() else 0xFFB9B9B5.toInt())
            }, LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(7) })
            addView(label(if (connected) "Подключено" else "VPNBOSS", 11, MUTED, true))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        if (api.token.isNotBlank()) {
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_settings)
                scaleType = ImageView.ScaleType.CENTER
                background = circle(Color.WHITE, 0x22000000)
                elevation = dp(1).toFloat()
                contentDescription = "Настройки аккаунта"
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener {
                    animate().scaleX(.92f).scaleY(.92f).setDuration(75).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(130).start()
                        showAccountMenu(this)
                    }.start()
                }
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(12) })
        }
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
        val route = routeAt(selected)
        page.addView(space(0, 0), LinearLayout.LayoutParams(1, dp(18)))

        val powerWrap = FrameLayout(this)
        val halo = View(this).apply { background = circle(if (connected) 0x16000000 else 0x0A000000) }
        powerHaloView = halo
        val power = PowerButtonView(this).apply {
            setState(when {
                connecting -> PowerButtonView.State.CONNECTING
                connected -> PowerButtonView.State.ON
                else -> PowerButtonView.State.OFF
            }, playFirstConnectAnimation)
            playFirstConnectAnimation = false
            elevation = dp(connected.compareTo(false) * 10).toFloat()
            contentDescription = if (connected) "Отключить VPN" else "Подключить выбранный сервер"
            setOnClickListener {
                animate().scaleX(.92f).scaleY(.92f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
                    connectSelected()
                }.start()
            }
        }
        powerButtonView = power
        powerWrap.addView(halo, FrameLayout.LayoutParams(dp(214), dp(214), Gravity.CENTER))
        powerWrap.addView(power, FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER))
        page.addView(powerWrap, lp(height = 222))

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

        val connectionTitle = label(when {
            connecting -> "Устанавливаем соединение"
            connected -> "VPNBOSS включён"
            else -> "Выберите локацию сервера"
        }, 14, if (connected) INK else SECONDARY, true).apply { gravity = Gravity.CENTER }
        connectionTitleView = connectionTitle
        page.addView(connectionTitle)
        page.addView(label("ЛОКАЦИЯ", 11, MUTED, true), lp(top = 22))

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(Color.WHITE, 7, 0x24000000)
            elevation = dp(1).toFloat()
            setOnClickListener {
                animate().scaleX(.985f).scaleY(.985f).setDuration(75).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    showServerDropdown(this)
                }.start()
            }
        }
        serverSelectorView = selector
        val selectorFlag = flagImage(route?.countryCode ?: "xx", dp(40))
        serverSelectorFlagView = selectorFlag
        selector.addView(selectorFlag, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(13) })
        val selectorName = label(route?.name ?: "Серверы загружаются", 16, INK, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        serverNameView = selectorName
        val selectorDetail = label(when {
            connecting -> "Устанавливаем защищённый маршрут"
            connected -> "Подключено · ${route?.detail ?: "VLESS Reality"}"
            else -> route?.detail ?: "Ожидание подписки"
        }, 12, MUTED, false).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        serverDetailView = selectorDetail
        selector.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(selectorName)
            addView(selectorDetail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        }, LinearLayout.LayoutParams(0, -1, 1f))
        selector.addView(label("⌄", 22, INK, false).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(30), -1))
        page.addView(selector, lp(top = 8, height = 68))

        val primaryAction = primaryButton(if (connected) "ОТКЛЮЧИТЬ" else "НАЙТИ ЛУЧШИЙ СЕРВЕР") {
            if (connected) stopVpn() else connectAutomatic()
        }
        primaryActionView = primaryAction
        page.addView(primaryAction, lp(top = 22, height = 56))
        page.addView(secondaryButton("ЛИЧНЫЙ КАБИНЕТ") { showCabinetDialog() }, lp(top = 9, height = 48))
        page.addView(space(0, 0), LinearLayout.LayoutParams(1, 0, .15f))
    }

    private fun animateEntrance(page: LinearLayout) {
        for (index in 0 until page.childCount) {
            page.getChildAt(index).apply {
                alpha = 0f
                translationY = dp(if (index < 2) 8 else 14).toFloat()
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((index * 32L).coerceAtMost(190L))
                    .setDuration(280)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun routeAt(index: Int): RouteItem? {
        if (routes.isEmpty()) return null
        return routes[(index % routes.size + routes.size) % routes.size]
    }

    private fun selectServer(index: Int) {
        if (routes.isEmpty() || connecting || connected) return
        selected = index.coerceIn(0, routes.lastIndex)
        prefs.edit().putInt("selected", selected).apply()
        bindSelectedRoute(animate = true)
    }

    private fun bindSelectedRoute(animate: Boolean = false) {
        val route = routeAt(selected)
        serverNameView?.text = route?.name ?: "Серверы загружаются"
        serverDetailView?.text = route?.detail ?: "Ожидание подписки"
        serverSelectorFlagView?.let { bindFlagImage(it, route?.countryCode ?: "xx") }
        if (animate) {
            serverSelectorView?.apply {
                animate().cancel()
                alpha = .35f
                scaleX = .98f
                scaleY = .98f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190).start()
            }
        }
    }

    private fun showServerDropdown(anchor: View) {
        if (routes.isEmpty()) return showError("В подписке пока нет доступных серверов")
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.WHITE, 7, 0x22000000)
        }
        val scroll = ScrollView(this).apply { addView(list) }
        val popup = PopupWindow(
            scroll,
            anchor.width.coerceAtLeast(dp(280)),
            minOf(dp(360), routes.size * dp(58) + dp(16)),
            true,
        ).apply {
            elevation = dp(12).toFloat()
            isOutsideTouchable = true
            setBackgroundDrawable(rounded(Color.WHITE, 7))
        }
        routes.forEachIndexed { index, route ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
                background = rounded(if (index == selected) 0xFFF0F0EE.toInt() else Color.WHITE, 6)
                addView(flagImage(route.countryCode, dp(36)), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
                addView(label(route.name, 15, INK, true).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (index == selected) addView(label("●", 12, INK, false))
                setOnClickListener {
                    popup.dismiss()
                    selectServer(index)
                }
            }, LinearLayout.LayoutParams(-1, dp(58)))
        }
        popup.showAsDropDown(anchor, 0, dp(6))
        scroll.apply {
            pivotY = 0f
            alpha = 0f
            scaleY = .97f
            animate().alpha(1f).scaleY(1f).setDuration(170).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun flagImage(countryCode: String, size: Int) = ImageView(this).apply {
        layoutParams = ViewGroup.LayoutParams(size, size)
        scaleType = ImageView.ScaleType.CENTER_CROP
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        background = circle(Color.WHITE, 0x16000000)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        bindFlagImage(this, countryCode)
    }

    private fun bindFlagImage(view: ImageView, countryCode: String) {
        val code = countryCode.lowercase().takeIf { it.matches(Regex("[a-z]{2}")) } ?: "xx"
        if (code == "xx") {
            view.setImageResource(android.R.drawable.ic_menu_compass)
            return
        }
        runCatching {
            assets.open("flags/$code.svg").use { stream ->
                view.setImageDrawable(PictureDrawable(SVG.getFromInputStream(stream).renderToPicture()))
            }
        }.onFailure {
            view.setImageResource(android.R.drawable.ic_menu_compass)
        }
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
            val check = runCatching { api.appAuthCheck(code) }.getOrElse { AuthCheck("pending", null) }
            when (check.status) {
                "confirmed" -> {
                    prefs.edit().putString("token", api.token).apply()
                    handler.post { render() }
                    if (check.subscriptionUrl != null) refreshRoutes(check.subscriptionUrl) else resumeAccount()
                    return@thread
                }
                "expired" -> {
                    showError("Ссылка входа истекла. Откройте её ещё раз.")
                    return@thread
                }
            }
        }
    }

    private fun resumeAccount() {
        if (accountLoading || api.token.isBlank()) return
        accountLoading = true
        thread {
            runCatching { api.profile() }
                .onSuccess { profile ->
                    if (profile.needsCompletion) {
                        accountLoading = false
                        showError("Завершите вход и укажите email на сайте VPNBOSS")
                        handler.post { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vpnboss.space/cabinet"))) }
                    } else {
                        runCatching { api.connectionUrl() }
                            .onSuccess { subscriptionUrl ->
                                accountLoading = false
                                refreshRoutes(subscriptionUrl)
                            }
                            .onFailure {
                                accountLoading = false
                                handler.post {
                                    routes = emptyList()
                                    bindSelectedRoute()
                                    showError("Активной подписки пока нет")
                                }
                            }
                    }
                }
                .onFailure { error ->
                    accountLoading = false
                    if (error.message?.contains("401") == true) {
                        api.token = ""
                        prefs.edit().remove("token").apply()
                        handler.post { render() }
                    } else showError(error.message)
                }
        }
    }

    private fun refreshRoutes(subscriptionUrl: String) = thread {
        runCatching { api.loadRoutes(subscriptionUrl) }
            .onSuccess { loaded -> handler.post {
                routes = loaded
                saveCachedRoutes(loaded)
                selected = selected.coerceIn(0, (routes.size - 1).coerceAtLeast(0))
                bindSelectedRoute(animate = true)
            } }
            .onFailure { error -> handler.post {
                if (error.message?.contains("401") == true || error.message?.contains("Unauthorized", true) == true) {
                    api.token = ""
                    prefs.edit().remove("token").apply()
                    render()
                } else showError(error.message)
            } }
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
        updateConnectionPresentation()
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
        updateConnectionPresentation()
        val config = runCatching { XrayConfigBuilder.build(route.config, route.name) }
            .getOrElse { connecting = false; updateConnectionPresentation(); return showError(it.message) }
        startForegroundService(Intent(this, XrayVPNService::class.java).apply {
            putExtra("COMMAND", AppConfigs.V2RAY_SERVICE_COMMANDS.START_SERVICE)
            putExtra("V2RAY_CONFIG", config)
            putExtra("PROXY_ONLY", false)
        })
        handler.postDelayed({
            connecting = false
            connected = AppConfigs.V2RAY_STATE == AppConfigs.V2RAY_STATES.V2RAY_CONNECTED
            updateConnectionPresentation(animateSuccess = connected)
            if (!connected) showError("Не удалось запустить VPN-туннель. Проверьте конфигурацию сервера.")
        }, 1_500)
    }

    private fun stopVpn() {
        startService(Intent(this, XrayVPNService::class.java).apply {
            putExtra("COMMAND", AppConfigs.V2RAY_SERVICE_COMMANDS.STOP_SERVICE)
        })
        connected = false
        connecting = false
        updateConnectionPresentation()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION) {
            if (resultCode == RESULT_OK) startVpn() else { connecting = false; updateConnectionPresentation() }
        }
    }

    private fun updateConnectionPresentation(animateSuccess: Boolean = false) {
        val state = when {
            connecting -> PowerButtonView.State.CONNECTING
            connected -> PowerButtonView.State.ON
            else -> PowerButtonView.State.OFF
        }
        powerButtonView?.setState(state, animateSuccess)
        powerHaloView?.background = circle(when {
            connected -> 0x24708078
            connecting -> 0x16000000
            else -> 0x0A000000
        })
        pulse?.cancel()
        if (connecting) {
            val halo = powerHaloView
            if (halo != null) {
                val scaleX = ObjectAnimator.ofFloat(halo, View.SCALE_X, .88f, 1.08f).apply {
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
                val scaleY = ObjectAnimator.ofFloat(halo, View.SCALE_Y, .88f, 1.08f).apply {
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
                val alpha = ObjectAnimator.ofFloat(halo, View.ALPHA, .28f, .9f).apply {
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
                pulse = AnimatorSet().apply {
                    playTogether(scaleX, scaleY, alpha)
                    duration = 760
                    start()
                }
            }
        } else {
            powerHaloView?.apply { scaleX = 1f; scaleY = 1f; alpha = 1f }
        }
        connectionTitleView?.text = when {
            connecting -> "Устанавливаем соединение"
            connected -> "VPNBOSS включён"
            else -> "Выберите локацию сервера"
        }
        serverDetailView?.text = when {
            connecting -> "Устанавливаем защищённый маршрут"
            connected -> "Подключено · ${routeAt(selected)?.detail ?: "VLESS Reality"}"
            else -> routeAt(selected)?.detail ?: "Ожидание подписки"
        }
        primaryActionView?.text = if (connected) "ОТКЛЮЧИТЬ" else "НАЙТИ ЛУЧШИЙ СЕРВЕР"
        listOf(connectionTitleView, serverDetailView).forEach { view ->
            view?.apply {
                animate().cancel()
                alpha = .35f
                animate().alpha(1f).setDuration(220).start()
            }
        }
    }

    private fun showCabinetDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(25), dp(24), dp(20))
            background = rounded(PAPER, 8)
            addView(label("Личный кабинет", 24, INK, true))
            addView(label("Управление подпиской и аккаунтом находится на официальном сайте VPNBOSS.", 14, SECONDARY, false).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            }, lp(top = 11))
            addView(label("vpnboss.space", 17, INK, true), lp(top = 22))
            addView(primaryButton("ОТКРЫТЬ САЙТ") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vpnboss.space/cabinet")))
                dialog.dismiss()
            }, lp(top = 22, height = 54))
            addView(secondaryButton("ЗАКРЫТЬ") { dialog.dismiss() }, lp(top = 8, height = 46))
        }
        dialog.setContentView(content)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showAccountMenu(anchor: View) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.WHITE, 8, 0x22000000)
            addView(label("АККАУНТ", 11, MUTED, true).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
            }, LinearLayout.LayoutParams(-1, dp(38)))
        }
        val popup = PopupWindow(content, dp(210), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(14).toFloat()
            isOutsideTouchable = true
            setBackgroundDrawable(rounded(Color.WHITE, 8))
        }
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(0xFFFFF4F3.toInt(), 6)
            addView(ImageView(this@MainActivity).apply {
                setImageResource(android.R.drawable.ic_lock_power_off)
                setColorFilter(0xFFB42318.toInt())
                contentDescription = null
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
            addView(label("Выйти", 15, 0xFFB42318.toInt(), true), LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener {
                popup.dismiss()
                logout()
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        popup.showAsDropDown(anchor, anchor.width - dp(210), dp(7))
        content.apply {
            pivotX = dp(210).toFloat()
            pivotY = 0f
            alpha = 0f
            scaleX = .97f
            scaleY = .97f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(170)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun logout() {
        if (connected || connecting) stopVpn()
        pulse?.cancel()
        api.token = ""
        routes = emptyList()
        selected = 0
        connecting = false
        connected = false
        accountLoading = false
        prefs.edit()
            .remove("token")
            .remove("routes")
            .remove("selected")
            .apply()
        render()
        Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
    }

    private fun primaryButton(text: String, action: () -> Unit) = label(text, 14, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = rounded(INK, 28)
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

    private fun saveCachedRoutes(items: List<RouteItem>) {
        val payload = JSONArray()
        items.forEach { route ->
            payload.put(JSONObject()
                .put("id", route.id)
                .put("flag", route.flag)
                .put("countryCode", route.countryCode)
                .put("name", route.name)
                .put("location", route.location)
                .put("detail", route.detail)
                .put("config", route.config))
        }
        prefs.edit().putString("routes", payload.toString()).apply()
    }

    private fun loadCachedRoutes(): List<RouteItem> = runCatching {
        val payload = JSONArray(prefs.getString("routes", "[]"))
        (0 until payload.length()).map { index ->
            val route = payload.getJSONObject(index)
            RouteItem(
                route.optLong("id", index.toLong()),
                route.optString("flag", "🌐"),
                route.optString("countryCode", "xx"),
                route.optString("name", "VPNBOSS"),
                route.optString("location", ""),
                route.optString("detail", "VLESS Reality"),
                route.getString("config"),
            )
        }.filterNot { route ->
            val text = route.name.lowercase()
            text.contains("лимит устройств") || text.contains("удалите одно устройство") ||
                text.contains("device limit") || text.contains("remove one device")
        }
    }.getOrDefault(emptyList())
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
