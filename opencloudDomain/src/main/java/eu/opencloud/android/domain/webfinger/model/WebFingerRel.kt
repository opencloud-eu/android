/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
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
package eu.opencloud.android.domain.webfinger.model

enum class WebFingerRel(val uri: String) {
    OPENCLOUD_INSTANCE("http://webfinger.opencloud/rel/server-instance"),

    // https://openid.net/specs/openid-connect-discovery-1_0.html#IssuerDiscovery
    OIDC_ISSUER_DISCOVERY("http://openid.net/specs/connect/1.0/issuer")
}
