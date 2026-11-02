package com.neonevent.countdown

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private lateinit var prefs: CountdownPrefs
    private var settingsShown = false
    private val imageRequestCode = 4902
    private val ink = Color.rgb(12, 17, 31)
    private val gold = Color.rgb(224, 185, 108)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = CountdownPrefs(this)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        buildHomeScreen()
    }

    override fun onResume() {
        super.onResume()
        // Recalculate immediately after returning from the background or system settings.
        window.decorView.invalidate()
        CountdownWidgetBase.updateAllWidgets(this)
        CountdownWidgetBase.scheduleBoundaries(this)
    }

    private fun buildHomeScreen() {
        settingsShown = false
        val frame = FrameLayout(this).apply { setBackgroundColor(ink); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        frame.addView(NeonCountdownView(this), FrameLayout.LayoutParams(-1, -1))

        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundBackground(0xD60B1422.toInt(), 0xB8FFFFFF.toInt(), 28)
            contentDescription = "הגדרות"
            elevation = dp(4).toFloat()
            setOnClickListener { buildSettingsScreen() }
        }
        // Use physical LEFT gravity, not START, so RTL layout never mirrors this button.
        frame.addView(settings, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.TOP or Gravity.LEFT).apply {
            topMargin = dp(18)
            leftMargin = dp(16)
        })
        setContentView(frame)
    }

    private fun buildSettingsScreen() {
        settingsShown = true
        val frame = FrameLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(ink) }
        val background = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(prefs.backgroundBitmap())
            alpha = 0.56f
        }
        frame.addView(background, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply { setBackgroundColor(0xA6070D1A.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(16), dp(32))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val back = makeButton("‹  חזרה") { buildHomeScreen() }
        header.addView(back, LinearLayout.LayoutParams(dp(110), dp(48)))
        val pageTitle = makeText("הגדרות", 25f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        header.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        addDesignSection(content)
        addEventSection(content)
        addDisplaySection(content)
        addAdditionalSection(content)
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
    }

    private fun addDesignSection(content: LinearLayout) {
        val card = settingsCard()
        card.addView(makeText("עיצוב", 20f, Color.WHITE, true))
        card.addView(makeText("בחרו רקע מלא ומוכן. הצבעים והקוביות מותאמים לכל ערכה.", 14f, 0xFFCBD4E5.toInt(), false),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val labels = CountdownPrefs.themeNames
        for (i in labels.indices) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(8))
                background = roundBackground(if (prefs.theme == i) 0xCC24314B.toInt() else 0x66232C40, if (prefs.theme == i) gold else 0x668D9AB0, 14)
                setOnClickListener {
                    prefs.theme = i
                    afterSettingsChanged()
                    buildSettingsScreen()
                }
            }
            val preview = ImageView(this).apply {
                setImageBitmap(prefs.backgroundBitmapForTheme(i))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = labels[i]
            }
            tile.addView(preview, LinearLayout.LayoutParams(-1, dp(116)))
            tile.addView(makeText(labels[i], 12.5f, Color.WHITE, prefs.theme == i).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i != 0) marginStart = dp(5)
                if (i != 2) marginEnd = dp(5)
            })
        }
        card.addView(row, LinearLayout.LayoutParams(-1, -2))

        card.addView(makeButton("▧  בחירת תמונה אישית מהמכשיר") { choosePersonalImage() },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        if (prefs.theme == CountdownPrefs.THEME_PERSONAL) {
            card.addView(makeText("התמונה האישית פעילה. אפשר לבחור לה מראה לקוביות:", 14f, Color.WHITE, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            val styles = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            for (style in CountdownPrefs.CUBES_DARK..CountdownPrefs.CUBES_AUTO) {
                val colors = when (style) {
                    CountdownPrefs.CUBES_LIGHT -> Quadruple(
                        0xE8FFFFFF.toInt(), 0xD0FFFFFF.toInt(),
                        0xFF182033.toInt(), 0xFF31394B.toInt()
                    )
                    CountdownPrefs.CUBES_FROSTED -> Quadruple(
                        0xBFF7F8FC.toInt(), 0xDDFEFFFF.toInt(),
                        0xFF182033.toInt(), 0xFF303849.toInt()
                    )
                    CountdownPrefs.CUBES_AUTO -> if (prefs.personalAutoContrastIsDark()) Quadruple(
                        0xE8FFFFFF.toInt(), 0xD0FFFFFF.toInt(),
                        0xFF182033.toInt(), 0xFF31394B.toInt()
                    ) else Quadruple(
                        0xD9151D2B.toInt(), 0xB8FFFFFF.toInt(),
                        Color.WHITE, 0xFFE8EDF7.toInt()
                    )
                    else -> Quadruple(
                        0xD9151D2B.toInt(), 0xB8FFFFFF.toInt(),
                        Color.WHITE, 0xFFE8EDF7.toInt()
                    )
                }
                val fill = colors.first
                val stroke = colors.second
                val numberColor = colors.third
                val labelColor = colors.fourth
                val selected = prefs.cubeStyle == style
                val option = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(3), dp(4), dp(3), dp(4))
                    background = roundBackground(
                        0x66232C40, if (selected) gold else 0x668D9AB0, 12
                    )
                    setOnClickListener {
                        prefs.cubeStyle = style
                        afterSettingsChanged()
                        buildSettingsScreen()
                    }
                }
                val preview = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = roundBackground(fill, stroke, 10)
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                }
                preview.addView(
                    makeText("123", 15f, numberColor, true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, dp(28))
                )
                preview.addView(
                    makeText("ימים", 10f, labelColor, true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, dp(20))
                )
                option.addView(preview, LinearLayout.LayoutParams(-1, dp(50)))
                option.addView(
                    makeText(CountdownPrefs.cubeNames[style], 11.5f, Color.WHITE, selected).apply {
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(-1, dp(25))
                )
                styles.addView(option, LinearLayout.LayoutParams(0, dp(86), 1f).apply {
                    if (style != CountdownPrefs.CUBES_DARK) marginStart = dp(4)
                    if (style != CountdownPrefs.CUBES_FROSTED) marginEnd = dp(4)
                })
            }
            card.addView(styles)
            card.addView(makeButton("חזרה לרקע מובנה") { prefs.theme = CountdownPrefs.THEME_GOLD; afterSettingsChanged(); buildSettingsScreen() },
                LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
        }
        content.addView(card, cardLayoutParams())
    }

    private fun addEventSection(content: LinearLayout) {
        val card = settingsCard()
        card.addView(makeText("האירוע", 20f, Color.WHITE, true))
        card.addView(makeText("שם האירוע, הכותרת ותאריך היעד", 14f, 0xFFCBD4E5.toInt(), false),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(10) })
        val titleInput = EditText(this).apply {
            setText(prefs.eventTitle)
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9AA8BF.toInt())
            hint = "כותרת אישית לאירוע"
            textDirection = View.TEXT_DIRECTION_RTL
            background = roundBackground(0xAA101928.toInt(), 0x668E9CB4, 12)
            setPadding(dp(12), 0, dp(12), 0)
        }
        card.addView(titleInput, LinearLayout.LayoutParams(-1, dp(50)))
        val saveTitle = makeButton("שמירת הכותרת") {
            prefs.eventTitle = titleInput.text.toString().trim().ifBlank { "הרגע הגדול מתקרב" }
            afterSettingsChanged()
            toast("הכותרת נשמרה")
        }
        card.addView(saveTitle, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        val showTitleSwitch = Switch(this).apply {
            text = "הצגת כותרת האירוע במסך הראשי"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = prefs.showTitle
            buttonTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnCheckedChangeListener { _, checked ->
                prefs.showTitle = checked
                afterSettingsChanged()
            }
        }
        card.addView(showTitleSwitch, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        val targetButton = makeButton("תאריך האירוע: " + prefs.formattedTargetDate()) { showHebrewDatePicker() }
        card.addView(targetButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        content.addView(card, cardLayoutParams())
    }

    private fun addDisplaySection(content: LinearLayout) {
        val card = settingsCard()
        card.addView(makeText("תצוגת הספירה", 20f, Color.WHITE, true))
        card.addView(makeText("בחרו אילו יחידות זמן יוצגו בקוביות.", 14f, 0xFFCBD4E5.toInt(), false),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) })
        val names = listOf("ימים", "שעות", "דקות", "שניות")
        names.forEachIndexed { index, label ->
            val check = CheckBox(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                isChecked = prefs.isUnitVisible(index)
                buttonTintList = android.content.res.ColorStateList.valueOf(gold)
                setOnCheckedChangeListener { button, checked ->
                    var mask = prefs.unitsMask
                    mask = if (checked) mask or (1 shl index) else mask and (1 shl index).inv()
                    if (mask == 0) {
                        button.isChecked = true
                        toast("יש להשאיר לפחות יחידת זמן אחת")
                    } else {
                        prefs.unitsMask = mask
                        afterSettingsChanged()
                    }
                }
            }
            card.addView(check, LinearLayout.LayoutParams(-1, dp(40)))
        }
        content.addView(card, cardLayoutParams())
    }

    private fun addAdditionalSection(content: LinearLayout) {
        val card = settingsCard()
        card.addView(makeText("תצוגה נוספת", 20f, Color.WHITE, true))
        card.addView(makeText("אפשר להגדיר את הספירה כשומר מסך או כרקע חי, בהתאם לתמיכת המכשיר.", 14f, 0xFFCBD4E5.toInt(), false),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(10) })
        card.addView(makeButton("✦  הגדרות שומר מסך") { openDreamSettings() },
            LinearLayout.LayoutParams(-1, dp(50)))
        card.addView(makeButton("▣  הגדרות טפט חי") { openWallpaperPicker() },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
        card.addView(makeText("עדכון שעון הווידג׳ט", 17f, Color.WHITE, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        card.addView(makeText(
            if (exactAllowed) "הרשאת התזמון המדויק זמינה. Android עדיין עשוי לעכב אזעקות במצבי מערכת מסוימים."
            else "כדי לשפר את דיוק המעברים בין תצוגות השעון וחצות, אפשר לאשר גישה מיוחדת „התראות ותזכורות”. ללא האישור Android עשוי לדחות את העדכונים.",
            13.5f, 0xFFCBD4E5.toInt(), false
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        card.addView(makeButton(if (exactAllowed) "בדיקת הרשאת התזמון" else "פתיחת Alarms & reminders") {
            requestExactAlarmAccess()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })

        card.addView(makeText("ווידג׳טים למסך הבית", 17f, Color.WHITE, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        card.addView(makeText("לחצו לחיצה ארוכה על מסך הבית, בחרו „ווידג׳טים” והוסיפו „ספירה עם כותרת” או „ספירה ללא כותרת”. העדכון מתבצע בקירוב פעם בדקה בהתאם למדיניות Android.", 14f, 0xFFCBD4E5.toInt(), false),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        content.addView(card, cardLayoutParams())
    }

    private fun showHebrewDatePicker() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        root.addView(makeText("בחרו יום, חודש ושנה עבריים", 16f, ink, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val wheels = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val dayPicker = NumberPicker(this).apply {
            contentDescription = "יום עברי"
            wrapSelectorWheel = true
        }
        val monthPicker = NumberPicker(this).apply {
            contentDescription = "חודש עברי"
            wrapSelectorWheel = true
        }
        val yearPicker = NumberPicker(this).apply {
            contentDescription = "שנה עברית"
            wrapSelectorWheel = false
        }
        wheels.addView(dayPicker, LinearLayout.LayoutParams(0, dp(176), 0.85f))
        wheels.addView(monthPicker, LinearLayout.LayoutParams(0, dp(176), 1.2f))
        wheels.addView(yearPicker, LinearLayout.LayoutParams(0, dp(176), 1.05f))
        root.addView(wheels, LinearLayout.LayoutParams(-1, -2))

        val preview = makeText("", 16f, ink, true).apply { gravity = Gravity.CENTER }
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) })

        var currentYear = prefs.targetHebrewYear
        var currentMonth = prefs.targetHebrewMonth
        var currentDay = prefs.targetHebrewDay
        var monthOptions = emptyList<Pair<Int, String>>()
        var suppressWheelEvents = true

        val currentHebrewYear = prefs.currentHebrewYear()
        yearPicker.minValue = (currentHebrewYear - 1).coerceAtLeast(5700)
        yearPicker.maxValue = currentHebrewYear + 50
        yearPicker.displayedValues = (yearPicker.minValue..yearPicker.maxValue)
            .map { prefs.hebrewYearLabel(it) }.toTypedArray()
        yearPicker.value = currentYear.coerceIn(yearPicker.minValue, yearPicker.maxValue)
        currentYear = yearPicker.value

        fun updatePreview() {
            currentDay = dayPicker.value
            preview.text = prefs.formatHebrewDate(currentYear, currentMonth, currentDay)
        }

        fun updateDayOptions(preferredDay: Int) {
            dayPicker.displayedValues = null
            dayPicker.minValue = 1
            dayPicker.maxValue = prefs.daysInHebrewMonth(currentYear, currentMonth)
            dayPicker.displayedValues = prefs.hebrewDayOptions(currentYear, currentMonth)
                .map { prefs.hebrewNumber(it) }.toTypedArray()
            dayPicker.value = preferredDay.coerceIn(dayPicker.minValue, dayPicker.maxValue)
            currentDay = dayPicker.value
        }

        fun updateMonthOptions(preferredMonth: Int) {
            monthOptions = prefs.monthOptions(currentYear)
            val selectedMonth = when {
                monthOptions.any { it.first == preferredMonth } -> preferredMonth
                preferredMonth == CountdownPrefs.ADAR_1 ->
                    CountdownPrefs.ADAR.takeIf { candidate -> monthOptions.any { it.first == candidate } }
                        ?: monthOptions.first().first
                else -> monthOptions.first().first
            }
            monthPicker.displayedValues = null
            monthPicker.minValue = 0
            monthPicker.maxValue = monthOptions.lastIndex
            monthPicker.displayedValues = monthOptions.map { it.second }.toTypedArray()
            monthPicker.value = monthOptions.indexOfFirst { it.first == selectedMonth }.coerceAtLeast(0)
            currentMonth = monthOptions[monthPicker.value].first
        }

        suppressWheelEvents = true
        updateMonthOptions(currentMonth)
        updateDayOptions(currentDay)
        updatePreview()
        suppressWheelEvents = false

        yearPicker.setOnValueChangedListener { _, _, newValue ->
            if (suppressWheelEvents) return@setOnValueChangedListener
            val oldMonth = currentMonth
            suppressWheelEvents = true
            currentYear = newValue
            updateMonthOptions(oldMonth)
            updateDayOptions(currentDay)
            updatePreview()
            suppressWheelEvents = false
        }
        monthPicker.setOnValueChangedListener { _, _, newValue ->
            if (suppressWheelEvents || newValue !in monthOptions.indices) return@setOnValueChangedListener
            suppressWheelEvents = true
            currentMonth = monthOptions[newValue].first
            updateDayOptions(currentDay)
            updatePreview()
            suppressWheelEvents = false
        }
        dayPicker.setOnValueChangedListener { _, _, newValue ->
            if (suppressWheelEvents) return@setOnValueChangedListener
            currentDay = newValue
            updatePreview()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("בחירת תאריך עברי")
            .setView(root)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("שמירת תאריך", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                prefs.setHebrewDate(currentYear, currentMonth, currentDay)
                CountdownWidgetBase.updateAllWidgets(this)
                dialog.dismiss()
                if (settingsShown) buildSettingsScreen() else buildHomeScreen()
            }
        }
        dialog.show()
    }

    private fun choosePersonalImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { startActivityForResult(intent, imageRequestCode) }
        catch (_: Exception) { toast("לא ניתן לפתוח את בחירת התמונות במכשיר הזה") }
    }

    @Deprecated("Uses the system document picker to retain offline access to the selected image.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != imageRequestCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.personalImageUri = uri.toString()
        prefs.theme = CountdownPrefs.THEME_PERSONAL
        prefs.backgroundBitmap()
        afterSettingsChanged()
        if (settingsShown) buildSettingsScreen() else buildHomeScreen()
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            toast("בגרסת Android זו אין צורך בהרשאה המיוחדת")
            return
        }
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            toast("ההרשאה כבר מאושרת")
            CountdownWidgetBase.scheduleBoundaries(this)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("גישה מיוחדת: התראות ותזכורות")
            .setMessage("ההרשאה מאפשרת לתזמן את שלושת המעברים החשובים של שעון הווידג׳ט. היא אינה משמשת למטרה אחרת. אם לא תאשר, השעון ימשיך לפעול אך Android עשוי לעכב את שינויי התצוגה ואת ההסתרה בחצות.")
            .setNegativeButton("לא עכשיו", null)
            .setPositiveButton("פתיחת הגדרות") { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + packageName)))
                }.onFailure {
                    unsupported("לא ניתן לפתוח את מסך Alarms & reminders במכשיר הזה. אפשר למצוא את האפליקציה בהגדרות > אפליקציות > גישה מיוחדת.")
                }
            }.show()
    }

    private fun openDreamSettings() {
        try { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
        catch (_: ActivityNotFoundException) { unsupported("המכשיר לא מציג הגדרה לשומר מסך. בדקו בהגדרות המערכת אם האפשרות קיימת.") }
        catch (_: Exception) { unsupported("לא ניתן לפתוח את הגדרות שומר המסך במכשיר הזה.") }
    }

    private fun openWallpaperPicker() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, CountdownWallpaperService::class.java)))
        } catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            catch (_: Exception) { unsupported("המכשיר לא מספק בורר לטפטים חיים.") }
        } catch (_: Exception) { unsupported("לא ניתן לפתוח את בורר הטפטים החיים במכשיר הזה.") }
    }

    private fun unsupported(message: String) {
        AlertDialog.Builder(this).setTitle("אפשרות מערכת").setMessage(message).setPositiveButton("הבנתי", null).show()
    }

    private fun afterSettingsChanged() {
        CountdownWidgetBase.updateAllWidgets(this)
        if (!settingsShown) buildHomeScreen()
    }

    private fun settingsCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = roundBackground(0xE00D1627.toInt(), 0x667A8EAC, 20)
    }

    private fun cardLayoutParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }

    private fun makeText(textValue: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun makeButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = roundBackground(0xFF18263D.toInt(), 0x887F9ABC.toInt(), 13)
        minHeight = dp(44)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun roundBackground(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    @Deprecated("Back navigation routes to the home screen while settings are open.")
    override fun onBackPressed() {
        if (settingsShown) buildHomeScreen() else super.onBackPressed()
    }
}
