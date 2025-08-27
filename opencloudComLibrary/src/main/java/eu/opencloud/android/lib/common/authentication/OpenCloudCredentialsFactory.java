/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2020 ownCloud GmbH.
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 *
 */

package eu.opencloud.android.lib.common.authentication;

public class OpenCloudCredentialsFactory {

    public static final String CREDENTIAL_CHARSET = "UTF-8";

    private static OpenCloudAnonymousCredentials sAnonymousCredentials;

    public static OpenCloudCredentials newBasicCredentials(String username, String password) {
        return new OpenCloudBasicCredentials(username, password);
    }

    public static OpenCloudCredentials newBearerCredentials(String username, String authToken) {
        return new OpenCloudBearerCredentials(username, authToken);
    }

    public static final OpenCloudCredentials getAnonymousCredentials() {
        if (sAnonymousCredentials == null) {
            sAnonymousCredentials = new OpenCloudAnonymousCredentials();
        }
        return sAnonymousCredentials;
    }

    public static final class OpenCloudAnonymousCredentials implements OpenCloudCredentials {

        protected OpenCloudAnonymousCredentials() {
        }

        @Override
        public String getAuthToken() {
            return "";
        }

        @Override
        public String getHeaderAuth() {
            return "";
        }

        @Override
        public boolean authTokenExpires() {
            return false;
        }

        @Override
        public boolean authTokenCanBeRefreshed() {
            return false;
        }

        @Override
        public String getUsername() {
            // no user name
            return null;
        }
    }
}