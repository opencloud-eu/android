/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 * @author Jorge Aguado Recio
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.presentation.spaces

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.GridLayoutManager
import eu.opencloud.android.R
import eu.opencloud.android.databinding.SpacesListFragmentBinding
import eu.opencloud.android.domain.files.model.FileListOption
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.extensions.collectLatestLifecycleFlow
import eu.opencloud.android.extensions.showErrorInSnackbar
import eu.opencloud.android.extensions.toDrawableRes
import eu.opencloud.android.extensions.toSubtitleStringRes
import eu.opencloud.android.extensions.toTitleStringRes
import eu.opencloud.android.presentation.capabilities.CapabilityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class SpacesListFragment : SpacesListAdapter.SpacesListAdapterListener, Fragment(), SearchView.OnQueryTextListener {
    private var _binding: SpacesListFragmentBinding? = null
    private val binding get() = _binding!!

    private var isMultiPersonal = false

    private val spacesListViewModel: SpacesListViewModel by viewModel {
        parametersOf(
            requireArguments().getString(BUNDLE_ACCOUNT_NAME),
            requireArguments().getBoolean(BUNDLE_SHOW_PERSONAL_SPACE),
        )
    }
    private val capabilityViewModel: CapabilityViewModel by viewModel {
        parametersOf(
            requireArguments().getString(BUNDLE_ACCOUNT_NAME),
        )
    }

    private lateinit var spacesListAdapter: SpacesListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SpacesListFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isMultiPersonal = capabilityViewModel.checkMultiPersonal()
        initViews()
        subscribeToViewModels()
    }

    private fun initViews() {
        setHasOptionsMenu(true)

        val spacesListLayoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerSpacesList.layoutManager = spacesListLayoutManager
        spacesListAdapter = SpacesListAdapter(this)
        binding.recyclerSpacesList.adapter = spacesListAdapter

        binding.swipeRefreshSpacesList.setOnRefreshListener {
            spacesListViewModel.refreshSpacesFromServer()
        }
        setTextHintRootToolbar()
    }

    private fun subscribeToViewModels() {
        collectLatestLifecycleFlow(spacesListViewModel.spacesList) { uiState ->
            if (uiState.searchFilter != "") {
                var spacesToListFiltered =
                    uiState.spaces.filter { it.name.lowercase().contains(uiState.searchFilter.lowercase()) && !it.isPersonal && !it.isDisabled }
                val personalSpace = uiState.spaces.find { it.isPersonal }
                personalSpace?.let {
                    spacesToListFiltered = spacesToListFiltered.toMutableList().apply {
                        add(0, personalSpace)
                    }
                }
                showOrHideEmptyView(spacesToListFiltered)
                spacesListAdapter.setData(spacesToListFiltered, isMultiPersonal)
            } else {
                showOrHideEmptyView(uiState.spaces)
                spacesListAdapter.setData(uiState.spaces.filter { !it.isDisabled }, isMultiPersonal)
            }
            binding.swipeRefreshSpacesList.isRefreshing = uiState.refreshing
            uiState.error?.let { showErrorInSnackbar(R.string.spaces_sync_failed, it) }

            uiState.rootFolderFromSelectedSpace?.let {
                setFragmentResult(REQUEST_KEY_CLICK_SPACE, bundleOf(BUNDLE_KEY_CLICK_SPACE to it))
            }
        }
    }

    private fun showOrHideEmptyView(spacesList: List<OCSpace>) {
        binding.recyclerSpacesList.isVisible = spacesList.isNotEmpty()

        with(binding.emptyDataParent) {
            root.isVisible = spacesList.isEmpty()
            listEmptyDatasetIcon.setImageResource(FileListOption.SPACES_LIST.toDrawableRes())
            listEmptyDatasetTitle.setText(FileListOption.SPACES_LIST.toTitleStringRes())
            listEmptyDatasetSubTitle.setText(FileListOption.SPACES_LIST.toSubtitleStringRes())
        }
    }

    override fun onItemClick(ocSpace: OCSpace) {
        spacesListViewModel.getRootFileForSpace(ocSpace)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        (menu.findItem(R.id.action_search).actionView as SearchView).run {
            setOnQueryTextListener(this@SpacesListFragment)
            queryHint = resources.getString(R.string.actionbar_search_space)
        }
        menu.findItem(R.id.action_share_current_folder)?.itemId?.let { menu.removeItem(it) }
    }

    override fun onQueryTextSubmit(query: String?): Boolean = false

    override fun onQueryTextChange(newText: String?): Boolean {
        newText?.let { spacesListViewModel.updateSearchFilter(it) }
        return true
    }

    fun setSearchListener(searchView: SearchView) {
        searchView.setOnQueryTextListener(this)
    }

    private fun setTextHintRootToolbar() {
        val searchViewRootToolbar = requireActivity().findViewById<SearchView>(R.id.root_toolbar_search_view)
        searchViewRootToolbar.queryHint = getString(R.string.actionbar_search_space)
    }

    companion object {
        const val REQUEST_KEY_CLICK_SPACE = "REQUEST_KEY_CLICK_SPACE"
        const val BUNDLE_KEY_CLICK_SPACE = "BUNDLE_KEY_CLICK_SPACE"
        const val BUNDLE_SHOW_PERSONAL_SPACE = "showPersonalSpace"
        const val BUNDLE_ACCOUNT_NAME = "accountName"
        fun newInstance(
            showPersonalSpace: Boolean,
            accountName: String
        ): SpacesListFragment {
            val args = Bundle().apply {
                putBoolean(BUNDLE_SHOW_PERSONAL_SPACE, showPersonalSpace)
                putString(BUNDLE_ACCOUNT_NAME, accountName)
            }
            return SpacesListFragment().apply { arguments = args }
        }
    }

}
