/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
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
package eu.opencloud.android.utils

import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.presentation.files.SortType

class SortFilesUtils {
    fun sortFiles(
        listOfFiles: List<OCFile>,
        sortTypeValue: Int,
        ascending: Boolean,
    ): List<OCFile> =
        when (SortType.fromPreference(sortTypeValue)) {
            SortType.SORT_TYPE_BY_NAME -> sortByName(listOfFiles, ascending)
            SortType.SORT_TYPE_BY_SIZE -> sortBySize(listOfFiles, ascending)
            SortType.SORT_TYPE_BY_DATE -> sortByDate(listOfFiles, ascending)
        }

    private fun sortByName(listOfFiles: List<OCFile>, ascending: Boolean): List<OCFile> {
        val newListOfFiles =
            if (ascending) listOfFiles.sortedBy { it.fileName.lowercase() }
            else listOfFiles.sortedByDescending { it.fileName.lowercase() }

        // Show first the folders when sorting by name
        return newListOfFiles.sortedByDescending { it.isFolder }
    }

    private fun sortBySize(listOfFiles: List<OCFile>, ascending: Boolean): List<OCFile> =
        if (ascending) listOfFiles.sortedBy { it.length }
        else listOfFiles.sortedByDescending { it.length }

    private fun sortByDate(listOfFiles: List<OCFile>, ascending: Boolean): List<OCFile> =
        if (ascending) listOfFiles.sortedBy { it.modificationTimestamp }
        else listOfFiles.sortedByDescending { it.modificationTimestamp }
}
