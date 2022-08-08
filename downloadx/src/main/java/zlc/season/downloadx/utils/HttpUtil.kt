package zlc.season.downloadx.utils

import zlc.season.downloadx.helper.Default.MAX_RANGES
import zlc.season.downloadx.helper.Default.MIN_RANGE_SIZE
import java.io.Closeable
import java.util.*
import java.util.regex.Pattern


/** Closes this, ignoring any checked exceptions. */
fun Closeable.closeQuietly() {
    try {
        close()
    } catch (rethrown: RuntimeException) {
        throw rethrown
    } catch (_: Exception) {
    }
}


fun calcRanges(cotentLength: Long, rangeSize: Long): Long {
    val remainder = cotentLength % rangeSize
    val result = cotentLength / rangeSize

    return if (remainder == 0L) {
        result
    } else {
        result + 1
    }
}


/**
 * 最小分片大小为10M，最大分片数量为5
 * @receiver Response<*>
 * @param ranges Int
 * @return Pair<Long, Int>
 */
fun calcRanges(contentLength: Long, ranges: Int): Pair<Long, Int> {
    val totalSize = contentLength
    var finalRangeSize = 0L
    var finalRanges = ranges

    if(totalSize < MIN_RANGE_SIZE) {
        finalRanges = 1
        finalRangeSize = MIN_RANGE_SIZE
    } else if(totalSize <= 50L * 1024 * 1024) {
        finalRangeSize = MIN_RANGE_SIZE
        val tempRanges = totalSize / finalRangeSize
        val tempRangSize = totalSize % finalRangeSize
        if(tempRanges < ranges) {
            finalRanges = tempRanges.toInt()
            if(tempRangSize != 0L) {
                finalRanges += 1
            }
        }
    }

    if(finalRanges > MAX_RANGES) {
        finalRanges = MAX_RANGES
    }

    val tempRangeSize = totalSize.toMaxSegmentation(finalRanges.toLong())
    if(tempRangeSize > finalRangeSize) {
        finalRangeSize = tempRangeSize.toMinMultiple(8)
    }

    //溢出之后可能会减少数量
    if(totalSize % finalRangeSize == 0L) {
        finalRanges = (totalSize / finalRangeSize).toInt()
    }

    return Pair(finalRangeSize, finalRanges)
}

fun getFileNameFromContentDisposition(disposition: String?): String {
    val contentDisposition = disposition?.toLowerCase(Locale.getDefault())?: return ""

    if (contentDisposition.isEmpty()) {
        return ""
    }

    val matcher = Pattern.compile(".*filename=(.*)").matcher(contentDisposition)
    if (!matcher.find()) {
        return ""
    }

    var result = matcher.group(1)
    if (result.startsWith("\"")) {
        result = result.substring(1)
    }
    if (result.endsWith("\"")) {
        result = result.substring(0, result.length - 1)
    }

    result = result.replace("/", "_", false)

    return result
}

fun getFileNameFromUrl(url: String): String {
    var temp = url
    if (temp.isNotEmpty()) {
        val fragment = temp.lastIndexOf('#')
        if (fragment > 0) {
            temp = temp.substring(0, fragment)
        }

        val query = temp.lastIndexOf('?')
        if (query > 0) {
            temp = temp.substring(0, query)
        }

        val filenamePos = temp.lastIndexOf('/')
        val filename = if (0 <= filenamePos) temp.substring(filenamePos + 1) else temp

        if (filename.isNotEmpty() && Pattern.matches("[a-zA-Z_0-9.\\-()%]+", filename)) {
            return filename
        }
    }

    return ""
}