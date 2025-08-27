package eu.opencloud.android.lib.common.http.methods.nonwebdav

import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URL

/**
 * OkHttp OPTIONS calls wrapper
 */
class OptionsMethod(url: URL) : HttpMethod(url) {
    @Throws(IOException::class)
    override fun onExecute(okHttpClient: OkHttpClient): Int {
        request = request.newBuilder()
            .method("OPTIONS", null)
            .build()
        return super.onExecute(okHttpClient)
    }
}
