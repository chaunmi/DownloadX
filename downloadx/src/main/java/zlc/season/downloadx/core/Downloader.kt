package zlc.season.downloadx.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import zlc.season.downloadx.Progress
import zlc.season.downloadx.net.IRequestResponse
import java.io.File

class QueryProgress(val completableDeferred: CompletableDeferred<Progress>)

interface Downloader {
    var actor: SendChannel<QueryProgress>

    suspend fun queryProgress(): Progress

    suspend fun download(
        downloadParam: DownloadParam,
        downloadConfig: DownloadConfig,
        response: IRequestResponse
    )
}

@OptIn(ObsoleteCoroutinesApi::class)
abstract class BaseDownloader(protected val coroutineScope: CoroutineScope, protected val downloadConfig: DownloadConfig) : Downloader {
    protected var totalSize: Long = 0L
    protected var downloadSize: Long = 0L
    protected var isChunked: Boolean = false

    var lastProgressTimestamp = 0L
    protected val progress = Progress()

    override var actor = GlobalScope.actor<QueryProgress>(Dispatchers.IO) {
        for (each in channel) {
            each.completableDeferred.complete(progress.also {
                it.downloadSize = downloadSize
                it.totalSize = totalSize
                it.isChunked = isChunked
            })
        }
    }

    override suspend fun queryProgress(): Progress {
        val ack = CompletableDeferred<Progress>()
        val queryProgress = QueryProgress(ack)
        actor.send(queryProgress)
        return ack.await()
    }


    protected suspend fun notifyUpdateProgress() = withContext(Dispatchers.Main){
        progress.downloadSize = downloadSize
        progress.totalSize = totalSize
        progress.isChunked = isChunked

        downloadConfig.downloadListener?.let {
            val now = System.currentTimeMillis()
            if(now - lastProgressTimestamp > downloadConfig.progressInterval || downloadSize == totalSize) {
                it.onProgress(progress)
                lastProgressTimestamp = now
            }
        }
    }

    fun DownloadParam.dir(): File {
        return File(savePath)
    }

    fun DownloadParam.file(): File {
        return File(savePath, saveName)
    }
}