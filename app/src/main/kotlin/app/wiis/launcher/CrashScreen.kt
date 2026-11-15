@file:SuppressLint("SetTextI18n") // Wii-S isn't translated; its other text lives in Compose.

package app.wiis.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.system.exitProcess

/**
 * Shown on the top screen after a crash: what went wrong, and ways to send it. Plain Android views
 * with no theme, fonts or app state, so it can't fail the way the launcher did.
 */
object CrashScreen {
    private const val BACKGROUND = 0xFF15141C.toInt()
    private const val TEXT = 0xFFECEAF3.toInt()
    private const val MUTED = 0xFFA9A5B9.toInt()
    private const val BUTTON = 0xFF2A2935.toInt()
    private const val FOCUS = 0xFF3FD39A.toInt()

    fun show(activity: Activity, report: String) {
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(px(36), px(28), px(36), px(20))
        }
        root.addView(TextView(activity).apply {
            text = "Wii-S stopped"
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(activity).apply {
            text = "Wii-S hit an error the last time it opened. Please send this report so it can be fixed: share it, copy it, or save it to Downloads and attach the file. " +
                "If it keeps stopping, choose Top screen only: Wii-S starts without the bottom screen until the next update."
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, px(6), 0, px(16))
        })

        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, onClick: () -> Unit) = Button(activity).apply {
            text = label
            isAllCaps = false
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            minHeight = px(48)
            setPadding(px(20), 0, px(20), 0)
            background = GradientDrawable().apply { cornerRadius = px(24).toFloat(); setColor(BUTTON) }
            stateListAnimator = null
            isFocusable = true
            setOnFocusChangeListener { v, focused ->
                (v.background as GradientDrawable).setStroke(if (focused) px(3) else 0, FOCUS)
            }
            setOnClickListener { onClick() }
            // The controller's A button presses the highlighted button, as everywhere else in Wii-S.
            setOnKeyListener { v, code, event ->
                if (code != KeyEvent.KEYCODE_BUTTON_A) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) v.performClick()
                true
            }
            buttons.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(12) })
        }
        val share = button("Share report") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Wii-S crash report")
                .putExtra(Intent.EXTRA_TEXT, report)
            runCatching { activity.startActivity(Intent.createChooser(send, "Send the report")) }
                .onFailure { toast(activity, "No app here can share text. Try Copy or Save instead.") }
        }
        button("Copy") {
            activity.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Wii-S crash report", report))
            toast(activity, "Copied")
        }
        button("Save to Downloads") {
            runCatching { CrashLog.saveToDownloads(activity, report) }
                .onSuccess { toast(activity, "Saved to Downloads as \"$it\"") }
                .onFailure { toast(activity, "Couldn't save: ${it.message}") }
        }
        button("Start Wii-S") { restart(activity) }
        button("Top screen only") { CrashLog.pauseBottomScreen(activity); restart(activity) }
        root.addView(android.widget.HorizontalScrollView(activity).apply { addView(buttons); isHorizontalScrollBarEnabled = false })

        val trace = TextView(activity).apply {
            text = report
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, px(16), 0, px(8))
        }
        root.addView(ScrollView(activity).apply { addView(trace); isFillViewport = true }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        activity.setContentView(root)
        share.requestFocus()
    }

    /** Dismisses the report and starts the launcher in a fresh process. */
    private fun restart(activity: Activity) {
        CrashLog.clear(activity)
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) activity.startActivity(intent)
        activity.finishAffinity()
        exitProcess(0)
    }

    private fun toast(activity: Activity, text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    /** The bottom screen's note while the report is up. */
    fun showOnBottom(activity: Activity) {
        val dp = activity.resources.displayMetrics.density
        activity.setContentView(TextView(activity).apply {
            text = "Wii-S stopped last time.\nThe details are on the top screen."
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setBackgroundColor(BACKGROUND)
            setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
        })
    }
}
