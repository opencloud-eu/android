/**
 * openCloud Android client application
 *
 * @author David Crespo Ríos
 * Copyright (C) 2022 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.presentation.releasenotes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import eu.opencloud.android.BuildConfig
import eu.opencloud.android.MainApp
import eu.opencloud.android.MainApp.Companion.versionCode
import eu.opencloud.android.R
import eu.opencloud.android.databinding.ReleaseNotesActivityBinding
import eu.opencloud.android.presentation.authentication.LoginActivity
import eu.opencloud.android.ui.activity.FileDisplayActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class ReleaseNotesActivity : AppCompatActivity() {

    // ViewModel
    private val releaseNotesViewModel by viewModel<ReleaseNotesViewModel>()

    private var _binding: ReleaseNotesActivityBinding? = null
    val binding get() = _binding!!

    private val releaseNotesAdapter = ReleaseNotesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ReleaseNotesActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setData()
        initView()
    }

    private fun initView() {
        binding.releaseNotes.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = releaseNotesAdapter
        }

        binding.btnProceed.setOnClickListener {
            releaseNotesViewModel.updateVersionCode()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun setData() {
        releaseNotesAdapter.setData(releaseNotesViewModel.getReleaseNotes())

        val header = String.format(
            getString(R.string.release_notes_header),
            getString(R.string.app_name)
        )

        val footer = String.format(
            getString(R.string.release_notes_footer),
            getString(R.string.app_name)
        )

        binding.txtHeader.text = header
        binding.txtFooter.text = footer
    }

    companion object {
        fun runIfNeeded(context: Context) {
            if (context is ReleaseNotesActivity) {
                return
            }
            if (shouldShow(context)) {
                context.startActivity(Intent(context, ReleaseNotesActivity::class.java))
            }
        }

        private fun shouldShow(context: Context): Boolean {
            val showReleaseNotes = context.resources.getBoolean(R.bool.release_notes_enabled) && !BuildConfig.DEBUG

            return firstRunAfterUpdate() && showReleaseNotes &&
                    ReleaseNotesViewModel.releaseNotesList.isNotEmpty() &&
                    (context is FileDisplayActivity || context is LoginActivity)
        }

        private fun firstRunAfterUpdate(): Boolean =
            MainApp.getLastSeenVersionCode() != versionCode
    }
}
