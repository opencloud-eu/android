package eu.opencloud.android.lib.common.http.methods.nonwebdav

import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URL

/**
 * OkHttp HEAD calls wrapper
 */
class HeadMethod(url: URL) : HttpMethod(url) {
    @Throws(IOException::class)
    override fun onExecute(okHttpClient: OkHttpClient): Int {
        request = request.newBuilder()
            .head()
            .build()
        return super.onExecute(okHttpClient)
    }
}
