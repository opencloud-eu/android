/**
 * openCloud Android client application
 *
 * @author Bartek Przybylski
 * @author Christian Schabesberger
 * Copyright (C) 2012 Bartek Przybylski
 * Copyright (C) 2020 ownCloud GmbH.
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

import eu.opencloud.android.datamodel.FileDataStorageManager;
import eu.opencloud.android.services.OperationsService.OperationsServiceBinder;
import eu.opencloud.android.ui.helpers.FileOperationsHelper;

public interface ComponentsGetter {
    /**
     * To be invoked when the parent activity is fully created to get a reference
     * to the OperationsService service API.
     */
    OperationsServiceBinder getOperationsServiceBinder();

    FileDataStorageManager getStorageManager();

    FileOperationsHelper getFileOperationsHelper();

}
