package zlc.season.downloadx.core

import okio.*
import okio.ByteString.Companion.decodeHex
import zlc.season.downloadx.Progress
import java.io.File

class RangeTmpFile(private val tmpFile: File) {
    private val fileHeader = FileHeader()
    private val fileContent = FileContent()

    /**
     *
     * @param totalSize Long
     * @param totalRanges Long
     * @param rangeSize Long
     */
    fun write(totalSize: Long, totalRanges: Long, rangeSize: Long) {
        tmpFile.sink().buffer().use {
            fileHeader.write(it, totalSize, totalRanges)
            fileContent.write(it, totalSize, totalRanges, rangeSize)
        }
    }

    fun read() {
        tmpFile.source().buffer().use {
            fileHeader.read(it)
            fileContent.read(it, fileHeader.totalRanges)
        }
    }

    fun isValid(totalSize: Long, totalRanges: Long): Boolean {
        return fileHeader.check(totalSize, totalRanges)
    }

    fun undoneRanges(): List<Range> {
        return fileContent.ranges.filter { !it.isComplete() }
    }

    fun lastProgress(): Progress {
        val totalSize = fileHeader.totalSize
        val downloadSize = fileContent.downloadSize()

        return Progress(downloadSize, totalSize)
    }
}

/**
 * Save tmp file base info
 */
private class FileHeader(
    var totalSize: Long = 0L,
    var totalRanges: Long = 0L
) {

    companion object {
        const val FILE_HEADER_MAGIC_NUMBER = "a1b2c3d4e5f6"

        //How to calc: ByteString.decodeHex(FILE_HEADER_MAGIC_NUMBER).size() = 6
        const val FILE_HEADER_MAGIC_NUMBER_SIZE = 6L

        //total header size
        const val FILE_HEADER_SIZE = FILE_HEADER_MAGIC_NUMBER_SIZE + 16L
    }

    fun write(sink: BufferedSink, totalSize: Long, totalRanges: Long) {
        this.totalSize = totalSize
        this.totalRanges = totalRanges

        sink.apply {
            write(FILE_HEADER_MAGIC_NUMBER.decodeHex())
            writeLong(totalSize)
            writeLong(totalRanges)
        }
    }

    fun read(source: BufferedSource) {
        val header = source.readByteString(FILE_HEADER_MAGIC_NUMBER_SIZE).hex()
        if (header != FILE_HEADER_MAGIC_NUMBER) {
            throw IllegalStateException("not a tmp file")
        }
        totalSize = source.readLong()
        totalRanges = source.readLong()
    }

    fun check(totalSize: Long, totalRanges: Long): Boolean {
        return this.totalSize == totalSize &&
                this.totalRanges == totalRanges
    }
}

/**
 * Save file range info
 */
private class FileContent {
    val ranges = mutableListOf<Range>()

    /**
     * @param sink
     * @param totalSize 文件总大小
     * @param totalRanges  分成几片
     * @param rangeSize 每片大小
     */
    fun write(
        sink: BufferedSink,
        totalSize: Long,
        totalRanges: Long,
        rangeSize: Long
    ) {
        ranges.clear()

        slice(totalSize, totalRanges, rangeSize)

        ranges.forEach {
            it.write(sink)
        }
    }

    fun read(source: BufferedSource, totalRanges: Long) {
        ranges.clear()
        for (i in 0 until totalRanges) {
            ranges.add(Range().read(source))
        }
    }

    fun downloadSize(): Long {
        var downloadSize = 0L
        ranges.forEach {
            downloadSize += it.completeSize()
        }
        return downloadSize
    }

    private fun slice(totalSize: Long, totalRanges: Long, rangeSize: Long) {
        var start = 0L

        for (i in 0 until totalRanges) {
            val end = if (i == totalRanges - 1) {
                totalSize - 1
            } else {
                start + rangeSize - 1
            }

            ranges.add(Range(i, start, start, end))

            start += rangeSize
        }
    }
}

/**
 * 前闭后闭[]
 * @property index Long  第几分片
 * @property start Long 注意这个start为文件总大小的基础上的start
 * @property current Long 当前已下载的大小
 * @property end Long 当前分片的结束字符
 * @constructor
 */
class Range(
    var index: Long = 0L,
    var start: Long = 0L,
    var current: Long = 0L,
    var end: Long = 0L  //
) {

    companion object {
        const val CURRENT_BYTES_INDEX = 16
        const val RANGE_SIZE = 32L //each Long is 8 bytes
    }

    fun write(sink: BufferedSink): Range {
        sink.apply {
            writeLong(index)
            writeLong(start)
            writeLong(current)
            writeLong(end)
        }
        return this
    }

    fun read(source: BufferedSource): Range {
        val buffer = Buffer()
        source.readFully(buffer, RANGE_SIZE)

        buffer.apply {
            index = readLong()
            start = readLong()
            current = readLong()
            end = readLong()
        }

        return this
    }

    fun isComplete() = (current - end) == 1L

    fun remainSize() = end - current + 1 //实际上为 end - (current - 1)

    fun completeSize() = current - start

    fun totalSize() = end - start + 1
    /**
     * Return the starting position of the range
     */
    fun startByte() = FileHeader.FILE_HEADER_SIZE + RANGE_SIZE * index
}