package zlc.season.downloadx.core

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import zlc.season.downloadx.core.Range.Companion.RANGE_SIZE
import zlc.season.downloadx.net.IRequestResponse
import zlc.season.downloadx.utils.*
import java.io.File

@OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class RangeDownloader(coroutineScope: CoroutineScope, downloadConfig: DownloadConfig) : BaseDownloader(coroutineScope, downloadConfig) {
    private lateinit var file: File
    private lateinit var shadowFile: File  //下载的临时文件
    private lateinit var tmpFile: File  //存储文件相关信息，包括总大小，分片信息等
    private lateinit var rangeTmpFile: RangeTmpFile

    override suspend fun download(
        downloadParam: DownloadParam,
        downloadConfig: DownloadConfig,
        response: IRequestResponse
    ) {
        try {
            file = downloadParam.file()
            shadowFile = file.shadow()
            tmpFile = file.tmp()

            val alreadyDownloaded = checkFiles(downloadParam, downloadConfig, response)

            if (alreadyDownloaded) {
                downloadSize = response.contentLength()
                totalSize = response.contentLength()
                notifyUpdateProgress()
            } else {
                val last = rangeTmpFile.lastProgress()
                downloadSize = last.downloadSize
                totalSize = last.totalSize
                notifyUpdateProgress()
                startDownload(downloadParam, downloadConfig)
            }
        } finally {
            response.closeQuietly()
        }
    }

    private fun checkFiles(
        param: DownloadParam,
        config: DownloadConfig,
        response: IRequestResponse
    ): Boolean {
        var alreadyDownloaded = false

        //make sure dir is exists
        val fileDir = param.dir()
        if (!fileDir.exists() || !fileDir.isDirectory) {
            fileDir.mkdirs()
        }
        //总大小
        val contentLength = response.contentLength()

        val calcRanges = calcRanges(contentLength, config.ranges)
        val rangeSize = calcRanges.first
        val totalRanges = calcRanges.second.toLong()

        Log.e("myTest", " slice totalSize: $contentLength, ${contentLength.formatSize()} rangeSize: $rangeSize, totalRanges: $totalRanges ")
        if (file.exists()) {
            if (DownloadManager.fileValidator.validate(file, param, response)) {
                alreadyDownloaded = true
            } else {
                file.delete()
                recreateFiles(contentLength, totalRanges, rangeSize)
            }
        } else {
            if (shadowFile.exists() && tmpFile.exists()) {
                rangeTmpFile = RangeTmpFile(tmpFile)
                rangeTmpFile.read()

                if (!rangeTmpFile.isValid(contentLength, totalRanges)) {
                    recreateFiles(contentLength, totalRanges, rangeSize)
                }
            } else {
                recreateFiles(contentLength, totalRanges, rangeSize)
            }
        }

        return alreadyDownloaded
    }

    /**
     *
     * @param contentLength Long  文件总大小
     * @param totalRanges Long  总共分成几片
     * @param rangeSize Long  每片的大小
     */
    private fun recreateFiles(contentLength: Long, totalRanges: Long, rangeSize: Long) {
        tmpFile.recreate()
        shadowFile.recreate(contentLength)
        rangeTmpFile = RangeTmpFile(tmpFile)
        rangeTmpFile.write(contentLength, totalRanges, rangeSize)
    }

    private suspend fun startDownload(param: DownloadParam, config: DownloadConfig) {
        val progressChannel = coroutineScope.actor<Int> {
            channel.consumeEach {
                downloadSize += it
                notifyUpdateProgress()
            }
        }

        rangeTmpFile.undoneRanges().parallel(max = config.rangeCurrency) {
            it.download(param, config, progressChannel)
        }

        progressChannel.close()
        shadowFile.renameTo(file)
        tmpFile.delete()
    }

    private suspend fun Range.download(
        param: DownloadParam,
        config: DownloadConfig,
        sendChannel: SendChannel<Int>
    ) = coroutineScope {
        val deferred = async(Dispatchers.IO) {
            val url = param.url
            val rangeHeader = mapOf("Range" to "bytes=${current}-${end}")

            val response = config.requestProvider.request(url, rangeHeader)
            if (!response.isSuccess()) {
                throw RuntimeException("Request failed!")
            }
            response.byteStream()?.let {
                it.use { source ->
                    val tmpFileBuffer = tmpFile.mappedByteBuffer(startByte(), RANGE_SIZE)
                    val shadowFileBuffer = shadowFile.mappedByteBuffer(current, remainSize())

                    val buffer = ByteArray(8192)
                    var readLen = source.read(buffer)
                    try {
                        while (isActive && readLen != -1) {
                            shadowFileBuffer.put(buffer, 0, readLen)
                            current += readLen
                            //current 是第三个long写入值，index为写入字节的位置
                            tmpFileBuffer.putLong(Range.CURRENT_BYTES_INDEX, current)

                            sendChannel.send(readLen)

                            Log.d("myTest", " downloading index: $index, current: $current, percent: ${ (completeSize() / (totalSize() * 1.0f))} ")
                            readLen = source.read(buffer)
                        }
                    }catch (e: Exception) {
                        e.printStackTrace()
                        Log.e("myTest", e.localizedMessage)
                    }
                    Log.e("myTest", "------------------------- download end index: $index ------------------------")
                }
            }
        }
        deferred.await()
    }
}