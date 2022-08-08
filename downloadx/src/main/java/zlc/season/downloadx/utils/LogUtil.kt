package zlc.season.downloadx.utils

import zlc.season.downloadx.helper.LogUtils

fun <T> T.log(prefix: String = ""): T {
    val prefixStr = if (prefix.isEmpty()) "" else "[$prefix] "
    if (this is Throwable) {
        LogUtils.w(prefixStr + this.message, this)
    } else {
        LogUtils.d(prefixStr + toString())
    }
    return this
}

