/**
 * openCloud Android client application
 *
 * @author masensio
 * @author David A. Velasco
 * @author Christian Schabesberger
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
package eu.opencloud.android.ui.adapter;

import android.view.View;
import android.widget.LinearLayout;

import eu.opencloud.android.R;
import eu.opencloud.android.lib.common.network.CertificateCombinedException;
import eu.opencloud.android.ui.dialog.SslUntrustedCertDialog;
import eu.opencloud.android.utils.PreferenceUtils;

/**
 * TODO
 *
 */
public class CertificateCombinedExceptionViewAdapter implements SslUntrustedCertDialog.ErrorViewAdapter {

    private CertificateCombinedException mSslException;

    public CertificateCombinedExceptionViewAdapter(CertificateCombinedException sslException) {
        mSslException = sslException;
    }

    @Override
    public void updateErrorView(View dialogView) {
        // Allow or disallow touches with other visible windows
        LinearLayout manageSpace = dialogView.findViewById(R.id.root);
        manageSpace.setFilterTouchesWhenObscured(
                PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(dialogView.getContext())
        );

        /// clean
        dialogView.findViewById(R.id.reason_no_info_about_error).setVisibility(View.GONE);

        /// refresh
        if (mSslException.getCertPathValidatorException() != null) {
            dialogView.findViewById(R.id.reason_cert_not_trusted).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_cert_not_trusted).setVisibility(View.GONE);
        }

        if (mSslException.getCertificateExpiredException() != null) {
            dialogView.findViewById(R.id.reason_cert_expired).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_cert_expired).setVisibility(View.GONE);
        }

        if (mSslException.getCertificateNotYetValidException() != null) {
            dialogView.findViewById(R.id.reason_cert_not_yet_valid).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_cert_not_yet_valid).setVisibility(View.GONE);
        }

        if (mSslException.getSslPeerUnverifiedException() != null) {
            dialogView.findViewById(R.id.reason_hostname_not_verified).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_hostname_not_verified).setVisibility(View.GONE);
        }

    }
}
