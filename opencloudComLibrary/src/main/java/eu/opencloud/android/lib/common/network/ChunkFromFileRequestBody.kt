/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2022 ownCloud GmbH.
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
package eu.opencloud.android.lib.common.network

import eu.opencloud.android.lib.resources.files.chunks.ChunkedUploadFromFileSystemOperation.Companion.CHUNK_SIZE
import okhttp3.MediaType
import okio.BufferedSink
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * A Request body that represents a file chunk and include information about the progress when uploading it
 *
 * @author David González Verdugo
 */
class ChunkFromFileRequestBody(
    file: File,
    contentType: MediaType?,
    private val channel: FileChannel,
    private val chunkSize: Long = CHUNK_SIZE
) : FileRequestBody(file, contentType) {

    private var offset: Long = 0
    private var alreadyTransferred: Long = 0
    private val buffer = ByteBuffer.allocate(4_096)

    init {
        require(chunkSize > 0) { "Chunk size must be greater than zero" }
    }

    override fun contentLength(): Long =
        chunkSize.coerceAtMost((channel.size() - offset).coerceAtLeast(0))

    override fun writeTo(sink: BufferedSink) {
        var readCount: Int
        var iterator: Iterator<OnDatatransferProgressListener>
        try {
            channel.position(offset)

            val maxCount = (offset + chunkSize).coerceAtMost(channel.size())
            while (channel.position() < maxCount) {
                val remainingForChunk = (maxCount - channel.position()).toInt()
                if (remainingForChunk <= 0) break
                // limit how much we read so we never consume past the chunk boundary
                val toRead = minOf(buffer.capacity(), remainingForChunk)
                buffer.limit(toRead)
                readCount = channel.read(buffer)
                if (readCount == -1) break
                sink.buffer.write(buffer.array(), 0, readCount)
                sink.flush()
                buffer.clear()

                if (readCount > 0) {
                    alreadyTransferred = (alreadyTransferred + readCount.toLong()).coerceAtMost(chunkSize)
                }

                synchronized(dataTransferListeners) {
                    iterator = dataTransferListeners.iterator()
                    while (iterator.hasNext()) {
                        iterator.next().onTransferProgress(readCount.toLong(), alreadyTransferred, file.length(), file.absolutePath)
                    }
                }
            }
        } catch (exception: Exception) {
            Timber.e(exception, "Transferred " + alreadyTransferred + " bytes from a total of " + file.length())
        }
    }

    fun setOffset(newOffset: Long) {
        offset = newOffset
    }

}
