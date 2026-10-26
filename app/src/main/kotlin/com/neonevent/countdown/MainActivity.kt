package com.neonevent.countdown

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var prefs: CountdownPrefs
    private lateinit var countdownView: NeonCountdownView
    private lateinit var targetSummary: TextView
    private lateinit var root: LinearLayout
    private val backgroundColor = Color.rgb(5, 8, 24)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = CountdownPrefs(this)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        buildUi()
        applyFullscreen()
        CountdownAlarmReceiver.schedule(this)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(backgroundColor)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(18), dp(16), dp(20))
            setBackgroundColor(backgroundColor)
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        heading.addView(TextView(this).apply {
            text = "ספירה לאחור לאירוע"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        heading.addView(TextView(this).apply {
            text = "הזמן שלך. הרגע שלך."
            textSize = 14f
            setTextColor(0xFF9BAECF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(heading)

        countdownView = NeonCountdownView(this)
        countdownView.setBackground(roundBackground(0xFF080D20.toInt(), 0x6636DFFF.toInt(), 18))
        root.addView(countdownView, LinearLayout.LayoutParams(-1, dp(295)).apply {
            topMargin = dp(4)
            bottomMargin = dp(10)
        })

        targetSummary = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFB4C5E4.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(12))
        }
        root.addView(targetSummary, LinearLayout.LayoutParams(-1, -2))
        refreshSummary()

        addButtonRow(
            button("📅  מועד האירוע") { chooseDate() },
            button("⚙  עיצוב והגדרות") { showSettingsMenu() }
        )
        addButtonRow(
            button("✦  שומר מסך") { openDreamSettings() },
            button("◉  רקע חי") { openWallpaperPicker() }
        )
        root.addView(TextView(this).apply {
            text = "הנתונים נשמרים במכשיר בלבד • ללא חיבור לרשת"
            setTextColor(0xFF627392.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        }, LinearLayout.LayoutParams(-1, -2))
        countdownView.invalidate()

        root.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            val left: Int
            val right: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = bars.bottom
                left = bars.left
                right = bars.right
            } else {
                @Suppress("DEPRECATION")
                run {
                    top = insets.systemWindowInsetTop
                    bottom = insets.systemWindowInsetBottom
                    left = insets.systemWindowInsetLeft
                    right = insets.systemWindowInsetRight
                }
            }
            view.setPadding(dp(16) + left, dp(18) + top, dp(16) + right, dp(20) + bottom)
            insets
        }
        scroll.isFocusableInTouchMode = true
        setContentView(scroll)
    }

    private fun addButtonRow(first: Button, second: Button) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        row.addView(first, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        row.addView(second, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(6) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = roundBackground(0xFF101B34.toInt(), 0xAA25D9FF.toInt(), 14)
        minHeight = dp(48)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun roundBackground(fill: Int, stroke: Int, radiusDp: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun chooseDate() {
        val current = runCatching { LocalDate.parse(prefs.targetDate) }.getOrDefault(LocalDate.now())
        DatePickerDialog(this, { _, year, month, day ->
            val picked = LocalDate.of(year, month + 1, day)
            AlertDialog.Builder(this)
                .setTitle("איך לחשב את הספירה?")
                .setItems(arrayOf("תאריך בלבד — בלי שעה", "תאריך ושעה מדויקת")) { _, choice ->
                    if (choice == 0) {
                        prefs.targetDate = picked.toString()
                        prefs.ignoreTime = true
                        prefs.targetHour = 0
                        prefs.targetMinute = 0
                        afterSettingsChanged()
                    } else {
                        TimePickerDialog(this, { _, hour, minute ->
                            prefs.targetDate = picked.toString()
                            prefs.targetHour = hour
                            prefs.targetMinute = minute
                            prefs.ignoreTime = false
                            afterSettingsChanged()
                        }, prefs.targetHour, prefs.targetMinute, true).show()
                    }
                }.show()
        }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
            datePicker.minDate = LocalDate.of(2000, 1, 1).toEpochDay() * 86_400_000L
        }.show()
    }

    private fun showSettingsMenu() {
        val items = arrayOf(
            "כותרת האירוע",
            if (prefs.showTitle) "הסתרת הכותרת" else "הצגת הכותרת",
            "בחירת יחידות זמן",
            "ערכת צבעי ניאון",
            "צבע מוביל",
            "עוצמת זוהר",
            "מהירות אנימציה",
            "מה קורה בסיום?",
            if (prefs.soundEnabled) "השתקת צליל סיום" else "הפעלת צליל סיום",
            if (prefs.fullScreen) "יציאה ממסך מלא" else "הפעלת מסך מלא",
            "הוראות לשומר מסך",
            "הוראות לרקע חי",
            "איפוס הגדרות"
        )
        AlertDialog.Builder(this)
            .setTitle("עיצוב והגדרות")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> editTitle()
                    1 -> { prefs.showTitle = !prefs.showTitle; afterSettingsChanged() }
                    2 -> chooseUnits()
                    3 -> chooseTheme()
                    4 -> chooseAccent()
                    5 -> chooseGlow()
                    6 -> chooseAnimationSpeed()
                    7 -> chooseCompletion()
                    8 -> {
                        prefs.soundEnabled = !prefs.soundEnabled
                        CountdownAlarmReceiver.schedule(this)
                        afterSettingsChanged()
                        toast(if (prefs.soundEnabled) "צליל סיום הופעל" else "צליל הסיום הושתק")
                    }
                    9 -> {
                        prefs.fullScreen = !prefs.fullScreen
                        applyFullscreen()
                        afterSettingsChanged()
                    }
                    10 -> openDreamSettings()
                    11 -> openWallpaperPicker()
                    12 -> confirmReset()
                }
            }.show()
    }

    private fun editTitle() {
        val input = EditText(this).apply {
            setText(prefs.eventTitle)
            setSingleLine(true)
            hint = "שם האירוע"
            textDirection = View.TEXT_DIRECTION_RTL
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("כותרת אישית")
            .setView(input)
            .setPositiveButton("שמירה") { _, _ ->
                prefs.eventTitle = input.text.toString().trim().ifBlank { "הרגע הגדול מתקרב" }
                afterSettingsChanged()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun chooseUnits() {
        val names = arrayOf("ימים", "שעות", "דקות", "שניות")
        val checked = BooleanArray(4) { prefs.isUnitVisible(it) }
        AlertDialog.Builder(this)
            .setTitle("אילו יחידות להציג?")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("שמירה") { _, _ ->
                var mask = 0
                checked.forEachIndexed { index, enabled -> if (enabled) mask = mask or (1 shl index) }
                if (mask == 0) {
                    toast("יש לבחור לפחות יחידת זמן אחת")
                } else {
                    prefs.unitsMask = mask
                    afterSettingsChanged()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun chooseTheme() {
        AlertDialog.Builder(this)
            .setTitle("ערכת צבעי ניאון")
            .setSingleChoiceItems(CountdownPrefs.themeNames.toTypedArray(), prefs.theme) { dialog, which ->
                prefs.theme = which
                dialog.dismiss()
                afterSettingsChanged()
            }
            .setNegativeButton("סגירה", null)
            .show()
    }

    private fun chooseAccent() {
        AlertDialog.Builder(this)
            .setTitle("צבע מוביל")
            .setSingleChoiceItems(CountdownPrefs.accentNames.toTypedArray(), prefs.accent) { dialog, which ->
                prefs.accent = which
                dialog.dismiss()
                afterSettingsChanged()
            }
            .setNegativeButton("סגירה", null)
            .show()
    }

    private fun chooseGlow() {
        val seek = SeekBar(this).apply { max = 100; progress = prefs.glow }
        val label = TextView(this).apply {
            text = "עוצמת זוהר: " + prefs.glow + "%"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 0, 0, dp(8))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(4))
            addView(label)
            addView(seek)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { label.text = "עוצמת זוהר: " + value + "%" }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        AlertDialog.Builder(this)
            .setTitle("עוצמת זוהר")
            .setView(container)
            .setPositiveButton("שמירה") { _, _ -> prefs.glow = seek.progress; afterSettingsChanged() }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun chooseAnimationSpeed() {
        val names = arrayOf("ללא אנימציה", "איטית", "רגילה", "מהירה")
        AlertDialog.Builder(this)
            .setTitle("מהירות אנימציה")
            .setSingleChoiceItems(names, prefs.animationSpeed) { dialog, which ->
                prefs.animationSpeed = which
                dialog.dismiss()
                afterSettingsChanged()
            }
            .setNegativeButton("סגירה", null)
            .show()
    }

    private fun chooseCompletion() {
        val names = arrayOf("הצגת אפסים", "הודעת סיום", "אפקט זוהר")
        AlertDialog.Builder(this)
            .setTitle("כשהספירה מגיעה לאפס")
            .setSingleChoiceItems(names, prefs.completionMode) { dialog, which ->
                prefs.completionMode = which
                dialog.dismiss()
                afterSettingsChanged()
            }
            .setNegativeButton("סגירה", null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("איפוס הגדרות")
            .setMessage("האם לאפס את התאריך, הכותרת והעיצוב?")
            .setPositiveButton("איפוס") { _, _ ->
                prefs.reset()
                CountdownAlarmReceiver.schedule(this)
                applyFullscreen()
                afterSettingsChanged()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun openDreamSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            showUnsupported("המכשיר לא מספק מסך הגדרות לשומר מסך. אפשר לבדוק בהגדרות המערכת אם קיימת אפשרות בשם „שומר מסך” או „Dream”.")
        } catch (_: Exception) {
            showUnsupported("לא ניתן לפתוח את הגדרות שומר המסך במכשיר הזה.")
        }
    }

    private fun openWallpaperPicker() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, CountdownWallpaperService::class.java)
            )
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                showUnsupported("המכשיר לא מספק בורר רקעים חיים. ייתכן שהיצרן אינו תומך בתכונה הזו.")
            }
        } catch (_: Exception) {
            showUnsupported("לא ניתן לפתוח את בורר הרקעים החיים במכשיר הזה.")
        }
    }

    private fun showUnsupported(message: String) {
        AlertDialog.Builder(this).setTitle("אפשרות מערכת").setMessage(message).setPositiveButton("הבנתי", null).show()
    }

    private fun afterSettingsChanged() {
        refreshSummary()
        countdownView.invalidate()
        CountdownAlarmReceiver.schedule(this)
        applyFullscreen()
    }

    private fun refreshSummary() {
        val date = runCatching {
            LocalDate.parse(prefs.targetDate).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))
        }.getOrDefault(prefs.targetDate)
        val time = String.format(Locale.ROOT, "%02d:%02d", prefs.targetHour, prefs.targetMinute)
        val target = if (prefs.ignoreTime) date + " • תאריך בלבד" else date + " • " + time
        targetSummary.text = "מועד היעד: " + target
    }

    private fun applyFullscreen() {
        val flags = if (prefs.fullScreen) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        window.decorView.systemUiVisibility = flags
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::prefs.isInitialized && prefs.fullScreen) applyFullscreen()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}