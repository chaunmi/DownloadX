package zlc.season.downloadx.net

import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.BufferedSource
import okio.Okio
import okio.buffer
import okio.source
import zlc.season.downloadx.net.RequestHelper.HTTP_PARTIAL
import zlc.season.downloadx.utils.*
import java.io.InputStream

class OkHttpRequestResponse(private val response: Response? = null): IRequestResponse {

    override fun detectFilename(): String {
        if(response == null) {
            return ""
        }
        val requestUrl = response.request.url.toString()
        val contentDisposition = response.header(RequestHelper.CONTENT_DISPOSITION)
        var fileName = getFileNameFromContentDisposition(contentDisposition)
        if(fileName.isEmpty()) {
            fileName = getFileNameFromUrl(requestUrl)
        }
        return fileName
    }

    override fun isSupportRange(): Boolean {
        if (response?.code == HTTP_PARTIAL
            || !response?.header("Content-Range").isNullOrEmpty()
            || response?.header("Accept-Ranges") == "bytes"
        ) {
            return true
        }
        return false
    }

    override fun isChunked(): Boolean {
        return response?.header("Transfer-Encoding") == "chunked"
    }

    override fun isSuccess(): Boolean {
        return response?.isSuccessful.safeUnbox() && response?.body != null
    }

    override fun contentLength(): Long {
        var length = -1L
        response?.header("Content-Length")?.apply {
            length = toLongOrDefault(length)
        }
        return length
    }

    override fun byteStream(): InputStream? {
        return response?.body?.byteStream()
    }

    override fun close() {
        response?.closeQuietly()
    }
}