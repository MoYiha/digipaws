package neth.iecal.curbox.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import neth.iecal.curbox.R

object ViewUtils {
    fun showHelpPopup(anchor: View, text: String) {
        val context = anchor.context
        val popupView = LayoutInflater.from(context).inflate(R.layout.layout_help_tooltip, null)
        val textView = popupView.findViewById<TextView>(R.id.tv_help_text)
        textView.text = text

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        
        popupWindow.elevation = 8f
        popupWindow.showAsDropDown(anchor, 0, 0)
    }
}
