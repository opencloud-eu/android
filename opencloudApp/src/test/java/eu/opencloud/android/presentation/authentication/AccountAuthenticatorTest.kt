/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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

package eu.opencloud.android.presentation.authentication

import eu.opencloud.android.domain.exceptions.NoConnectionWithServerException
import eu.opencloud.android.domain.exceptions.OAuth2ErrorAccessDeniedException
import eu.opencloud.android.domain.exceptions.OAuth2ErrorException
import eu.opencloud.android.domain.exceptions.ServerConnectionTimeoutException
import eu.opencloud.android.domain.exceptions.ServerNotReachableException
import eu.opencloud.android.domain.exceptions.ServiceUnavailableException
import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.exceptions.UnhandledHttpCodeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a failed token refresh is "the session is over" or "try again later" decides if the user is sent to the
 * login screen. Only a refusal by the IdP may do that.
 */
class AccountAuthenticatorTest {

    @Test
    fun `refresh token rejected by the IdP is a permanent failure`() {
        assertTrue(AccountAuthenticator.isRefreshTokenRejected(OAuth2ErrorException()))
        assertTrue(AccountAuthenticator.isRefreshTokenRejected(OAuth2ErrorAccessDeniedException()))
        assertTrue(AccountAuthenticator.isRefreshTokenRejected(UnauthorizedException()))
    }

    @Test
    fun `connection problems and server errors are transient failures`() {
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(NoConnectionWithServerException()))
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(ServerConnectionTimeoutException()))
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(ServerNotReachableException()))
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(ServiceUnavailableException()))
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(UnhandledHttpCodeException(500)))
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(UnhandledHttpCodeException(400)))
    }

    @Test
    fun `unknown failure or no failure at all is not a rejection`() {
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(RuntimeException("boom")))
        assertFalse(AccountAuthenticator.isRefreshTokenRejected(null))
    }
}
