package eu.opencloud.android.lib.common.http.methods.nonwebdav

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.io.IOException
import java.net.URL

/**
 * OkHttp PATCH calls wrapper
 */
class PatchMethod(
    url: URL,
    private val patchRequestBody: RequestBody
) : HttpMethod(url) {
    @Throws(IOException::class)
    override fun onExecute(okHttpClient: OkHttpClient): Int {
        request = request.newBuilder()
            .patch(patchRequestBody)
            .build()
        return super.onExecute(okHttpClient)
    }
}
