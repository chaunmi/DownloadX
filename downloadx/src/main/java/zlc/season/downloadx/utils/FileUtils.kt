package zlc.season.downloadx.utils

import zlc.season.downloadx.core.DownloadManager
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

fun File.shadow(): File {
    val shadowPath = "$canonicalPath.download"
    return File(shadowPath)
}

fun File.tmp(): File {
    val tmpPath = "$canonicalPath.tmp"
    return File(tmpPath)
}

fun File.recreate(length: Long = 0L) {
    delete()
    val created = createNewFile()
    if (created) {
        setLength(length)
    } else {
        throw IllegalStateException("File create failed!")
    }
}

fun File.setLength(length: Long = 0L) {
    RandomAccessFile(this, "rw").setLength(length)
}

fun File.channel(): FileChannel {
    return RandomAccessFile(this, "rw").channel
}

fun File.mappedByteBuffer(position: Long, size: Long): MappedByteBuffer {
    val channel = channel()
    val map = channel.map(FileChannel.MapMode.READ_WRITE, position, size)
    channel.closeQuietly()
    return map
}

fun File.clear() {
    val shadow = shadow()
    val tmp = tmp()
    shadow.delete()
    tmp.delete()
    delete()
}

fun getFilePath(path: String): String {
    var filePath = path
    if(filePath.isEmpty()) {
        filePath = DownloadManager.defaultPath()
        if(filePath.isEmpty()) {
            throw Exception(" savePath is empty and no default path, please set savePath or set Default path with DownloadManage.init() ")
        }
    }
    return filePath
}
