package zlc.season.downloadx.utils

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


/* get uuid without '-' 及所有不可打印的 ASCII 字符(ASCII 代码小于或等于空格) */
val uuid: String
    get() = UUID.randomUUID()
        .toString()
        .trim { it <= ' ' }
        .replace("-".toRegex(), "")

/* caculate md5 for string */
 fun String.md5(): String {
    return try {
        val md = MessageDigest.getInstance("MD5")
        md.update(this.toByteArray(StandardCharsets.UTF_8))
        val bi = BigInteger(1, md.digest())
        val hash = StringBuilder(bi.toString(16))
        while (hash.length < 32) {
            hash.insert(0, "0")
        }
        hash.toString()
    } catch (e: Exception) {
        uuid
    }
}

fun String.toLongOrDefault(defaultValue: Long): Long {
    return try {
        toLong()
    } catch (_: NumberFormatException) {
        defaultValue
    }
}

fun Boolean?.safeUnbox(): Boolean = this != null && this

fun Long.formatSize(): String {
    require(this >= 0) { "Size must larger than 0." }

    val byte = this.toDouble()
    val kb = byte / 1024.0
    val mb = byte / 1024.0 / 1024.0
    val gb = byte / 1024.0 / 1024.0 / 1024.0
    val tb = byte / 1024.0 / 1024.0 / 1024.0 / 1024.0

    return when {
        tb >= 1 -> "${tb.decimal(2)} TB"
        gb >= 1 -> "${gb.decimal(2)} GB"
        mb >= 1 -> "${mb.decimal(2)} MB"
        kb >= 1 -> "${kb.decimal(2)} KB"
        else -> "${byte.decimal(2)} B"
    }
}

fun Double.decimal(digits: Int): Double {
    return this.toBigDecimal()
        .setScale(digits, BigDecimal.ROUND_HALF_UP)
        .toDouble()
}

infix fun Long.ratio(bottom: Long): Double {
    if (bottom <= 0) {
        return 0.0
    }
    val result = (this * 100.0).toBigDecimal()
        .divide((bottom * 1.0).toBigDecimal(), 2, BigDecimal.ROUND_FLOOR)
    return result.toDouble()
}

/**
 * max分片下载并行数量
 */
suspend fun <T, R> (Collection<T>).parallel(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    max: Int = 2,
    action: suspend CoroutineScope.(T) -> R
): Iterable<R> = coroutineScope {
    val list = this@parallel
    if (list.isEmpty()) return@coroutineScope listOf<R>()

    val channel = Channel<T>()
    val output = Channel<R>()

    val counter = AtomicInteger(0)

    launch {
        list.forEach { channel.send(it) }
        channel.close()
    }

    repeat(max) {
        launch(dispatcher) {
            channel.consumeEach {
                output.send(action(it))
                val completed = counter.incrementAndGet()
                if (completed == list.size) {
                    output.close()
                }
            }
        }
    }

    val results = mutableListOf<R>()
    for (item in output) {
        results.add(item)
    }

    return@coroutineScope results
}

/**
 * 返回满足当前值对某个数的最小倍数值
 * @receiver Long
 * @param multiple Long
 * @return Long
 */
fun Long.toMinMultiple(multiple: Long): Long {
    if(multiple <= 1) {
        return this
    }
    val remainder = this % multiple
    return this + multiple - remainder
}

/**
 * 返回将当前值分割成多少份的最大值
 * @receiver Long
 * @param multiple Long
 * @return Long
 */
fun Long.toMaxSegmentation(segmentation: Long): Long {
    if(segmentation <= 1) {
        return this
    }
    var result = this / segmentation
    val remainder = this % segmentation
    if(remainder == 0L) {
        return result
    }
    return result + 1
}