package com.neonevent.countdown

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
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
                scaleType = ImageView.ScaleType.CENTER_CROP
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
            val styles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (style in CountdownPrefs.CUBES_DARK..CountdownPrefs.CUBES_FROSTED) {
                val choice = CheckBox(this).apply {
                    text = CountdownPrefs.cubeNames[style]
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(if (prefs.cubeStyle == style) gold else 0xFFCAD4E2.toInt())
                    isChecked = prefs.cubeStyle == style
                    setOnClickListener {
                        prefs.cubeStyle = style
                        afterSettingsChanged()
                        buildSettingsScreen()
                    }
                }
                styles.addView(choice, LinearLayout.LayoutParams(-1, dp(44)))
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
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        root.addView(makeText("בחרו שנה, חודש ויום עבריים", 15f, ink, true))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val year = NumberPicker(this)
        val month = Spinner(this)
        row.addView(month, LinearLayout.LayoutParams(0, dp(54), 1f))
        row.addView(year, LinearLayout.LayoutParams(dp(110), dp(54)))
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        val selectedText = makeText("", 15f, ink, true).apply { gravity = Gravity.CENTER }
        root.addView(selectedText, LinearLayout.LayoutParams(-1, dp(34)))
        val grid = GridLayout(this).apply { columnCount = 7; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))

        var currentYear = prefs.targetHebrewYear
        var currentMonth = prefs.targetHebrewMonth
        var currentDay = prefs.targetHebrewDay
        var monthOptions = prefs.monthOptions(currentYear)
        var suppressMonth = false
        year.minValue = (HebrewCalendar(Locale("he", "IL")).get(Calendar.YEAR) - 1).coerceAtLeast(5700)
        year.maxValue = HebrewCalendar(Locale("he", "IL")).get(Calendar.YEAR) + 50
        year.value = currentYear.coerceIn(year.minValue, year.maxValue)
        year.displayedValues = (year.minValue..year.maxValue).map { hebrewYearLabel(it) }.toTypedArray()

        fun fillMonthOptions(preferred: Int) {
            monthOptions = prefs.monthOptions(currentYear)
            val idx = monthOptions.indexOfFirst { it.first == preferred }.takeIf { it >= 0 } ?: 0
            currentMonth = monthOptions[idx].first
            suppressMonth = true
            month.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthOptions.map { it.second })
            month.setSelection(idx, false)
            suppressMonth = false
        }

        fun drawDays() {
            grid.removeAllViews()
            val weekdays = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
            for (column in 0..6) {
                val label = makeText(weekdays[column], 12f, 0xFF475167.toInt(), true).apply { gravity = Gravity.CENTER }
                grid.addView(label, GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(column, 1f)).apply {
                    width = 0; height = dp(32)
                })
            }
            val firstDay = prefs.firstWeekdayOfHebrewMonth(currentYear, currentMonth)
            val maxDay = prefs.daysInHebrewMonth(currentYear, currentMonth)
            currentDay = currentDay.coerceIn(1, maxDay)
            selectedText.text = hebrewYearLabel(currentDay) + " ב" + (monthOptions.firstOrNull { it.first == currentMonth }?.second ?: "")
            for (index in 0 until firstDay + maxDay) {
                val rowIndex = index / 7 + 1
                val colIndex = index % 7
                val day = index - firstDay + 1
                val view = if (day < 1) View(this) else makeText(hebrewYearLabel(day), 14f,
                    if (day == currentDay) Color.WHITE else ink, day == currentDay).apply {
                    gravity = Gravity.CENTER
                    background = if (day == currentDay) roundBackground(0xFF273D5B.toInt(), gold, 20) else
                        roundBackground(Color.WHITE, 0xFFE2E6EF.toInt(), 20)
                    setOnClickListener {
                        currentDay = day
                        drawDays()
                    }
                }
                grid.addView(view, GridLayout.LayoutParams(GridLayout.spec(rowIndex), GridLayout.spec(colIndex, 1f)).apply {
                    width = 0; height = dp(39); setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
        }

        fillMonthOptions(currentMonth)
        drawDays()
        month.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressMonth || position !in monthOptions.indices) return
                currentMonth = monthOptions[position].first
                currentDay = currentDay.coerceAtMost(prefs.daysInHebrewMonth(currentYear, currentMonth))
                drawDays()
            }
        }
        year.setOnValueChangedListener { _, _, newValue ->
            val oldMonthName = monthOptions.firstOrNull { it.first == currentMonth }?.second
            currentYear = newValue
            val newOptions = prefs.monthOptions(currentYear)
            val preferred = newOptions.firstOrNull { it.second == oldMonthName }?.first ?: currentMonth
            fillMonthOptions(preferred)
            currentDay = currentDay.coerceAtMost(prefs.daysInHebrewMonth(currentYear, currentMonth))
            drawDays()
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
                afterSettingsChanged()
                dialog.dismiss()
                if (settingsShown) buildSettingsScreen()
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

    private fun hebrewYearLabel(year: Int): String {
        // Display the Hebrew-calendar year in Hebrew numerals, e.g. תשפ״ז.
        var rest = year % 1000
        val out = StringBuilder()
        while (rest >= 400) { out.append('ת'); rest -= 400 }
        when {
            rest >= 300 -> { out.append('ש'); rest -= 300 }
            rest >= 200 -> { out.append('ר'); rest -= 200 }
            rest >= 100 -> { out.append('ק'); rest -= 100 }
        }
        if (rest == 15) { out.append("טו"); rest = 0 }
        else if (rest == 16) { out.append("טז"); rest = 0 }
        else {
            val tens = when { rest >= 90 -> 90; rest >= 80 -> 80; rest >= 70 -> 70; rest >= 60 -> 60; rest >= 50 -> 50; rest >= 40 -> 40; rest >= 30 -> 30; rest >= 20 -> 20; rest >= 10 -> 10; else -> 0 }
            if (tens > 0) { out.append(when (tens) { 90 -> 'צ'; 80 -> 'פ'; 70 -> 'ע'; 60 -> 'ס'; 50 -> 'נ'; 40 -> 'מ'; 30 -> 'ל'; 20 -> 'כ'; else -> 'י' }); rest -= tens }
            if (rest > 0) out.append(when (rest) { 9 -> 'ט'; 8 -> 'ח'; 7 -> 'ז'; 6 -> 'ו'; 5 -> 'ה'; 4 -> 'ד'; 3 -> 'ג'; 2 -> 'ב'; else -> 'א' })
        }
        if (out.length == 1) out.append('׳') else if (out.length > 1) out.insert(out.length - 1, '״')
        return out.toString()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    @Deprecated("Back navigation routes to the home screen while settings are open.")
    override fun onBackPressed() {
        if (settingsShown) buildHomeScreen() else super.onBackPressed()
    }
}
