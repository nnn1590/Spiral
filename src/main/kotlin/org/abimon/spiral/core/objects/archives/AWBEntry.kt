package org.abimon.spiral.core.objects.archives

import org.abimon.spiral.core.utils.WindowedInputStream
import org.abimon.visi.io.DataSource
import java.io.InputStream

data class AWBEntry(val id: Int, val fileSize: Long, val offset: Long, val awb: AWB) : DataSource {
    override val location: String = "AWB File ${awb.dataSource.location}, offset $offset bytes"

    override val data: ByteArray
        get() = use { it.readBytes() }

    override val inputStream: InputStream
        get() = WindowedInputStream(awb.dataSource.inputStream, offset, fileSize)
    override val seekableInputStream: InputStream
        get() = WindowedInputStream(awb.dataSource.seekableInputStream, offset, fileSize)

    override val size: Long = fileSize
}