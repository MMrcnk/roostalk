package pl.blitz.callwebhook

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat

/**
 * Ekran RoosTalk – ciemny motyw wg makiety:
 * tytuł (League Spartan), tekst o zgodzie, karta z polem linku (kłódka po włączeniu),
 * polem wyboru SIM i przełącznikiem „włącz / włączony”. Zmiany zabezpieczone okienkami.
 */
class MainActivity : Activity() {

    // Kolory z makiety
    private val BG = Color.BLACK
    private val CARD = 0xFF292929.toInt()
    private val FIELD = 0xFFF2F2F2.toInt()
    private val FIELD_TEXT = 0xFF6E6E6E.toInt()
    private val INPUT_TEXT = 0xFF333333.toInt()
    private val TRACK_OFF = 0xFFC8D1D9.toInt()
    private val TRACK_ON = 0xFF2E8446.toInt()
    private val RED = 0xFFAE2B22.toInt()
    private val VERSION_TEXT = 0xFF555555.toInt()

    private val required = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
    private val toRequest get() = required +
        (if (Build.VERSION.SDK_INT >= 26) arrayOf(Manifest.permission.READ_PHONE_NUMBERS) else emptyArray()) +
        (if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())

    // Czcionki (assets/fonts) – z bezpiecznym powrotem do systemowej
    private fun font(name: String, fallback: Typeface) =
        try { Typeface.createFromAsset(assets, "fonts/$name") } catch (_: Throwable) { fallback }
    private val fReg by lazy { font("DMSans-Regular.ttf", Typeface.DEFAULT) }
    private val fTitle by lazy { font("LeagueSpartan-Bold.ttf", Typeface.DEFAULT_BOLD) }

