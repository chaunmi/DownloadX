package zlc.season.downloadx.net

import java.io.Closeable
import java.io.InputStream

interface IRequestResponse: Closeable {
    /**
     * 从 http header/url 中获取文件名，如果存在的话
     */
    fun detectFilename(): String

    /**
     * 是否支持断点续传
     * @return Boolean
     */
    fun isSupportRange(): Boolean

    /**
     * 是否是分片传输方式 Transfer-Encoding=chunked
     * @return Boolean
     */
    fun isChunked(): Boolean

    fun isSuccess(): Boolean

    fun contentLength(): Long

    fun byteStream(): InputStream?
}