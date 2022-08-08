package zlc.season.downloadx

import kotlinx.coroutines.CoroutineScope
import zlc.season.downloadx.helper.Default
import zlc.season.downloadx.core.DownloadTask
import zlc.season.downloadx.core.DownloadParam
import zlc.season.downloadx.core.DownloadConfig
import zlc.season.downloadx.core.DownloadManager
import zlc.season.downloadx.utils.getFilePath

fun CoroutineScope.download(
    url: String,
    saveName: String = "",
    savePath: String = "",
    downloadConfig: DownloadConfig = DownloadConfig()
): DownloadTask {
    var filePath = getFilePath(savePath)
    val downloadParam = DownloadParam(url, saveName, filePath)
    val task = DownloadTask(this, downloadParam, downloadConfig)
    return DownloadManager.taskManager.add(task)
}

fun CoroutineScope.download(
    downloadParam: DownloadParam,
    downloadConfig: DownloadConfig = DownloadConfig()
): DownloadTask {
    downloadParam.savePath = getFilePath(downloadParam.savePath)
    val task = DownloadTask(this, downloadParam, downloadConfig)
    return DownloadManager.taskManager.add(task)
}