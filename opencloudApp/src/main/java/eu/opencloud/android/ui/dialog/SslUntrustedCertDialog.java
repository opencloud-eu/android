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
package eu.opencloud.android.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.SslErrorHandler;
import android.widget.Button;

import androidx.fragment.app.DialogFragment;
import eu.opencloud.android.R;
import eu.opencloud.android.extensions.DialogExtKt;
import eu.opencloud.android.lib.common.network.CertificateCombinedException;
import eu.opencloud.android.lib.common.network.NetworkUtils;
import eu.opencloud.android.ui.adapter.CertificateCombinedExceptionViewAdapter;
import eu.opencloud.android.ui.adapter.SslCertificateViewAdapter;
import eu.opencloud.android.ui.adapter.SslErrorViewAdapter;
import eu.opencloud.android.ui.adapter.X509CertificateViewAdapter;
import timber.log.Timber;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

/**
 * Dialog to show information about an untrusted certificate and allow the user
 * to decide trust on it or not.
 * <p>
 * Abstract implementation of common functionality for different dialogs that
 * get the information about the error and the certificate from different classes.
 */
public class SslUntrustedCertDialog extends DialogFragment {

    protected View mView = null;
    protected SslErrorHandler mHandler = null;
    protected X509Certificate m509Certificate = null;

    private ErrorViewAdapter mErrorViewAdapter = null;
    private CertificateViewAdapter mCertificateViewAdapter = null;

    public static SslUntrustedCertDialog newInstanceForEmptySslError(SslError error, SslErrorHandler handler) {
        if (error == null) {
            throw new IllegalArgumentException("Trying to create instance with parameter error == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Trying to create instance with parameter handler == null");
        }
        SslUntrustedCertDialog dialog = new SslUntrustedCertDialog();
        dialog.mHandler = handler;
        dialog.mErrorViewAdapter = new SslErrorViewAdapter(error);
        dialog.mCertificateViewAdapter = new SslCertificateViewAdapter(error.getCertificate());
        return dialog;
    }

    public static SslUntrustedCertDialog newInstanceForFullSslError(CertificateCombinedException sslException) {
        if (sslException == null) {
            throw new IllegalArgumentException("Trying to create instance with parameter sslException == null");
        }
        SslUntrustedCertDialog dialog = new SslUntrustedCertDialog();
        dialog.m509Certificate = sslException.getServerCertificate();
        dialog.mErrorViewAdapter = new CertificateCombinedExceptionViewAdapter(sslException);
        dialog.mCertificateViewAdapter = new X509CertificateViewAdapter(sslException.getServerCertificate());
        return dialog;
    }

    public static SslUntrustedCertDialog newInstanceForFullSslError(X509Certificate cert, SslError error,
                                                                    SslErrorHandler handler) {
        if (cert == null) {
            throw new IllegalArgumentException("Trying to create instance with parameter cert == null");
        }
        if (error == null) {
            throw new IllegalArgumentException("Trying to create instance with parameter error == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Trying to create instance with parameter handler == null");
        }
        SslUntrustedCertDialog dialog = new SslUntrustedCertDialog();
        dialog.m509Certificate = cert;
        dialog.mHandler = handler;
        dialog.mErrorViewAdapter = new SslErrorViewAdapter(error);
        dialog.mCertificateViewAdapter = new X509CertificateViewAdapter(cert);
        return dialog;
    }

    @Override
    public void onAttach(Activity activity) {
        Timber.d("onAttach");
        super.onAttach(activity);
        if (!(activity instanceof OnSslUntrustedCertListener)) {
            throw new IllegalArgumentException("The host activity must implement " + OnSslUntrustedCertListener.class.getCanonicalName());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate, savedInstanceState is %s", savedInstanceState);
        super.onCreate(savedInstanceState);
        setRetainInstance(true);    // force to keep the state of the fragment on configuration changes (such as
        // device rotations)
        setCancelable(false);
        mView = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Timber.d("onCreateView, savedInstanceState is %s", savedInstanceState);
        // Create a view by inflating desired layout
        if (mView == null) {
            mView = inflater.inflate(R.layout.ssl_untrusted_cert_layout, container, false);
            mView.findViewById(R.id.details_scroll).setVisibility(View.GONE);
            mErrorViewAdapter.updateErrorView(mView);
        } else {
            ((ViewGroup) mView.getParent()).removeView(mView);
        }

        Button ok = mView.findViewById(R.id.ok);
        ok.setOnClickListener(new OnCertificateTrusted());

        Button cancel = mView.findViewById(R.id.btnCancel);
        cancel.setOnClickListener(new OnCertificateNotTrusted());

        Button details = mView.findViewById(R.id.details_btn);
        details.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                View detailsScroll = mView.findViewById(R.id.details_scroll);
                if (detailsScroll.getVisibility() == View.VISIBLE) {
                    detailsScroll.setVisibility(View.GONE);
                    ((Button) v).setText(R.string.ssl_validator_btn_details_see);

                } else {
                    detailsScroll.setVisibility(View.VISIBLE);
                    ((Button) v).setText(R.string.ssl_validator_btn_details_hide);
                    mCertificateViewAdapter.updateCertificateView(mView);
                }
            }

        });

        return mView;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Timber.d("onCreateDialog, savedInstanceState is %s", savedInstanceState);
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogExtKt.avoidScreenshotsIfNeeded(dialog);
        return dialog;
    }

    @Override
    public void onDestroyView() {
        Timber.d("onDestroyView");
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

    private class OnCertificateNotTrusted implements OnClickListener {

        @Override
        public void onClick(View v) {
            getDialog().cancel();
            if (mHandler != null) {
                mHandler.cancel();
            }
            ((OnSslUntrustedCertListener) getActivity()).onCancelCertificate();
        }
    }

    private class OnCertificateTrusted implements OnClickListener {

        @Override
        public void onClick(View v) {
            dismiss();
            if (mHandler != null) {
                mHandler.proceed();
            }
            if (m509Certificate != null) {
                Activity activity = getActivity();
                try {
                    NetworkUtils.addCertToKnownServersStore(m509Certificate, activity);   // TODO make this
                    // asynchronously, it can take some time
                    ((OnSslUntrustedCertListener) activity).onSavedCertificate();

                } catch (GeneralSecurityException e) {
                    ((OnSslUntrustedCertListener) activity).onFailedSavingCertificate();
                    Timber.e(e, "Server certificate could not be saved in the known-servers trust store ");

                } catch (IOException e) {
                    ((OnSslUntrustedCertListener) activity).onFailedSavingCertificate();
                    Timber.e(e, "Server certificate could not be saved in the known-servers trust store ");
                }
            }
        }

    }

    public interface OnSslUntrustedCertListener {
        void onSavedCertificate();

        void onFailedSavingCertificate();

        void onCancelCertificate();
    }

    public interface ErrorViewAdapter {
        void updateErrorView(View mView);
    }

    public interface CertificateViewAdapter {
        void updateCertificateView(View mView);
    }

}
