package zlc.season.downloadx.core

import zlc.season.downloadx.helper.Default.DEFAULT_RANGE_CURRENCY
import zlc.season.downloadx.helper.Default.MAX_RANGES
import zlc.season.downloadx.net.IRequestProvider
import zlc.season.downloadx.net.OkHttpRequestProvider

class DownloadConfig(
    /**
     * 分片数量
     */
    val ranges: Int = MAX_RANGES,
    /**
     * 分片下载并行数量
     */
    val rangeCurrency: Int = DEFAULT_RANGE_CURRENCY,
    /**
     * true: 断点续传下载
     * false: 非断点续传下载
     */
    var isRangeDownload: Boolean = true,
    /**
     * http client
     */
    var requestProvider: IRequestProvider = OkHttpRequestProvider.create(DownloadManager.okhttpClient),

    /**
     * progress回调间隔
     */
    var progressInterval: Long = 200L,
    /**
     * 下载回调
     */
    var downloadListener: IDownloadListener? = null
)