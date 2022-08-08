package zlc.season.downloadx.core

import okhttp3.OkHttpClient
import okhttp3.Protocol
import zlc.season.downloadx.net.IRequestResponse
import java.io.File
import java.util.concurrent.TimeUnit

interface HttpClientFactory {
    fun create(): OkHttpClient
}

class DefaultHttpClientFactory : HttpClientFactory {
    override fun create(): OkHttpClient {
        return OkHttpClient().newBuilder()
            .protocols(arrayListOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

interface DownloadDispatcher {
    fun dispatch(downloadTask: DownloadTask, response: IRequestResponse): Downloader
}

class DefaultDownloadDispatcher : DownloadDispatcher {
    override fun dispatch(downloadTask: DownloadTask, response: IRequestResponse): Downloader {
        return if (downloadTask.config.isRangeDownload && response.isSupportRange()) {
            RangeDownloader(downloadTask.coroutineScope)
        } else {
            NormalDownloader(downloadTask.coroutineScope)
        }
    }
}

interface FileValidator {
    fun validate(
        file: File,
        param: DownloadParam,
        resp: IRequestResponse
    ): Boolean
}

class DefaultFileValidator : FileValidator {
    override fun validate(
        file: File,
        param: DownloadParam,
        resp: IRequestResponse
    ): Boolean {
        return file.length() == resp.contentLength()
    }
}