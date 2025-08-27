/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * @author Juan Carlos Garrote Gascón
 *
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

package eu.opencloud.android.data

import eu.opencloud.android.domain.exceptions.AccountException
import eu.opencloud.android.domain.exceptions.AccountNotFoundException
import eu.opencloud.android.domain.exceptions.AccountNotNewException
import eu.opencloud.android.domain.exceptions.AccountNotTheSameException
import eu.opencloud.android.domain.exceptions.BadOcVersionException
import eu.opencloud.android.domain.exceptions.CancelledException
import eu.opencloud.android.domain.exceptions.ConflictException
import eu.opencloud.android.domain.exceptions.CopyIntoDescendantException
import eu.opencloud.android.domain.exceptions.DelayedForWifiException
import eu.opencloud.android.domain.exceptions.FileNotFoundException
import eu.opencloud.android.domain.exceptions.ForbiddenException
import eu.opencloud.android.domain.exceptions.IncorrectAddressException
import eu.opencloud.android.domain.exceptions.InstanceNotConfiguredException
import eu.opencloud.android.domain.exceptions.InvalidCharacterException
import eu.opencloud.android.domain.exceptions.InvalidCharacterInNameException
import eu.opencloud.android.domain.exceptions.InvalidLocalFileNameException
import eu.opencloud.android.domain.exceptions.InvalidOverwriteException
import eu.opencloud.android.domain.exceptions.LocalFileNotFoundException
import eu.opencloud.android.domain.exceptions.LocalStorageFullException
import eu.opencloud.android.domain.exceptions.LocalStorageNotCopiedException
import eu.opencloud.android.domain.exceptions.LocalStorageNotMovedException
import eu.opencloud.android.domain.exceptions.LocalStorageNotRemovedException
import eu.opencloud.android.domain.exceptions.MoveIntoDescendantException
import eu.opencloud.android.domain.exceptions.NetworkErrorException
import eu.opencloud.android.domain.exceptions.NoConnectionWithServerException
import eu.opencloud.android.domain.exceptions.NoNetworkConnectionException
import eu.opencloud.android.domain.exceptions.OAuth2ErrorAccessDeniedException
import eu.opencloud.android.domain.exceptions.OAuth2ErrorException
import eu.opencloud.android.domain.exceptions.PartialCopyDoneException
import eu.opencloud.android.domain.exceptions.PartialMoveDoneException
import eu.opencloud.android.domain.exceptions.QuotaExceededException
import eu.opencloud.android.domain.exceptions.RedirectToNonSecureException
import eu.opencloud.android.domain.exceptions.ResourceLockedException
import eu.opencloud.android.domain.exceptions.SSLErrorException
import eu.opencloud.android.domain.exceptions.ServerConnectionTimeoutException
import eu.opencloud.android.domain.exceptions.ServerNotReachableException
import eu.opencloud.android.domain.exceptions.ServerResponseTimeoutException
import eu.opencloud.android.domain.exceptions.ServiceUnavailableException
import eu.opencloud.android.domain.exceptions.ShareForbiddenException
import eu.opencloud.android.domain.exceptions.ShareNotFoundException
import eu.opencloud.android.domain.exceptions.ShareWrongParameterException
import eu.opencloud.android.domain.exceptions.SpecificForbiddenException
import eu.opencloud.android.domain.exceptions.SpecificMethodNotAllowedException
import eu.opencloud.android.domain.exceptions.SpecificServiceUnavailableException
import eu.opencloud.android.domain.exceptions.SpecificUnsupportedMediaTypeException
import eu.opencloud.android.domain.exceptions.SyncConflictException
import eu.opencloud.android.domain.exceptions.TooEarlyException
import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.exceptions.UnhandledHttpCodeException
import eu.opencloud.android.domain.exceptions.UnknownErrorException
import eu.opencloud.android.domain.exceptions.WrongServerResponseException
import eu.opencloud.android.lib.common.network.CertificateCombinedException
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import java.net.SocketTimeoutException

fun <T> executeRemoteOperation(operation: () -> RemoteOperationResult<T>): T {
    operation.invoke().also {
        return handleRemoteOperationResult(it)
    }
}

