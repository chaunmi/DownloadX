package zlc.season.downloadx.downloader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import zlc.season.downloadx.Progress
import zlc.season.downloadx.core.Default
import zlc.season.downloadx.core.request
import zlc.season.downloadx.task.DownloadConfig
import zlc.season.downloadx.task.DownloadParams
import zlc.season.downloadx.utils.fileName
import zlc.season.downloadx.utils.isSupportRange
import zlc.season.downloadx.utils.log

open class DownloadTask(
    private val downloadParams: DownloadParams,
    private val downloadConfig: DownloadConfig
) {
    private var downloadJob: Job? = null
    private var downloader: Downloader? = null

    private val coroutineScope = downloadConfig.coroutineScope ?: GlobalScope

    private val downloadStateFlow = MutableStateFlow(0)

    fun start() {
        if (downloadJob != null) {
            downloadJob?.cancel()
        }
        downloadJob = coroutineScope.launch {
            val response = request(downloadParams.url, downloadConfig.header)
            if (!response.isSuccessful) {
                "Url [${downloadParams.url}] request failed!".log()
                return@launch
            }

            if (downloadParams.saveName.isEmpty()) {
                downloadParams.saveName = response.fileName()
            }
            if (downloadParams.savePath.isEmpty()) {
                downloadParams.savePath = Default.DEFAULT_SAVE_PATH
            }

            downloader = if (response.isSupportRange()) {
                NormalDownloader(coroutineScope)
            } else {
                NormalDownloader(coroutineScope)
            }
            val downloadJob = async {
                downloader?.download(downloadParams, downloadConfig, response)
            }
            val stateTriggerJob = async { stateTrigger() }
            downloadJob.join()
            stateTriggerJob.join()
        }
    }

    fun stop() {
        downloadJob?.cancel()
    }

    @FlowPreview
    fun progress(interval: Long = 100): Flow<Progress> {
        return downloadStateFlow.flatMapConcat {
            flow {
                var progress = progress()
                if (progress.isComplete()) {
                    emit(progress)
                } else {
                    while (downloadJob?.isActive == true && !progress.isComplete()) {
                        delay(interval)
                        progress = progress()
                        emit(progress)
                    }
                }
            }
        }
    }

    suspend fun progress(): Progress {
        return downloader?.queryProgress() ?: Progress()
    }

    private fun stateTrigger() {
        downloadStateFlow.value = downloadStateFlow.value + 1
    }

    private fun Progress.isComplete(): Boolean {
        return totalSize > 0 && totalSize == downloadSize
    }
}