    private lateinit var toggle: Toggle
    private lateinit var linkEdit: EditText
    private lateinit var linkText: TextView
    private lateinit var lockIcon: Lock
    private lateinit var simField: TextView
    private var pendingEnable = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.setBackgroundColor(BG)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0   // jasne ikony paska stanu na czarnym tle

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER          // całe menu na środku: ten sam odstęp od góry i od dołu
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "RoosTalk"
            typeface = fTitle; textSize = 36f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            includeFontPadding = false
        }, matchWrap())

        root.addView(space(30))

        root.addView(TextView(this).apply {
            text = "pamiętaj o wyrażeniu zgody w ustawieniach\nna dostęp do rejestru połączeń!"
            typeface = fReg; setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setOnClickListener { openAppSettings() }
        }, matchWrap())

        root.addView(space(30))

        // ----- karta -----
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(CARD, 10)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // pole linku: edytowalne przed pierwszym włączeniem, potem tekst + kłódka
        val linkBox = FrameLayout(this).apply { background = rounded(FIELD, 10) }
        linkEdit = EditText(this).apply {
            hint = "wklej link"
            typeface = fReg; textSize = 18f
            setTextColor(INPUT_TEXT); setHintTextColor(FIELD_TEXT)
            background = null
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(Prefs.rawUrl(this@MainActivity))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (!isLinkLocked()) Prefs.setUrl(this@MainActivity, s.toString())
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        linkBox.addView(linkEdit, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        linkText = TextView(this).apply {
            typeface = fReg; textSize = 18f; setTextColor(FIELD_TEXT)
            setPadding(dp(16), 0, dp(52), 0)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(); ellipsize = TextUtils.TruncateAt.MIDDLE
            setOnClickListener { toast("Kliknij kłódkę, żeby zmienić link") }
        }
        linkBox.addView(linkText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        lockIcon = Lock(this).apply { setOnClickListener { changeLink() } }
        linkBox.addView(lockIcon, FrameLayout.LayoutParams(dp(56), FrameLayout.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.END
        })
        card.addView(linkBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))

        card.addView(space(20))

        // pole wyboru SIM
        simField = TextView(this).apply {
            hint = "wybierz SIM"
            typeface = fReg; textSize = 18f
            setTextColor(FIELD_TEXT); setHintTextColor(FIELD_TEXT)
            background = rounded(FIELD, 10)
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(); ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { showSimPicker() }
        }
        card.addView(simField, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))

        card.addView(space(24))

        toggle = Toggle()
        toggle.onToggle = { on -> if (on) tryEnable() else confirmDisable() }
        card.addView(toggle, LinearLayout.LayoutParams(dp(TOGGLE_W), dp(TOGGLE_H)))

        root.addView(card, matchWrap())


        // Tło: kogut z logo w kolorze #1A1A1A na czerni, wyśrodkowany w pionie, lekko wysunięty w lewo
        val frame = FrameLayout(this)
        frame.setBackgroundColor(BG)
        frame.addView(ImageView(this).apply {
            setImageResource(R.drawable.bg_chicken)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            translationX = -dp(26).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL   // środek kury = środek ekranu (jak menu)
        })
        frame.addView(ScrollView(this).apply { isFillViewport = true; addView(root) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // numer wersji przyklejony do dołu ekranu – nie wpływa na wyśrodkowanie menu
        frame.addView(TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            typeface = fReg; textSize = 11f; setTextColor(VERSION_TEXT); setPadding(dp(8), dp(8), dp(8), dp(10))
            setOnClickListener { Updater.check(this@MainActivity, silent = false) }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        })
        setContentView(frame)

        try { Work.schedulePeriodic(this) } catch (_: Throwable) { }
        Updater.check(this, silent = true)

        // Otwarta automatycznie po restarcie telefonu – uruchom wszystko i schowaj się w tło
        if (intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true) {
            root.postDelayed({ try { moveTaskToBack(true) } catch (_: Throwable) { } }, 1500)
        }
    }

    companion object {
        const val EXTRA_FROM_BOOT = "from_boot"
        private const val TOGGLE_W = 200
        private const val TOGGLE_H = 54
        private const val KNOB_W = 120
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.enabled(this) && !hasPerms()) Prefs.setEnabled(this, false)
        toggle.set(Prefs.enabled(this), animate = false)
        refreshFields()
        MonitorService.ensureRunning(this)
        askNext()
    }

    // ---------- logika ----------

    private fun hasPerms() = required.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Link jest zablokowany (kłódka) po pierwszym włączeniu – zmiana tylko przez okienko. */
    private fun isLinkLocked() =
        Prefs.url(this).isNotBlank() && (Prefs.urlLocked(this) || Prefs.enabled(this))

    private fun refreshFields() {
        val locked = isLinkLocked()
        linkEdit.visibility = if (locked) View.GONE else View.VISIBLE
        linkText.visibility = if (locked) View.VISIBLE else View.GONE
        lockIcon.visibility = if (locked) View.VISIBLE else View.GONE
        if (locked) linkText.text = Prefs.rawUrl(this)

        val slot = Prefs.slot(this)
        simField.text = if (slot < 0) "" else {
            val carrier = Sims.list(this).firstOrNull { it.slot == slot }?.carrier.orEmpty()
            "SIM${slot + 1}" + if (carrier.isNotBlank()) " \u2013 $carrier" else ""
        }
    }

    private fun tryEnable() {
        if (Prefs.url(this).isBlank()) { toast("Wklej link"); return }
        if (Prefs.slot(this) < 0) { toast("Wybierz kartę SIM"); showSimPicker(); return }
        if (!hasPerms()) {
            pendingEnable = true
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(toRequest, 1)
            return
        }
        setEnabled(true)
        askNext()
    }

    private fun setEnabled(on: Boolean) {
        if (on) {
            ScanWorker.resetToNow(this)
            Prefs.setUrlLocked(this, true)
        }
        Prefs.setEnabled(this, on)
        toggle.set(on)
        refreshFields()
        if (on) MonitorService.ensureRunning(this) else MonitorService.stop(this)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (hasPerms()) {
            if (pendingEnable) { pendingEnable = false; setEnabled(true); askNext() }
        } else {
            pendingEnable = false
            toast("Zezwól na dostęp do telefonu i rejestru połączeń")
            openAppSettings()
        }
    }

    /**
     * Po włączeniu – kolejno, każde raz: bateria bez ograniczeń → autostart producenta
     * → wyświetlanie nad innymi aplikacjami (pozwala RoosTalk otworzyć się samemu po restarcie).
     * Wywoływane w onResume, więc po powrocie z każdego ekranu ustawień pokazuje następny.
     */
    private fun askNext() {
        if (!Prefs.enabled(this) || !hasPerms()) return
        if (Build.VERSION.SDK_INT >= 23 && !Prefs.askedBattery(this)) {
            Prefs.setAskedBattery(this)
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                    return
                } catch (_: Throwable) { }
            }
        }
        if (!Prefs.askedAutostart(this)) {
            Prefs.setAskedAutostart(this)
            if (openAutostart()) return
        }
        if (Build.VERSION.SDK_INT >= 23 && !Prefs.askedOverlay(this)) {
            Prefs.setAskedOverlay(this)
            if (!Settings.canDrawOverlays(this)) {
                try {
                    toast("Włącz dla RoosTalk – aplikacja sama uruchomi się po restarcie")
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return
                } catch (_: Throwable) { }
            }
        }
    }

    /** Xiaomi/Huawei/Oppo/Vivo/Samsung itp. – ekran autostartu / działania w tle. */
    private fun openAutostart(): Boolean {
        val screens = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.sm" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity"
        )
        for ((pkg, cls) in screens) {
            val i = Intent().setClassName(pkg, cls)
            if (packageManager.resolveActivity(i, 0) != null) {
                try {
                    toast("Włącz autostart / działanie w tle dla RoosTalk")
                    startActivity(i); return true
                } catch (_: Throwable) { }
            }
        }
        return false
    }

    private fun openAppSettings() = try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    } catch (_: Throwable) { }

    // ---------- okienka ----------

    /** Wyłączenie wymaga wpisania „wyłączam”. */
    private fun confirmDisable() = popup(
        message = "Żeby wyłączyć zapisywanie numerów przez RoosTalk wpisz \u201Ewyłączam\u201D",
        hint = "wpisz \u201Ewyłączam\u201D",
        button = "wyłącz",
        isLink = false
    ) { text, d, input ->
        if (text.uppercase() in listOf("WYŁĄCZAM", "WYLACZAM")) { d.dismiss(); setEnabled(false) }
        else { toast("Wpisz wyłączam"); shake(input) }
    }

    /** Zmiana linku – wklej nowy i kliknij „zmień”. */
    private fun changeLink() = popup(
        message = "Zmieniasz link do arkusza \u2013 jeżeli jesteś tego pewien to wklej nowy i kliknij zmień",
        hint = "wklej nowy link",
        button = "zmień",
        isLink = true
    ) { text, d, input ->
        val ok = text.startsWith("http") || Regex("^[A-Za-z0-9_-]{20,}$").matches(text)
        if (!ok) { toast("Wklej link do Apps Script"); shake(input); return@popup }
        Prefs.setUrl(this, text)
        Prefs.setUrlLocked(this, true)
        linkEdit.setText(Prefs.rawUrl(this))
        d.dismiss()
        refreshFields()
        toast("Zmieniono link")
    }

    /** Wybór karty: pierwszy wybór bez pytania, zmiana – okienko „zmieniam”. */
    private fun showSimPicker() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 14)
            setPadding(dp(22), dp(18), dp(22), dp(14))
        }
        box.addView(TextView(this).apply {
            text = "wybierz SIM"
            typeface = fReg; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }, matchWrap())
        val sims = Sims.list(this)
        for (slot in 0..1) {
            val carrier = sims.firstOrNull { it.slot == slot }?.carrier.orEmpty()
            box.addView(RadioButton(this).apply {
                text = "SIM${slot + 1}" + if (carrier.isNotBlank()) " \u2013 $carrier" else ""
                typeface = fReg; textSize = 19f; setTextColor(Color.WHITE)
                setPadding(dp(12), dp(12), 0, dp(12))
                isChecked = Prefs.slot(this@MainActivity) == slot
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(TRACK_ON, Color.WHITE))
                setOnClickListener {
                    val current = Prefs.slot(this@MainActivity)
                    d.dismiss()
                    when {
                        current == slot -> Unit
                        current < 0 -> applySlot(slot)
                        else -> confirmSimChange(slot)
                    }
                }
            })
        }
        showDialog(d, box)
    }

    private fun confirmSimChange(slot: Int) = popup(
        message = "Zmieniasz SIM z którego zapisujesz połączenia \u2013 jeżeli chcesz zmienić wpisz \u201Ezmieniam\u201D",
        hint = "wpisz \u201Ezmieniam\u201D",
        button = "zmień",
        isLink = false
    ) { text, d, input ->
        if (text.uppercase() == "ZMIENIAM") { d.dismiss(); applySlot(slot) }
        else { toast("Wpisz zmieniam"); shake(input) }
    }

    private fun applySlot(slot: Int) {
        if (Prefs.enabled(this)) ScanWorker.resetToNow(this)
        Prefs.setSlot(this, slot)
        refreshFields()
        MonitorService.ensureRunning(this)   // odświeża powiadomienie „Zapisuję połączenia z SIMx”
        toast("Zapisuję połączenia z SIM${slot + 1}")
    }

    /** Wspólne okienko: tekst, pole, biały przycisk z czerwoną obwódką. */
    private fun popup(
        message: String, hint: String, button: String, isLink: Boolean,
        onClick: (text: String, dialog: Dialog, input: EditText) -> Unit
    ) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(CARD, 14)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        box.addView(TextView(this).apply {
            text = message
            typeface = fReg; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.18f)
        }, matchWrap())
        val input = EditText(this).apply {
            this.hint = hint
            typeface = fReg; textSize = 17f
            setTextColor(INPUT_TEXT); setHintTextColor(FIELD_TEXT)
            background = rounded(FIELD, 10)
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine()
            inputType = if (isLink) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
            topMargin = dp(18)
        })
        box.addView(TextView(this).apply {
            text = button
            typeface = fReg; textSize = 20f; setTextColor(RED); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(25).toFloat(); setStroke(dp(4), RED)
            }
            isClickable = true
            setOnClickListener { onClick(input.text.toString().trim(), d, input) }
        }, LinearLayout.LayoutParams(dp(120), dp(50)).apply { topMargin = dp(20) })
        showDialog(d, box)
    }

    /** Wszystkie okienka mają tę samą szerokość: 88% ekranu, maks. 360dp. */
    private fun showDialog(d: Dialog, content: View) {
        val width = minOf((resources.displayMetrics.widthPixels * 0.88).toInt(), dp(360))
        d.setContentView(content, android.view.ViewGroup.LayoutParams(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    private fun shake(v: View) {
        v.animate().translationX(dp(10).toFloat()).setDuration(60).withEndAction {
            v.animate().translationX(-dp(10).toFloat()).setDuration(60).withEndAction {
                v.animate().translationX(0f).setDuration(60).start()
            }.start()
        }.start()
    }

    // ---------- widoki ----------

    /** Przełącznik-pigułka: „włącz” (szary tor, gałka z lewej) / „włączony” (zielony tor, gałka z prawej). */
    inner class Toggle : FrameLayout(this@MainActivity) {
        var onToggle: ((Boolean) -> Unit)? = null
        private var on = false
        private val track = GradientDrawable().apply { cornerRadius = dp(TOGGLE_H / 2).toFloat(); setColor(TRACK_OFF) }
        private var trackColor = TRACK_OFF
        private val knob = TextView(context).apply {
            gravity = Gravity.CENTER
            typeface = fReg; textSize = 19f; setTextColor(FIELD_TEXT)
            background = GradientDrawable().apply { cornerRadius = dp(TOGGLE_H / 2 - 4).toFloat(); setColor(Color.WHITE) }
        }

        init {
            background = track
            addView(knob, LayoutParams(dp(KNOB_W), dp(TOGGLE_H - 8)).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL; leftMargin = dp(4)
            })
            setOnClickListener { onToggle?.invoke(!on) }
            set(false, animate = false)
        }

        private var anim: android.animation.ValueAnimator? = null

        /** Płynne przejście: gałka ślizga się z wyhamowaniem, tor przechodzi kolorem, napis znika i pojawia się. */
        fun set(value: Boolean, animate: Boolean = true) {
            on = value
            val x = if (value) dp(TOGGLE_W - KNOB_W - 8).toFloat() else 0f
            val toColor = if (value) TRACK_ON else TRACK_OFF
            val newText = if (value) "włączony" else "włącz"
            anim?.cancel()
            if (!animate) {
                knob.translationX = x; track.setColor(toColor); trackColor = toColor
                knob.text = newText; knob.setTextColor(FIELD_TEXT)
                return
            }
            val fromX = knob.translationX
            val fromColor = trackColor
            val argb = android.animation.ArgbEvaluator()
            var swapped = false
            anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 380
                interpolator = android.view.animation.DecelerateInterpolator(1.8f)
                addUpdateListener { va ->
                    val f = va.animatedValue as Float
                    knob.translationX = fromX + (x - fromX) * f
                    val c = argb.evaluate(f, fromColor, toColor) as Int
                    track.setColor(c); trackColor = c
                    // napis: zanika do połowy, podmiana, pojawia się
                    val a = if (f < 0.5f) 1f - f * 2f else (f - 0.5f) * 2f
                    if (f >= 0.5f && !swapped) { knob.text = newText; swapped = true }
                    knob.setTextColor(Color.argb((a * 255).toInt(), 0x6E, 0x6E, 0x6E))
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        knob.text = newText; knob.setTextColor(FIELD_TEXT)
                    }
                })
                start()
            }
        }
    }

    /** Szara kłódka w polu linku (rysunek 22×28dp na środku większego pola dotyku). */
    class Lock(c: Context) : View(c) {
        private val d = c.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E6E6E.toInt() }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6E6E6E.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT
        }
        private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F2F2.toInt() }
        init { isClickable = true; contentDescription = "Zmień link" }
        override fun onDraw(cv: Canvas) {
            val w = 22 * d; val h = 28 * d
            val ox = (width - w) / 2f; val oy = (height - h) / 2f
            cv.save(); cv.translate(ox, oy)
            stroke.strokeWidth = w * 0.17f
            val sw = stroke.strokeWidth / 2
            val bodyTop = h * 0.44f
            cv.drawArc(RectF(w * 0.2f, sw, w * 0.8f, sw + w * 0.6f), 180f, 180f, false, stroke)
            val cy = sw + w * 0.3f
            cv.drawLine(w * 0.2f, cy, w * 0.2f, bodyTop + 1, stroke)
            cv.drawLine(w * 0.8f, cy, w * 0.8f, bodyTop + 1, stroke)
            cv.drawRoundRect(RectF(0f, bodyTop, w, h), w * 0.16f, w * 0.16f, fill)
            val ky = bodyTop + (h - bodyTop) * 0.4f
            cv.drawCircle(w / 2, ky, w * 0.11f, hole)
            cv.drawRect(w / 2 - w * 0.055f, ky, w / 2 + w * 0.055f, bodyTop + (h - bodyTop) * 0.74f, hole)
            cv.restore()
        }
    }

    // ---------- helpery ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun rounded(fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radiusDp).toFloat()
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