private fun <T> handleRemoteOperationResult(
    remoteOperationResult: RemoteOperationResult<T>
): T {
    if (remoteOperationResult.isSuccess) {
        return remoteOperationResult.data
    }

    when (remoteOperationResult.code) {
        RemoteOperationResult.ResultCode.WRONG_CONNECTION -> throw NoConnectionWithServerException()
        RemoteOperationResult.ResultCode.NO_NETWORK_CONNECTION -> throw NoNetworkConnectionException()
        RemoteOperationResult.ResultCode.TIMEOUT ->
            if (remoteOperationResult.exception is SocketTimeoutException) throw ServerResponseTimeoutException()
            else throw ServerConnectionTimeoutException()
        RemoteOperationResult.ResultCode.HOST_NOT_AVAILABLE -> throw ServerNotReachableException()
        RemoteOperationResult.ResultCode.SERVICE_UNAVAILABLE -> throw ServiceUnavailableException()
        RemoteOperationResult.ResultCode.SSL_RECOVERABLE_PEER_UNVERIFIED -> throw remoteOperationResult.exception as CertificateCombinedException
        RemoteOperationResult.ResultCode.BAD_OC_VERSION -> throw BadOcVersionException()
        RemoteOperationResult.ResultCode.INCORRECT_ADDRESS -> throw IncorrectAddressException()
        RemoteOperationResult.ResultCode.SSL_ERROR -> throw SSLErrorException()
        RemoteOperationResult.ResultCode.UNAUTHORIZED -> throw UnauthorizedException()
        RemoteOperationResult.ResultCode.INSTANCE_NOT_CONFIGURED -> throw InstanceNotConfiguredException()
        RemoteOperationResult.ResultCode.FILE_NOT_FOUND -> throw FileNotFoundException()
        RemoteOperationResult.ResultCode.OAUTH2_ERROR -> throw OAuth2ErrorException()
        RemoteOperationResult.ResultCode.OAUTH2_ERROR_ACCESS_DENIED -> throw OAuth2ErrorAccessDeniedException()
        RemoteOperationResult.ResultCode.ACCOUNT_NOT_NEW -> throw AccountNotNewException()
        RemoteOperationResult.ResultCode.ACCOUNT_NOT_THE_SAME -> throw AccountNotTheSameException()
        RemoteOperationResult.ResultCode.OK_REDIRECT_TO_NON_SECURE_CONNECTION -> throw RedirectToNonSecureException()
        RemoteOperationResult.ResultCode.UNHANDLED_HTTP_CODE -> throw UnhandledHttpCodeException()
        RemoteOperationResult.ResultCode.UNKNOWN_ERROR -> throw UnknownErrorException()
        RemoteOperationResult.ResultCode.CANCELLED -> throw CancelledException()
        RemoteOperationResult.ResultCode.INVALID_LOCAL_FILE_NAME -> throw InvalidLocalFileNameException()
        RemoteOperationResult.ResultCode.INVALID_OVERWRITE -> throw InvalidOverwriteException()
        RemoteOperationResult.ResultCode.CONFLICT -> throw ConflictException()
        RemoteOperationResult.ResultCode.SYNC_CONFLICT -> throw SyncConflictException()
        RemoteOperationResult.ResultCode.LOCAL_STORAGE_FULL -> throw LocalStorageFullException()
        RemoteOperationResult.ResultCode.LOCAL_STORAGE_NOT_MOVED -> throw LocalStorageNotMovedException()
        RemoteOperationResult.ResultCode.LOCAL_STORAGE_NOT_COPIED -> throw LocalStorageNotCopiedException()
        RemoteOperationResult.ResultCode.QUOTA_EXCEEDED -> throw QuotaExceededException()
        RemoteOperationResult.ResultCode.ACCOUNT_NOT_FOUND -> throw AccountNotFoundException()
        RemoteOperationResult.ResultCode.ACCOUNT_EXCEPTION -> throw AccountException()
        RemoteOperationResult.ResultCode.INVALID_CHARACTER_IN_NAME -> throw InvalidCharacterInNameException()
        RemoteOperationResult.ResultCode.LOCAL_STORAGE_NOT_REMOVED -> throw LocalStorageNotRemovedException()
        RemoteOperationResult.ResultCode.FORBIDDEN -> throw ForbiddenException()
        RemoteOperationResult.ResultCode.SPECIFIC_FORBIDDEN -> throw SpecificForbiddenException()
        RemoteOperationResult.ResultCode.INVALID_MOVE_INTO_DESCENDANT -> throw MoveIntoDescendantException()
        RemoteOperationResult.ResultCode.INVALID_COPY_INTO_DESCENDANT -> throw CopyIntoDescendantException()
        RemoteOperationResult.ResultCode.PARTIAL_MOVE_DONE -> throw PartialMoveDoneException()
        RemoteOperationResult.ResultCode.PARTIAL_COPY_DONE -> throw PartialCopyDoneException()
        RemoteOperationResult.ResultCode.SHARE_WRONG_PARAMETER -> throw ShareWrongParameterException()
        RemoteOperationResult.ResultCode.WRONG_SERVER_RESPONSE -> throw WrongServerResponseException()
        RemoteOperationResult.ResultCode.INVALID_CHARACTER_DETECT_IN_SERVER -> throw InvalidCharacterException()
        RemoteOperationResult.ResultCode.DELAYED_FOR_WIFI -> throw DelayedForWifiException()
        RemoteOperationResult.ResultCode.LOCAL_FILE_NOT_FOUND -> throw LocalFileNotFoundException()
        RemoteOperationResult.ResultCode.SPECIFIC_SERVICE_UNAVAILABLE -> throw SpecificServiceUnavailableException(remoteOperationResult.httpPhrase)
        RemoteOperationResult.ResultCode.SPECIFIC_UNSUPPORTED_MEDIA_TYPE -> throw SpecificUnsupportedMediaTypeException()
        RemoteOperationResult.ResultCode.SPECIFIC_METHOD_NOT_ALLOWED -> throw SpecificMethodNotAllowedException(remoteOperationResult.httpPhrase)
        RemoteOperationResult.ResultCode.SHARE_NOT_FOUND -> throw ShareNotFoundException(remoteOperationResult.httpPhrase)
        RemoteOperationResult.ResultCode.SHARE_FORBIDDEN -> throw ShareForbiddenException(remoteOperationResult.httpPhrase)
        RemoteOperationResult.ResultCode.TOO_EARLY -> throw TooEarlyException()
        RemoteOperationResult.ResultCode.NETWORK_ERROR -> throw NetworkErrorException()
        RemoteOperationResult.ResultCode.RESOURCE_LOCKED -> throw ResourceLockedException()
        else -> throw Exception("An unknown error has occurred")
    }
}
