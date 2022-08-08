package zlc.season.downloadx.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.*
import zlc.season.downloadx.Progress
import zlc.season.downloadx.State
import zlc.season.downloadx.State.None
import zlc.season.downloadx.State.Waiting
import zlc.season.downloadx.helper.Default
import zlc.season.downloadx.utils.clear
import zlc.season.downloadx.utils.closeQuietly
import zlc.season.downloadx.utils.getFilePath
import zlc.season.downloadx.utils.log
import java.io.File

@OptIn(ObsoleteCoroutinesApi::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
open class DownloadTask(
    val coroutineScope: CoroutineScope,
    val param: DownloadParam,
    val config: DownloadConfig
) {
    private val stateHolder by lazy { StateHolder() }

    private var downloadJob: Job? = null
    private var downloader: Downloader? = null

    private val downloadStateFlow = MutableStateFlow<State>(stateHolder.none)

    fun isStarted(): Boolean {
        return stateHolder.isStarted()
    }

    fun isFailed(): Boolean {
        return stateHolder.isFailed()
    }

    fun isSucceed(): Boolean {
        return stateHolder.isSucceed()
    }

    fun canStart(): Boolean {
        return stateHolder.canStart()
    }

    private fun checkJob() = downloadJob?.isActive == true

    /**
     * 获取下载文件
     */
    fun file(): File? {
        return if (param.saveName.isNotEmpty() && param.savePath.isNotEmpty()) {
            File(param.savePath, param.saveName)
        } else {
            null
        }
    }

    /**
     * 开始下载，添加到下载队列
     */
    fun start() {
        coroutineScope.launch {
            if (checkJob()) return@launch

            notifyStateUpdate(stateHolder.waiting)
            try {
                DownloadManager.downloadQueue.enqueue(this@DownloadTask)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    notifyFailed(e)
                }
                e.log()
            }
        }
    }

    /**
     * 开始下载并等待下载完成，直接开始下载，不添加到下载队列
     */
    suspend fun suspendStart() {
        if (checkJob()) return

        downloadJob?.cancel()
        val errorHandler = CoroutineExceptionHandler { _, throwable ->
            throwable.log()
            if (throwable !is CancellationException) {
                coroutineScope.launch {
                    notifyFailed(throwable)
                }
            }
        }
        downloadJob = coroutineScope.launch(errorHandler + Dispatchers.IO) {
            val response = config.requestProvider.request(param.url, Default.RANGE_CHECK_HEADER)

            try {
                if (!response.isSuccess()) {
                    throw RuntimeException("request failed")
                }

                param.savePath = getFilePath(param.savePath)

                if (param.saveName.isEmpty()) {
                    param.saveName = response.detectFilename()
                }

                if (downloader == null) {
                    downloader = DownloadManager.dispatcher.dispatch(this@DownloadTask, response)
                }

                notifyStateUpdate(stateHolder.downloading)

                val deferred = async(Dispatchers.IO) { downloader?.download(param, config, response) }
                deferred.await()

                notifyStateUpdate(stateHolder.succeed)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    notifyFailed()
                }
                e.log()
            } finally {
                response.closeQuietly()
            }
        }
        downloadJob?.join()
    }

    /**
     * 停止下载
     */
    fun stop() {
        coroutineScope.launch {
            if (isStarted()) {
                DownloadManager.downloadQueue.dequeue(this@DownloadTask)
                downloadJob?.cancel()
                notifyStateUpdate(stateHolder.stopped)
            }
        }
    }

    /**
     * 移除任务
     */
    fun remove(deleteFile: Boolean = true) {
        stop()
        DownloadManager.taskManager.remove(this)
        if (deleteFile) {
            file()?.clear()
        }
    }

    /**
     * 监听下载状态
     * @return Flow<State>
     */
    fun downloadStateFlow(): Flow<State> {
        return downloadStateFlow
    }

    suspend fun getProgress(): Progress {
        return downloader?.queryProgress() ?: Progress()
    }

    fun getCurrentState() = stateHolder.currentState

    private suspend fun notifyFailed(tr: Throwable? = null) {
        stateHolder.failed.throwable = tr
        notifyStateUpdate(stateHolder.failed)
    }

    private suspend fun notifyStateUpdate(state: State) {
        stateHolder.updateState(state)
        downloadStateFlow.value = stateHolder.currentState
        notifyStateChange(stateHolder.currentState)
        "url ${param.url} download task $state.".log()
    }

    private suspend fun notifyStateChange(state: State) = withContext(Dispatchers.Main){
        config.downloadListener?.onStateChange(state)
    }

    private fun Progress.isComplete(): Boolean {
        return totalSize > 0 && totalSize == downloadSize
    }

    class StateHolder {
        val none by lazy { None() }
        val waiting by lazy { Waiting() }
        val downloading by lazy { State.Downloading() }
        val stopped by lazy { State.Stopped() }
        val failed by lazy { State.Failed() }
        val succeed by lazy { State.Succeed() }

        var currentState: State = none

        fun isStarted(): Boolean {
            return currentState is Waiting || currentState is State.Downloading
        }

        fun isFailed(): Boolean {
            return currentState is State.Failed
        }

        fun isSucceed(): Boolean {
            return currentState is State.Succeed
        }

        fun canStart(): Boolean {
            return currentState is None || currentState is State.Failed || currentState is State.Stopped
        }

        fun updateState(new: State): State {
            currentState = new
            return currentState
        }
    }
}