package zlc.season.downloadx.core

import android.annotation.SuppressLint
import android.content.Context
import zlc.season.downloadx.helper.DefaultLoggerImpl
import zlc.season.downloadx.helper.ILogger
import zlc.season.downloadx.helper.LogUtils

@SuppressLint("StaticFieldLeak")
object DownloadManager {
    /**
     * 下载管理
     */
    val taskManager by lazy {
        config?.taskManager?: DefaultTaskManager()
    }
    /**
     * 下载队列
     */
    val downloadQueue by lazy {
        config?.downloadQueue?: DefaultDownloadQueue()
    }

    /**
     * 生成downloader
     */
    val dispatcher by lazy {
        config?.downloaderDispatcher?: DefaultDownloadDispatcher()
    }

    /**
     * 默认Okhttp，共用一个Okhttp
     */
    val okhttpClient by lazy {
        config?.okhttpClientFactory?.create()?: DefaultHttpClientFactory().create()
    }

    val fileValidator by lazy {
        config?.fileValidator?: DefaultFileValidator()
    }

    var context: Context? = null
    private set

    private var config: Config? = null

    fun defaultPath(): String {
        return context?.filesDir?.path?: ""
    }

    fun init(config: Config? = null, context: Context? = null) {
        this.config = config
        this.context = context?.applicationContext
        LogUtils.setLogger(config?.logger?: DefaultLoggerImpl())
    }

    class Config (
        var logger: ILogger? = null,
        var downloadQueue: DownloadQueue? = null,
        var taskManager: TaskManager? = null,
        var downloaderDispatcher: DownloadDispatcher? = null,
        val fileValidator: FileValidator? = null,
        var okhttpClientFactory: HttpClientFactory? = null
    )
}