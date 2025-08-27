/**
 * openCloud Android client application
 *
 * @author David A. Velasco
 * @author Christian Schabesberger
 * Copyright (C) 2020 ownCloud GmbH.
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

package eu.opencloud.android.operations.common;

import android.content.Context;
import android.os.Handler;

import eu.opencloud.android.datamodel.FileDataStorageManager;
import eu.opencloud.android.lib.common.OpenCloudClient;
import eu.opencloud.android.lib.common.operations.OnRemoteOperationListener;
import eu.opencloud.android.lib.common.operations.RemoteOperation;
import eu.opencloud.android.lib.common.operations.RemoteOperationResult;

/**
 * Operation which execution involves both interactions with an openCloud server and
 * with local data in the device.
 *
 * Provides methods to execute the operation both synchronously or asynchronously.
 */
public abstract class SyncOperation<T> extends RemoteOperation<T> {

    private FileDataStorageManager mStorageManager;

    public FileDataStorageManager getStorageManager() {
        return mStorageManager;
    }

    /**
     * Synchronously executes the operation on the received openCloud account.
     *
     * Do not call this method from the main thread.
     *
     * This method should be used whenever an openCloud account is available, instead of
     * {@link #execute(OpenCloudClient, eu.opencloud.android.datamodel.FileDataStorageManager)}.
     *
     * @param storageManager
     * @param context           Android context for the component calling the method.
     * @return Result of the operation.
     */
    public RemoteOperationResult<T> execute(FileDataStorageManager storageManager, Context context) {
        if (storageManager == null) {
            throw new IllegalArgumentException("Trying to execute a sync operation with a " +
                    "NULL storage manager");
        }
        if (storageManager.getAccount() == null) {
            throw new IllegalArgumentException("Trying to execute a sync operation with a " +
                    "storage manager for a NULL account");
        }
        mStorageManager = storageManager;
        return super.execute(mStorageManager.getAccount(), context);
    }

    /**
     * Synchronously executes the remote operation
     *
     * Do not call this method from the main thread.
     *
     * @param client            Client object to reach an openCloud server during the execution of the operation.
     * @param storageManager    Instance of local repository to sync with remote.
     * @return Result of the operation.
     */
    public RemoteOperationResult<T> execute(OpenCloudClient client,
                                            FileDataStorageManager storageManager) {
        if (storageManager == null) {
            throw new IllegalArgumentException("Trying to execute a sync operation with a " +
                    "NULL storage manager");
        }
        mStorageManager = storageManager;
        return super.execute(client);
    }

    /**
     * Asynchronously executes the remote operation
     *
     * This method should be used whenever an openCloud account is available,
     * instead of {@link #execute(OpenCloudClient, OnRemoteOperationListener, Handler))}.
     *
     * @param storageManager    Instance of local repository to sync with remote.
     * @param context           Android context for the component calling the method.
     * @param listener          Listener to be notified about the execution of the operation.
     * @param listenerHandler   Handler associated to the thread where the methods of the listener
     *                          objects must be called.
     * @return Thread were the remote operation is executed.
     */
    public Thread execute(FileDataStorageManager storageManager, Context context,
                          OnRemoteOperationListener listener, Handler listenerHandler) {
        if (storageManager == null) {
            throw new IllegalArgumentException("Trying to execute a sync operation " +
                    "with a NULL storage manager");
        }
        if (storageManager.getAccount() == null) {
            throw new IllegalArgumentException("Trying to execute a sync operation with a" +
                    " storage manager for a NULL account");
        }
        mStorageManager = storageManager;
        return super.execute(mStorageManager.getAccount(), context, listener, listenerHandler);
    }

    /**
     * Asynchronously executes the remote operation
     *
     * @param client            Client object to reach an openCloud server during the
     *                          execution of the operation.
     * @param storageManager    Instance of local repository to sync with remote.
     * @param listener          Listener to be notified about the execution of the operation.
     * @param listenerHandler   Handler associated to the thread where the methods of
     *                          the listener objects must be called.
     * @return Thread were the remote operation is executed.
     */
    public Thread execute(OpenCloudClient client, FileDataStorageManager storageManager,
                          OnRemoteOperationListener listener, Handler listenerHandler) {
        if (storageManager == null) {
            throw new IllegalArgumentException("Trying to execute a sync operation " +
                    "with a NULL storage manager");
        }
        mStorageManager = storageManager;
        return super.execute(client, listener, listenerHandler);
    }
}