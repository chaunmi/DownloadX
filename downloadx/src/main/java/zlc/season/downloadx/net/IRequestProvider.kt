package zlc.season.downloadx.net

import java.io.IOException

interface IRequestProvider {
    /**
     *  start to download. The downloader should handle redirect
     * status code, such as 301, 302 and so on.
     *
     * @param url
     * @param headerMap
     * @throws IOException throw [IOException] if error occurs
     */
    @Throws(IOException::class)
     fun request(url: String, headerMap: Map<String, String>? = null): IRequestResponse
}