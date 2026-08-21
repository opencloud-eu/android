package eu.opencloud.android.presentation.files.addtohomescreen

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import eu.opencloud.android.R
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.ui.activity.FileDisplayActivity

object FolderShortcutHelper {

    const val EXTRA_SHORTCUT_FOLDER_REMOTE_ID = "SHORTCUT_FOLDER_REMOTE_ID"
    const val EXTRA_SHORTCUT_FOLDER_REMOTE_PATH = "SHORTCUT_FOLDER_REMOTE_PATH"
    const val EXTRA_SHORTCUT_FOLDER_SPACE_ID = "SHORTCUT_FOLDER_SPACE_ID"

    fun createPinnedShortcut(context: Context, folder: OCFile, shortcutName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createPinnedShortcutApi26(context, folder, shortcutName)
        } else {
            Toast.makeText(context, context.getString(R.string.add_to_home_screen_shortcut_added), Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPinnedShortcutApi26(context: Context, folder: OCFile, shortcutName: String) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)

        if (shortcutManager?.isRequestPinShortcutSupported != true) {
            Toast.makeText(context, context.getString(R.string.add_to_home_screen_shortcut_added), Toast.LENGTH_SHORT).show()
            return
        }

        val shortcutId = "folder_${folder.id}"

        val shortcutIntent = Intent(context, FileDisplayActivity::class.java).apply {
            action = ACTION_OPEN_SHORTCUT
            putExtra(EXTRA_SHORTCUT_FOLDER_REMOTE_ID, folder.remoteId)
            putExtra(EXTRA_SHORTCUT_FOLDER_REMOTE_PATH, folder.remotePath)
            putExtra(EXTRA_SHORTCUT_FOLDER_SPACE_ID, folder.spaceId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val shortcut = ShortcutInfo.Builder(context, shortcutId)
            .setShortLabel(shortcutName)
            .setLongLabel(shortcutName)
            .setIcon(Icon.createWithResource(context, R.mipmap.icon))
            .setIntent(shortcutIntent)
            .build()

        shortcutManager.requestPinShortcut(shortcut, null)
        Toast.makeText(context, context.getString(R.string.add_to_home_screen_shortcut_added), Toast.LENGTH_SHORT).show()
    }

    const val ACTION_OPEN_SHORTCUT = "eu.opencloud.android.ui.activity.action.OPEN_SHORTCUT"
}
