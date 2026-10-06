package eu.opencloud.android.presentation.files.addtohomescreen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import eu.opencloud.android.ui.activity.FileDisplayActivity

class ShortcutTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forwardShortcutIntent()
        finish()
    }

    private fun forwardShortcutIntent() {
        val shortcutIntent = intent ?: return
        if (shortcutIntent.action != FolderShortcutHelper.ACTION_OPEN_SHORTCUT) return

        val target = Intent(this, FileDisplayActivity::class.java).apply {
            action = FolderShortcutHelper.ACTION_OPEN_SHORTCUT
            copyStringExtra(shortcutIntent, FolderShortcutHelper.EXTRA_SHORTCUT_FOLDER_REMOTE_ID)
            copyStringExtra(shortcutIntent, FolderShortcutHelper.EXTRA_SHORTCUT_FOLDER_REMOTE_PATH)
            copyStringExtra(shortcutIntent, FolderShortcutHelper.EXTRA_SHORTCUT_FOLDER_SPACE_ID)
            copyStringExtra(shortcutIntent, FolderShortcutHelper.EXTRA_SHORTCUT_FOLDER_ACCOUNT)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(target)
    }

    private fun Intent.copyStringExtra(source: Intent, key: String) {
        source.getStringExtra(key)?.let { putExtra(key, it) }
    }
}
