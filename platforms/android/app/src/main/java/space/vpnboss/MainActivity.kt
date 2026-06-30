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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
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
    private var carouselView: LinearLayout? = null
    private var leftFlagView: FrameLayout? = null
    private var mainFlagView: FrameLayout? = null
    private var rightFlagView: FrameLayout? = null
    private var serverNameView: TextView? = null
    private var serverDetailView: TextView? = null
    private var connectionTitleView: TextView? = null
    private var powerButtonView: PowerButtonView? = null
    private var powerHaloView: View? = null
    private var primaryActionView: TextView? = null
    private var accountLoading = false
    private var profileDialog: Dialog? = null

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
        else render()
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
        page.addView(space(0, 0), LinearLayout.LayoutParams(1, dp(34)))

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
        powerWrap.addView(halo, FrameLayout.LayoutParams(dp(234), dp(234), Gravity.CENTER))
        powerWrap.addView(power, FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER))
        page.addView(powerWrap, lp(height = 246))

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
        }, 15, INK, true).apply { gravity = Gravity.CENTER }
        connectionTitleView = connectionTitle
        page.addView(connectionTitle)

        val carousel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        carouselView = carousel
        carousel.addView(label("‹", 43, INK, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { changeServer(-1) }
        }, LinearLayout.LayoutParams(dp(46), dp(92)))
        val leftFlag = flagBubble(routeAt(selected - 1)?.flag ?: "🌐", false)
        val mainFlag = flagBubble(route?.flag ?: "🌐", true)
        val rightFlag = flagBubble(routeAt(selected + 1)?.flag ?: "🌐", false)
        leftFlagView = leftFlag
        mainFlagView = mainFlag
        rightFlagView = rightFlag
        carousel.addView(leftFlag, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(17) })
        carousel.addView(mainFlag, LinearLayout.LayoutParams(dp(92), dp(92)))
        carousel.addView(rightFlag, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(17) })
        carousel.addView(label("›", 43, INK, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { changeServer(1) }
        }, LinearLayout.LayoutParams(dp(46), dp(92)))
        installCarouselSwipe(carousel, leftFlag, mainFlag, rightFlag)
        page.addView(carousel, lp(top = 16, height = 92))

        serverNameView = label(route?.name ?: "Серверы загружаются", 22, INK, true).apply { gravity = Gravity.CENTER }
        page.addView(serverNameView, lp(top = 12))
        serverDetailView = label(when {
            connecting -> "Устанавливаем защищённый маршрут"
            connected -> "Подключено · ${route?.detail ?: "VLESS Reality"}"
            else -> route?.detail ?: "Ожидание подписки"
        }, 13, MUTED, false).apply { gravity = Gravity.CENTER }
        page.addView(serverDetailView, lp(top = 7))

        val primaryAction = primaryButton(if (connected) "ОТКЛЮЧИТЬ" else "НАЙТИ ЛУЧШИЙ СЕРВЕР") {
            if (connected) stopVpn() else connectAutomatic()
        }
        primaryActionView = primaryAction
        page.addView(primaryAction, lp(top = 26, height = 56))
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

    private fun changeServer(delta: Int) {
        if (routes.isEmpty() || connecting || connected) return
        val carousel = carouselView ?: return
        carousel.animate()
            .alpha(.15f)
            .translationX(dp(-delta * 22).toFloat())
            .scaleX(.97f)
            .scaleY(.97f)
            .setDuration(105)
            .withEndAction {
                selected = (selected + delta + routes.size) % routes.size
                prefs.edit().putInt("selected", selected).apply()
                bindFlag(leftFlagView, routeAt(selected - 1)?.flag ?: "🌐", false)
                bindFlag(mainFlagView, routeAt(selected)?.flag ?: "🌐", true)
                bindFlag(rightFlagView, routeAt(selected + 1)?.flag ?: "🌐", false)
                serverNameView?.text = routeAt(selected)?.name ?: "VPNBOSS"
                serverDetailView?.text = routeAt(selected)?.detail ?: "VLESS Reality"
                carousel.translationX = dp(delta * 22).toFloat()
                carousel.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(210)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                serverNameView?.apply {
                    alpha = 0f
                    translationY = dp(5).toFloat()
                    animate().alpha(1f).translationY(0f).setDuration(190).start()
                }
                serverDetailView?.apply {
                    alpha = 0f
                    animate().alpha(1f).setDuration(220).start()
                }
            }.start()
    }

    private fun installCarouselSwipe(carousel: LinearLayout, vararg targets: View) {
        var startX = 0f
        var switched = false
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (connecting || connected || kotlin.math.abs(distanceX) < kotlin.math.abs(distanceY)) return false
                carousel.translationX = (carousel.translationX - distanceX * .45f).coerceIn(-dp(42).toFloat(), dp(42).toFloat())
                carousel.alpha = .82f
                return true
            }

            override fun onFling(first: MotionEvent?, last: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (kotlin.math.abs(velocityX) < 280 || kotlin.math.abs(velocityX) < kotlin.math.abs(velocityY)) return false
                switched = true
                changeServer(if (velocityX < 0) 1 else -1)
                return true
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                showServers()
                return true
            }
        })
        val listener = View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                startX = event.x
                switched = false
            }
            val handled = detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                val distance = event.x - startX
                if (!switched && event.actionMasked == MotionEvent.ACTION_UP && kotlin.math.abs(distance) > dp(42)) {
                    changeServer(if (distance < 0) 1 else -1)
                }
                carousel.animate().translationX(0f).alpha(1f).setDuration(180).start()
            }
            handled
        }
        targets.forEach { it.setOnTouchListener(listener) }
    }

    private fun flagBubble(flag: String, main: Boolean) = FrameLayout(this).apply {
        bindFlag(this, flag, main)
    }

    private fun bindFlag(target: FrameLayout?, flag: String, main: Boolean) {
        target ?: return
        target.removeAllViews()
        target.apply {
        background = circle(Color.WHITE, 0x14000000)
        elevation = dp(if (main) 8 else 3).toFloat()
        alpha = if (main) 1f else .56f
        val drawable = when (flag) {
            "🇩🇰" -> R.drawable.flag_dk
            "🇩🇪" -> R.drawable.flag_de
            "🇷🇺" -> R.drawable.flag_ru
            "🇪🇸" -> R.drawable.flag_es
            else -> 0
        }
        if (drawable != 0) {
            addView(ImageView(this@MainActivity).apply {
                setImageResource(drawable)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = circle(Color.WHITE)
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            addView(label("◎", if (main) 32 else 21, INK, true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        }
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
            when (runCatching { api.appAuthCheck(code) }.getOrDefault("pending")) {
                "confirmed" -> {
                    prefs.edit().putString("token", api.token).apply()
                    resumeAccount()
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
                    when {
                        profile.needsCompletion -> handler.post {
                            accountLoading = false
                            showProfileCompletion(profile.email)
                        }
                        !profile.hasSubscription && !profile.trialUsed -> runCatching { api.activateTrial() }
                            .onSuccess { accountLoading = false; refreshRoutes() }
                            .onFailure { accountLoading = false; showError(it.message) }
                        else -> { accountLoading = false; refreshRoutes() }
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

    private fun showProfileCompletion(savedEmail: String) {
        if (profileDialog?.isShowing == true || isFinishing) return
        val email = profileField("Email", savedEmail, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = profileField("Пароль", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirmation = profileField("Повторите пароль", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(25), dp(24), dp(20))
            background = rounded(PAPER, 8)
            addView(label("Завершите регистрацию", 24, INK, true))
            addView(label("Укажите email и придумайте пароль. После этого мы сразу подарим вам 5 дней VPNBOSS.", 14, SECONDARY, false).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            }, lp(top = 10))
            addView(email, lp(top = 20, height = 52))
            addView(password, lp(top = 9, height = 52))
            addView(confirmation, lp(top = 9, height = 52))
            addView(primaryButton("ПОЛУЧИТЬ 5 ДНЕЙ БЕСПЛАТНО") {
                val cleanEmail = email.text.toString().trim()
                val pass = password.text.toString()
                when {
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> showError("Введите корректный email")
                    pass.length < 6 -> showError("Пароль должен содержать минимум 6 символов")
                    pass != confirmation.text.toString() -> showError("Пароли не совпадают")
                    else -> {
                        email.isEnabled = false; password.isEnabled = false; confirmation.isEnabled = false
                        thread {
                            runCatching {
                                api.completeProfile(cleanEmail, pass)
                                api.activateTrial()
                            }.onSuccess {
                                handler.post { dialog.dismiss(); profileDialog = null }
                                refreshRoutes()
                            }.onFailure { error -> handler.post {
                                email.isEnabled = true; password.isEnabled = true; confirmation.isEnabled = true
                                showError(error.message)
                            } }
                        }
                    }
                }
            }, lp(top = 18, height = 56))
            addView(label("75 ГБ · 3 устройства · без оплаты", 12, MUTED, false).apply { gravity = Gravity.CENTER }, lp(top = 11))
        }
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        profileDialog = dialog
    }

    private fun profileField(hint: String, value: String, type: Int) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = type
        textSize = 16f
        setTextColor(INK)
        setHintTextColor(MUTED)
        setPadding(dp(15), 0, dp(15), 0)
        background = rounded(Color.WHITE, 7, 0x24000000)
        setSelectAllOnFocus(false)
    }

    private fun refreshRoutes() = thread {
        runCatching { api.loadRoutes() }
            .onSuccess { loaded -> handler.post {
                routes = loaded
                saveCachedRoutes(loaded)
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
                addView(flagBubble(route.flag, false), LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
                addView(label(route.name, 15, INK, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(label(if (index == selected) "●" else "○", 15, INK, false))
                setOnClickListener {
                    selected = index
                    prefs.edit().putInt("selected", index).apply()
                    dialog.dismiss()
                    bindFlag(leftFlagView, routeAt(selected - 1)?.flag ?: "🌐", false)
                    bindFlag(mainFlagView, routeAt(selected)?.flag ?: "🌐", true)
                    bindFlag(rightFlagView, routeAt(selected + 1)?.flag ?: "🌐", false)
                    serverNameView?.text = routeAt(selected)?.name ?: "VPNBOSS"
                    serverDetailView?.text = routeAt(selected)?.detail ?: "VLESS Reality"
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
                route.optString("name", "VPNBOSS"),
                route.optString("location", ""),
                route.optString("detail", "VLESS Reality"),
                route.getString("config"),
            )
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
