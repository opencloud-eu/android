/**
 * openCloud Android client application
 *
 * @author David A. Velasco
 * Copyright (C) 2016 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import eu.opencloud.android.R;
import timber.log.Timber;

/**
 * Activity copying the text of the received Intent into the system clipboard.
 */
@SuppressWarnings("deprecation")
public class CopyToClipboardActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {

            // get the clipboard system service
            ClipboardManager clipboardManager = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);

            // get the text to copy into the clipboard
            Intent intent = getIntent();
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);

            if (text != null && text.length() > 0) {
                // minimum API level >= 11 -> only modern Clipboard
                ClipData clip = ClipData.newPlainText(
                        getString(R.string.clipboard_label, getString(R.string.app_name)),
                        text
                );
                clipboardManager.setPrimaryClip(clip);

                // API level < 11 -> legacy Clipboard - NOT SUPPORTED ANYMORE
                // clipboardManager.setText(text);

                // alert the user that the text is in the clipboard and we're done
                Toast.makeText(this, R.string.clipboard_text_copied, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.clipboard_no_text_to_copy, Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, R.string.clipboard_uxexpected_error, Toast.LENGTH_SHORT).show();
            Timber.e(e, "Exception caught while copying to clipboard");
        }

        finish();
    }

}
