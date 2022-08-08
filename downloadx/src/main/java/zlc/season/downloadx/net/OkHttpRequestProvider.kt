package zlc.season.downloadx.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OkHttpRequestProvider private constructor(private val client: OkHttpClient): IRequestProvider {
    private val redirectionCount = AtomicInteger()

    companion object {
       fun  create(client: OkHttpClient): OkHttpRequestProvider {
           return OkHttpRequestProvider(client)
       }
    }

    override fun request(url: String, headerMap: Map<String, String>?): IRequestResponse {
        redirectionCount.set(RequestHelper.MAX_REDIRECTION)
        return OkHttpRequestResponse(innerRequest(client, url, headerMap))
    }

    @Throws(IOException::class) fun innerRequest(
        client: OkHttpClient,
        url: String,
        headerMap: Map<String, String>? = null
    ): Response {
        val builder: Request.Builder = Request.Builder()
            .url(url)

        headerMap?.let {
            for(header in it) {
                builder.header(header.key, header.value)
            }
        }
        val response = client.newCall(builder.build()).execute()
        when (val statusCode = response.code) {
            301, 302, 303, RequestHelper.HTTP_TEMP_REDIRECT -> {
                response.close()
                return if (redirectionCount.decrementAndGet() >= 0) {
                    /* take redirect url and call start recursively */
                    val redirectUrl = response.header(RequestHelper.LOCATION)
                        ?: throw RequestException(statusCode, "redirects got no `Location` header")
                    innerRequest(client, redirectUrl, headerMap)
                } else {
                    throw RequestException(statusCode, "redirects too many times")
                }
            }
        }
        return response
    }
}