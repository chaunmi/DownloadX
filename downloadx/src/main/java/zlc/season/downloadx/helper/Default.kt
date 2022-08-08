package zlc.season.downloadx.helper

object Default {
    /**
     * 最小分片大小
     */
    const val MIN_RANGE_SIZE = 10L * 1024 * 1024
    /**
     * 最大分片数量
     */
    const val MAX_RANGES = 5

    /**
     * 单个任务同时下载的分片数量
     */
    const val DEFAULT_RANGE_CURRENCY = 5

    /**
     * 同时下载的任务数量
     */
    const val MAX_TASK_NUMBER = 3

    /**
     * 默认的Header
     */
    val RANGE_CHECK_HEADER = mapOf("Range" to "bytes=0-")
}