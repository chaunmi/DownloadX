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

    //TODO 将progress与state进行分离
    private val downloadProgressFlow = MutableStateFlow(0)
    private val downloadStateFlow = MutableStateFlow<State>(stateHolder.none)
    private val stateLock = Object()

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

            notifyWaiting()
            try {
                DownloadManager.downloadQueue.enqueue(this@DownloadTask)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    notifyFailed()
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
                    notifyFailed()
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

                notifyStarted()

                val deferred = async(Dispatchers.IO) { downloader?.download(param, config, response) }
                deferred.await()

                notifySucceed()
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
                notifyStopped()
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
     * @param interval 更新进度间隔时间，单位ms
     * @param ensureLast 能否收到最后一个进度
     */
    fun progress(interval: Long = 200, ensureLast: Boolean = true): Flow<Progress> {
        return downloadProgressFlow.flatMapConcat {
            // make sure send once
            var hasSend = false
            channelFlow {
                while (currentCoroutineContext().isActive) {
                    val progress = getProgress()
                    //TODO 通过一个channel来等待通知
                    if (hasSend && stateHolder.isEnd()) {
                        if (!ensureLast) {
                            break
                        }
                    }

                    send(progress)
                    "url ${param.url} progress ${progress.percentStr()}".log()
                    hasSend = true

                    if (progress.isComplete()) break

                    delay(interval)
                }
            }
        }
    }

    /**
     * @param interval 更新进度间隔时间，单位ms
     */
    //TODO 将state与progress隔离
    fun state(interval: Long = 200): Flow<State> {
        return downloadStateFlow.combine(progress(interval, ensureLast = false)) { l, r -> l.apply { progress = r } }
    }

    fun progress(interval: Long = 200): Flow<Progress> {
        return channelFlow {
            while (currentCoroutineContext().isActive) {

                val state = stateHolder.currentState
                when(state) {
                    is None, is Waiting -> {
                        stateLock.wait()
                    }
                    is State.Downloading -> {
                        val progress = getProgress()
                        send(progress)
                        "url ${param.url} progress ${progress.percentStr()}".log()
                        if (progress.isComplete()) break

                        delay(interval)
                    }
                }
            }
        }
    }

    suspend fun getProgress(): Progress {
        return downloader?.queryProgress() ?: Progress()
    }

    fun getState() = stateHolder.currentState

    private suspend fun notifyWaiting() {
        stateHolder.updateState(stateHolder.waiting, getProgress())
        downloadStateFlow.value = stateHolder.currentState
        "url ${param.url} download task waiting.".log()
    }

    private suspend fun notifyStarted() {
        stateHolder.updateState(stateHolder.downloading, getProgress())
        downloadStateFlow.value = stateHolder.currentState
        downloadProgressFlow.value = downloadProgressFlow.value + 1
        "url ${param.url} download task start.".log()
    }

    private suspend fun notifyStopped() {
        stateHolder.updateState(stateHolder.stopped, getProgress())
        downloadStateFlow.value = stateHolder.currentState
        "url ${param.url} download task stopped.".log()
    }

    private suspend fun notifyFailed() {
        stateHolder.updateState(stateHolder.failed, getProgress())
        downloadStateFlow.value = stateHolder.currentState
        "url ${param.url} download task failed.".log()
    }

    private suspend fun notifySucceed() {
        stateHolder.updateState(stateHolder.succeed, getProgress())
        downloadStateFlow.value = stateHolder.currentState
        "url ${param.url} download task succeed.".log()
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

        fun isEnd(): Boolean {
            return currentState is None || currentState is Waiting || currentState is State.Stopped || currentState is State.Failed || currentState is State.Succeed
        }

        fun updateState(new: State, progress: Progress): State {
            currentState = new.apply { this.progress = progress }
            return currentState
        }
    }
}