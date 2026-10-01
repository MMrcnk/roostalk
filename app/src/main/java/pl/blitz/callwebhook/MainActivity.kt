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
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    // Kolory z makiety
    private val TEXT = 0xFF111111.toInt()
    private val FIELD = 0xFFF1F1F1.toInt()
    private val BORDER = 0xFFCDD3DC.toInt()
    private val BLUE = 0xFF3D5A8F.toInt()
    private val POPUP = 0xFFF0F5FF.toInt()
    private val GREEN_BG = 0xFF98D194.toInt()
    private val GREEN_BORDER = 0xFF3E7D3E.toInt()
    private val GREY_BG = 0xFFE2E2E2.toInt()
    private val GREY_BORDER = 0xFFA0A0A0.toInt()
    private val GREY_TEXT = 0xFF707070.toInt()

    private val required = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
    private val toRequest get() = required +
        (if (Build.VERSION.SDK_INT >= 26) arrayOf(Manifest.permission.READ_PHONE_NUMBERS) else emptyArray()) +
        (if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())

    // Czcionka DM Sans (assets/fonts) – z bezpiecznym powrotem do systemowej
    private fun font(name: String, fallback: Typeface) =
        try { Typeface.createFromAsset(assets, "fonts/$name") } catch (_: Throwable) { fallback }
    private val fReg by lazy { font("DMSans-Regular.ttf", Typeface.DEFAULT) }
    private val fMed by lazy { font("DMSans-Medium.ttf", Typeface.DEFAULT_BOLD) }
    private val fBold by lazy { font("DMSans-Bold.ttf", Typeface.DEFAULT_BOLD) }

    private lateinit var toggle: Toggle
    private lateinit var simLabel: TextView
    private var pendingEnable = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.setBackgroundColor(Color.WHITE)
        if (Build.VERSION.SDK_INT >= 23) {
            window.statusBarColor = Color.WHITE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(22), dp(26), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Roostalk"
            typeface = fMed; textSize = 24f; setTextColor(TEXT); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(space(44))

        root.addView(TextView(this).apply {
            text = "pamiętaj o wyrażeniu zgody w ustawieniach\nna dostęp do rejestru połączeń!"
            typeface = fReg; setTextColor(TEXT); textSize = 17f; gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setOnClickListener { openAppSettings() }
        })

        root.addView(space(90))

        // Pole na link + przycisk z trzema kropkami
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(EditText(this).apply {
            hint = "wklej link"
            typeface = fReg; setHintTextColor(TEXT); setTextColor(TEXT); textSize = 17f
            background = box(FIELD, BORDER, 2, 4)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(Prefs.rawUrl(this@MainActivity))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = Prefs.setUrl(this@MainActivity, s.toString())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }, LinearLayout.LayoutParams(0, dp(56), 1f))

        val dotsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        dotsCol.addView(Dots(this).apply { setOnClickListener { showSimPicker() } },
            LinearLayout.LayoutParams(dp(58), dp(58)))
        simLabel = TextView(this).apply { typeface = fReg; textSize = 11f; setTextColor(GREY_TEXT); gravity = Gravity.CENTER }
        dotsCol.addView(simLabel)
        row.addView(dotsCol, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(20) })
        root.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(space(70))

        toggle = Toggle()
        toggle.onToggle = { on -> if (on) tryEnable() else setEnabled(false) }
        root.addView(toggle, LinearLayout.LayoutParams(dp(220), dp(56)))

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            typeface = fReg; textSize = 11f; setTextColor(0xFFB0B0B0.toInt()); setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { Updater.check(this@MainActivity, silent = false) }
        })

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })

        try { Work.schedulePeriodic(this) } catch (_: Throwable) { }
        Updater.check(this, silent = true)
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.enabled(this) && !hasPerms()) Prefs.setEnabled(this, false)
        toggle.set(Prefs.enabled(this), animate = false)
        MonitorService.ensureRunning(this)
        updateSimLabel()
    }

    // ---------- logika ----------

    private fun hasPerms() = required.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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
        askBatteryOnce()
    }

    private fun setEnabled(on: Boolean) {
        if (on) ScanWorker.resetToNow(this)
        Prefs.setEnabled(this, on)
        toggle.set(on)
        if (on) MonitorService.ensureRunning(this) else MonitorService.stop(this)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (hasPerms()) {
            if (pendingEnable) { pendingEnable = false; setEnabled(true); askBatteryOnce() }
        } else {
            pendingEnable = false
            toast("Zezwól na dostęp do telefonu i rejestru połączeń")
            openAppSettings()
        }
    }

    /** Najpierw zgoda na pracę bez oszczędzania baterii, potem (raz) ekran autostartu producenta. */
    private fun askBatteryOnce() {
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
        askAutostartOnce()
    }

    /** Xiaomi/Huawei/Oppo/Vivo itp. blokują start w tle, dopóki nie włączy się „Autostartu”. */
    private fun askAutostartOnce() {
        if (Prefs.askedAutostart(this)) return
        Prefs.setAskedAutostart(this)
        val screens = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity"
        )
        for ((pkg, cls) in screens) {
            val i = Intent().setClassName(pkg, cls)
            if (packageManager.resolveActivity(i, 0) != null) {
                try {
                    toast("Włącz autostart dla Roostalk")
                    startActivity(i); return
                } catch (_: Throwable) { }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // po powrocie z ekranu baterii pokaż ekran autostartu (jeśli jest)
        if (Prefs.enabled(this) && Prefs.askedBattery(this)) askAutostartOnce()
    }

    private fun openAppSettings() = try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    } catch (_: Throwable) { }

    private fun updateSimLabel() {
        val slot = Prefs.slot(this)
        if (slot < 0) { simLabel.text = ""; return }
        val carrier = Sims.list(this).firstOrNull { it.slot == slot }?.carrier.orEmpty()
        simLabel.text = "SIM${slot + 1}" + if (carrier.isNotBlank()) "\n$carrier" else ""
    }

    // ---------- popup wyboru SIM ----------

    private fun showSimPicker() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(POPUP, BORDER, 3, 6)
            setPadding(dp(26), dp(18), dp(90), dp(18))
        }
        for (slot in 0..1) {
            box.addView(RadioButton(this).apply {
                text = "SIM${slot + 1}"
                typeface = fReg; textSize = 20f; setTextColor(TEXT)
                setPadding(dp(14), dp(10), 0, dp(10))
                isChecked = Prefs.slot(this@MainActivity) == slot
                if (Build.VERSION.SDK_INT >= 21) buttonTintList = ColorStateList.valueOf(BLUE)
                setOnClickListener {
                    if (Prefs.slot(this@MainActivity) != slot && Prefs.enabled(this@MainActivity))
                        ScanWorker.resetToNow(this@MainActivity)
                    Prefs.setSlot(this@MainActivity, slot)
                    updateSimLabel()
                    d.dismiss()
                }
            })
        }
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
    }

    // ---------- widoki ----------

    /** Przełącznik: szary „WŁĄCZ” po lewej → zielony „WŁĄCZONE” po prawej. */
    inner class Toggle : FrameLayout(this@MainActivity) {
        var onToggle: ((Boolean) -> Unit)? = null
        private var on = false
        private val knob = TextView(context).apply {
            gravity = Gravity.CENTER
            typeface = fBold
        }

        init {
            background = box(FIELD, BORDER, 2, 4)
            addView(knob, LayoutParams(dp(124), LayoutParams.MATCH_PARENT))
            setOnClickListener { onToggle?.invoke(!on) }
            paint(false)
        }

        fun set(value: Boolean, animate: Boolean = true) {
            on = value
            paint(value)
            post {
                val x = if (value) (width - knob.width).toFloat() else 0f
                if (animate) knob.animate().translationX(x).setDuration(180).start()
                else knob.translationX = x
            }
        }

        private fun paint(value: Boolean) {
            knob.text = if (value) "WŁĄCZONE" else "WŁĄCZ"
            knob.textSize = if (value) 16f else 20f
            knob.setTextColor(if (value) GREEN_BORDER else GREY_TEXT)
            knob.background = if (value) box(GREEN_BG, GREEN_BORDER, 3, 4) else box(GREY_BG, GREY_BORDER, 3, 4)
        }
    }

    /** Niebieskie koło z trzema białymi kropkami. */
    class Dots(c: Context) : View(c) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D5A8F.toInt() }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        init { isClickable = true }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) / 2f
            canvas.drawCircle(cx, cy, r, bg)
            val dr = r * 0.16f; val gap = r * 0.42f
            for (i in -1..1) canvas.drawCircle(cx, cy + i * gap, dr, dot)
        }
    }

    // ---------- helpery ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun box(fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(strokeDp), stroke); cornerRadius = dp(radiusDp).toFloat()
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
