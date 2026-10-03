package eu.opencloud.android.presentation.files.addtohomescreen

import android.content.Context
import android.content.Intent
import android.content.pm.PinShortcutInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.opencloud.android.R
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.ui.activity.FileDisplayActivity

object FolderShortcutHelper {

    const val EXTRA_SHORTCUT_FOLDER_REMOTE_ID = "SHORTCUT_FOLDER_REMOTE_ID"
    const val EXTRA_SHORTCUT_FOLDER_REMOTE_PATH = "SHORTCUT_FOLDER_REMOTE_PATH"
    const val EXTRA_SHORTCUT_FOLDER_SPACE_ID = "SHORTCUT_FOLDER_SPACE_ID"
    const val ACTION_OPEN_SHORTCUT =
        "eu.opencloud.android.ui.activity.action.OPEN_SHORTCUT"

    fun createPinnedShortcut(context: Context, folder: OCFile, shortcutName: String) {
        if (shortcutName.isBlank()) {
            showMessage(context, R.string.add_to_home_screen_dialog_error_empty)
            return
        }

        val shortcutManager = context.getSystemService(ShortcutManager::class.java)

        if (shortcutManager?.isRequestPinShortcutSupported != true) {
            showMessage(context, R.string.add_to_home_screen_shortcut_not_supported)
            return
        }

        PinShortcutInfo.requestPinShortcut(context, null).requestPinShortcut(
            shortcutManager,
            buildShortcutInfo(context, folder, shortcutName),
            ContextCompat.getMainExecutor(context),
            object : PinShortcutInfo.CallbackHandler() {
                override fun onResultShortcutAdded(resultInfo: PinShortcutInfo) {
                    showMessage(context, R.string.add_to_home_screen_shortcut_added)
                }

                override fun onResultShortcutFailed(resultInfo: PinShortcutInfo) {
                    showMessage(context, R.string.add_to_home_screen_shortcut_add_failed)
                }
            }
        )
    }

    private fun buildShortcutInfo(context: Context, folder: OCFile, shortcutName: String): ShortcutInfo {
        val shortcutId = "folder_${folder.id}"

        val shortcutIntent = Intent(context, FileDisplayActivity::class.java).apply {
            action = ACTION_OPEN_SHORTCUT
            putExtra(EXTRA_SHORTCUT_FOLDER_REMOTE_ID, folder.remoteId)
            putExtra(EXTRA_SHORTCUT_FOLDER_REMOTE_PATH, folder.remotePath)
            putExtra(EXTRA_SHORTCUT_FOLDER_SPACE_ID, folder.spaceId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return ShortcutInfo.Builder(context, shortcutId)
            .setShortLabel(shortcutName)
            .setLongLabel(shortcutName)
            .setIcon(Icon.createWithResource(context, R.mipmap.icon))
            .setIntent(shortcutIntent)
            .build()
    }

    private fun showMessage(context: Context, messageRes: Int) {
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    }
}
