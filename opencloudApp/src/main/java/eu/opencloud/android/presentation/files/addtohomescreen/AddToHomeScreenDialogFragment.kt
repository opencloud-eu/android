package eu.opencloud.android.presentation.files.addtohomescreen

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import eu.opencloud.android.R
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.presentation.files.filelist.MainFileListFragment.Companion.MAX_FILENAME_LENGTH
import eu.opencloud.android.presentation.files.filelist.MainFileListFragment.Companion.forbiddenChars
import eu.opencloud.android.utils.PreferenceUtils

class AddToHomeScreenDialogFragment : DialogFragment() {

    private lateinit var folder: OCFile
    private lateinit var listener: AddToHomeScreenListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folder = requireArguments().getParcelable(ARG_FOLDER)!!
        listener = parentFragment as AddToHomeScreenListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.edit_box_dialog, null)

        view.filterTouchesWhenObscured =
            PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)

        val coordinatorLayout: CoordinatorLayout = requireActivity().findViewById(R.id.coordinator_layout)
        coordinatorLayout.filterTouchesWhenObscured =
            PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)

        val inputText: EditText = view.findViewById(R.id.user_input)
        val inputLayout: TextInputLayout = view.findViewById(R.id.edit_box_input_text_layout)

        inputText.setText(folder.fileName)
        inputText.selectAll()
        inputText.requestFocus()

        val builder = AlertDialog.Builder(requireActivity())
        builder.setView(view)
            .setPositiveButton(R.string.add_to_home_screen_dialog_add_button) { dialog, _ ->
                val name = inputText.text.toString().trim()
                if (name.isNotBlank()) {
                    listener.onAddToHomeScreen(name, folder)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setTitle(R.string.add_to_home_screen_dialog_title)

        val alertDialog = builder.create()

        alertDialog.setOnShowListener {
            val okButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.isEnabled = inputText.text.isNullOrBlank().not()

            okButton.setOnClickListener {
                val name = inputText.text.toString().trim()
                val error = when {
                    name.isBlank() -> getString(R.string.add_to_home_screen_dialog_error_empty)
                    name.length > MAX_FILENAME_LENGTH -> String.format(
                        getString(R.string.uploader_upload_text_dialog_filename_error_length_max),
                        MAX_FILENAME_LENGTH
                    )
                    forbiddenChars.any { name.contains(it) } -> getString(R.string.filename_forbidden_characters)
                    else -> null
                }

                if (error == null) {
                    listener.onAddToHomeScreen(name, folder)
                    alertDialog.dismiss()
                } else {
                    inputLayout.error = error
                }
            }
        }

        inputText.doOnTextChanged { text, _, _, _ ->
            val okButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            var error: String? = null

            if (text.isNullOrBlank()) {
                okButton.isEnabled = false
                error = getString(R.string.add_to_home_screen_dialog_error_empty)
            } else if (text.length > MAX_FILENAME_LENGTH) {
                error = String.format(
                    getString(R.string.uploader_upload_text_dialog_filename_error_length_max),
                    MAX_FILENAME_LENGTH
                )
            } else if (forbiddenChars.any { text.contains(it) }) {
                error = getString(R.string.filename_forbidden_characters)
            } else {
                okButton.isEnabled = true
            }

            if (error != null) {
                okButton.isEnabled = false
                inputLayout.error = error
            } else {
                inputLayout.error = null
            }
        }

        alertDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return alertDialog
    }

    interface AddToHomeScreenListener {
        fun onAddToHomeScreen(shortcutName: String, folder: OCFile)
    }

    companion object {
        const val TAG = "ADD_TO_HOME_SCREEN_DIALOG"
        private const val ARG_FOLDER = "ARG_FOLDER"

        @JvmStatic
        fun newInstance(folder: OCFile): AddToHomeScreenDialogFragment =
            AddToHomeScreenDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FOLDER, folder)
                }
            }
    }
}
