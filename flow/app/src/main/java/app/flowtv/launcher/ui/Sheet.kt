package app.flowtv.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.flowtv.launcher.Palette
import app.flowtv.launcher.R
import app.flowtv.launcher.remote.Remote

/** A themed, remote-friendly pop-up list. Every menu and picker in Flow is one of these. */
class Sheet(private val act: Activity, private val pal: Palette, title: String, subtitle: String? = null) {
    private val dialog = Dialog(act, R.style.FlowSheet)
    private val list = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private var initial: View? = null
    var onDismiss: (() -> Unit)? = null

    init {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = chamfer(pal.raised, act.dp(14).toFloat(), act.dp(1), pal.line)
            setPadding(act.dp(22), act.dp(22), act.dp(16), act.dp(16))
        }
        box.addView(TextView(act).look(22f, pal.highlight).apply {
            heading(title, pal.accent, pal.highlight)
            setPadding(0, 0, act.dp(6), 0)
        })
        if (subtitle != null) box.addView(TextView(act).look(14f, pal.dim).apply {
            text = subtitle
            setPadding(0, act.dp(8), act.dp(6), 0)
            setLineSpacing(0f, 1.2f)
        })
        val scroll = FlowScroll(act).apply { maxHeight = act.dp(320) }
        scroll.addView(list)
        box.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = act.dp(14) },
        )
        dialog.setContentView(box)
        dialog.window?.apply {
            setLayout(act.dp(480), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(if (pal.dark) 0.6f else 0.3f)
        }
        // Remote buttons keep working (and can be taught) while a sheet is open.
        dialog.setOnKeyListener { _, _, e -> Remote.filter(act, e) }
        dialog.setOnDismissListener {
            if (open === this) open = null
            onDismiss?.invoke()
        }
    }

    fun item(
        text: String,
        detail: String? = null,
        icon: Drawable? = null,
        checked: Boolean = false,
        keepOpen: Boolean = false,
        onClick: () -> Unit,
    ): Sheet {
        val cut = act.dp(8).toFloat()
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            minimumHeight = act.dp(52)
            setPadding(act.dp(14), act.dp(9), act.dp(14), act.dp(9))
            background = focusBackground(chamfer(0, cut), chamfer(pal.focus, cut, edgeWidth = act.dp(4), edgeColor = pal.ring))
        }
        if (icon != null) row.addView(
            ImageView(act).apply { setImageDrawable(icon) },
            LinearLayout.LayoutParams(act.dp(30), act.dp(30)).apply { marginEnd = act.dp(14) },
        )
        val texts = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(act).look(16f, pal.text).apply {
            this.text = text
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        if (detail != null) texts.addView(TextView(act).look(13f, pal.dim).apply {
            this.text = detail
            setPadding(0, act.dp(4), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (checked) {
            row.addView(
                ImageView(act).apply { setImageDrawable(tinted(act, R.drawable.ic_check, pal.accent)) },
                LinearLayout.LayoutParams(act.dp(22), act.dp(22)).apply { marginStart = act.dp(10) },
            )
            if (initial == null) initial = row
        }
        row.setOnFocusChangeListener { v, f ->
            v.animate().setStartDelay(0).translationX(if (f) act.dp(6).toFloat() else 0f)
                .setDuration(220).setInterpolator(Motion.glide).start()
        }
        row.setOnClickListener {
            if (!keepOpen) dialog.dismiss()
            onClick()
        }
        list.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = act.dp(6) },
        )
        return this
    }

    fun note(text: String): Sheet {
        list.addView(TextView(act).look(14f, pal.dim).apply {
            this.text = text
            setPadding(act.dp(14), act.dp(10), act.dp(14), act.dp(10))
            setLineSpacing(0f, 1.25f)
        })
        return this
    }

    fun show(): Sheet {
        open?.dismiss()
        open = this
        dialog.show()
        (initial ?: (0 until list.childCount).map(list::getChildAt).firstOrNull { it.isFocusable })?.requestFocus()
        return this
    }

    fun dismiss() = dialog.dismiss()

    companion object {
        /** The sheet on screen, so Home can close it. */
        var open: Sheet? = null
            private set
    }
}
