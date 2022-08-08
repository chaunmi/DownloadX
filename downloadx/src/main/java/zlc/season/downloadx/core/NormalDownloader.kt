package zlc.season.downloadx.core

import android.util.Log
import kotlinx.coroutines.*
import okio.buffer
import okio.sink
import zlc.season.downloadx.net.IRequestResponse
import zlc.season.downloadx.utils.closeQuietly
import zlc.season.downloadx.utils.recreate
import zlc.season.downloadx.utils.shadow
import java.io.File

@OptIn(ObsoleteCoroutinesApi::class)
class NormalDownloader(coroutineScope: CoroutineScope, downloadConfig: DownloadConfig) : BaseDownloader(coroutineScope, downloadConfig) {
    companion object {
        private const val BUFFER_SIZE = 8192
    }

    private var alreadyDownloaded = false

    private lateinit var file: File
    private lateinit var shadowFile: File

    override suspend fun download(
        downloadParam: DownloadParam,
        downloadConfig: DownloadConfig,
        response: IRequestResponse
    ) {
        try {
            file = downloadParam.file()
            shadowFile = file.shadow()

            val contentLength = response.contentLength()
            val isChunked = response.isChunked()

            downloadPrepare(downloadParam, contentLength)

            if (alreadyDownloaded) {
                this.downloadSize = contentLength
                this.totalSize = contentLength
                this.isChunked = isChunked
                notifyUpdateProgress()
            } else {
                this.totalSize = contentLength
                this.downloadSize = 0
                this.isChunked = isChunked
                notifyUpdateProgress()
                startDownload(response)
            }
        } finally {
            response.closeQuietly()
        }
    }

    private fun downloadPrepare(downloadParam: DownloadParam, contentLength: Long) {
        //make sure dir is exists
        val fileDir = downloadParam.dir()
        if (!fileDir.exists() || !fileDir.isDirectory) {
            fileDir.mkdirs()
        }

        if (file.exists()) {
            if (file.length() == contentLength) {
                alreadyDownloaded = true
            } else {
                file.delete()
                shadowFile.recreate()
            }
        } else {
            shadowFile.recreate()
        }
    }

    private suspend fun startDownload(response: IRequestResponse) = coroutineScope {
        val startTime = System.currentTimeMillis()
        val deferred = async(Dispatchers.IO) {
            response.byteStream()?.let { source ->
                val sink = shadowFile.sink().buffer()
                val buffer = ByteArray(BUFFER_SIZE)
                var readLen = source.read(buffer)
                while (isActive && readLen != -1) {
                    sink.write(buffer, 0, readLen)
                    downloadSize += readLen
                    notifyUpdateProgress()
                    readLen = source.read(buffer)
                }
                sink.closeQuietly()
            }
        }
        deferred.await()
        Log.i("myTest", " normal downloader cost: ${System.currentTimeMillis() - startTime} , url: ${file.name}")
        if (isActive) {
            shadowFile.renameTo(file)
        }
    }
}