/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.testutil

import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.domain.transfers.model.UploadEnqueuedBy
import java.io.File

val OC_TRANSFER = OCTransfer(
    id = 0L,
    localPath = "${File.separator}local${File.separator}path",
    remotePath = "${File.separator}remote${File.separator}path",
    accountName = OC_ACCOUNT_NAME,
    fileSize = 1024L,
    status = TransferStatus.TRANSFER_IN_PROGRESS,
    localBehaviour = UploadBehavior.MOVE,
    forceOverwrite = true,
    createdBy = UploadEnqueuedBy.ENQUEUED_BY_USER,
    sourcePath = "${File.separator}source${File.separator}path",
)

val OC_FINISHED_TRANSFER = OC_TRANSFER.copy(
    status = TransferStatus.TRANSFER_SUCCEEDED
)

val OC_FAILED_TRANSFER = OC_TRANSFER.copy(
    status = TransferStatus.TRANSFER_FAILED
)

val OC_PENDING_TRANSFER = OC_TRANSFER.copy(
    status = TransferStatus.TRANSFER_QUEUED
)
