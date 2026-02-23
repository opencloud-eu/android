/**
 * openCloud Android client application
 *
 * @author Bartek Przybylski
 * @author David A. Velasco
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2012 Bartek Przybylski
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

package eu.opencloud.android.presentation.conflicts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eu.opencloud.android.databinding.ActivityConflictsResolveBinding
import eu.opencloud.android.ui.activity.enableEdgeToEdgePostSetContentView
import eu.opencloud.android.ui.activity.enableEdgeToEdgePreSetContentView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class ConflictsResolveActivity : AppCompatActivity(), ConflictsResolveDialogFragment.OnConflictDecisionMadeListener {

    private var _binding: ActivityConflictsResolveBinding? = null
    val binding get() = _binding!!

    private val conflictsResolveViewModel by viewModel<ConflictsResolveViewModel> {
        parametersOf(
            intent.getParcelableExtra(
                EXTRA_FILE
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // edge-to-edge
        enableEdgeToEdgePreSetContentView(true)

        _binding = ActivityConflictsResolveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // edge-to-edge
        enableEdgeToEdgePostSetContentView { insets ->
            binding.toolbar.updatePadding(top = insets.top)
            binding.root.updatePadding(bottom = insets.bottom)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                conflictsResolveViewModel.currentFile.collectLatest { updatedOCFile ->
                    Timber.d("File ${updatedOCFile?.remotePath} from ${updatedOCFile?.owner} needs to fix a conflict with etag" +
                            " in conflict ${updatedOCFile?.etagInConflict}")
                    // Finish if the file does not exists or if the file is not in conflict anymore.
                    updatedOCFile?.etagInConflict ?: finish()
                }
            }
        }

        ConflictsResolveDialogFragment.newInstance(onConflictDecisionMadeListener = this).showDialog(this)
    }

    override fun conflictDecisionMade(decision: ConflictsResolveDialogFragment.Decision) {
        when (decision) {
            ConflictsResolveDialogFragment.Decision.CANCEL -> {}
            ConflictsResolveDialogFragment.Decision.KEEP_LOCAL -> {
                conflictsResolveViewModel.uploadFileInConflict()
            }
            ConflictsResolveDialogFragment.Decision.KEEP_BOTH -> {
                conflictsResolveViewModel.uploadFileFromSystem()
            }
            ConflictsResolveDialogFragment.Decision.KEEP_SERVER -> {
                conflictsResolveViewModel.downloadFile()
            }
        }

        Timber.d("Decision to fix conflict on file ${conflictsResolveViewModel.currentFile.value?.remotePath} is ${decision.name}")

        finish()
    }

    companion object {
        const val EXTRA_FILE = "EXTRA_FILE"
    }
}
