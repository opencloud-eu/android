package eu.opencloud.android.presentation.files.addtohomescreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import eu.opencloud.android.R

class ShortcutPinResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FolderShortcutHelper.ACTION_PIN_SHORTCUT_RESULT) return
        Toast.makeText(
            context,
            context.getString(R.string.add_to_home_screen_shortcut_added),
            Toast.LENGTH_SHORT
        ).show()
    }
}
