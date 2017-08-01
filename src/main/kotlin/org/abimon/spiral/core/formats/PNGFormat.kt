package org.abimon.spiral.core.formats

import org.abimon.spiral.core.toJPG
import org.abimon.spiral.core.toTGA
import org.abimon.spiral.core.writeNumber
import org.abimon.visi.collections.byteArrayOf
import org.abimon.visi.io.DataSource
import java.awt.Color
import java.io.OutputStream
import javax.imageio.ImageIO

object PNGFormat : SpiralFormat {
    override val name = "PNG"
    override val extension = "png"
    override val conversions: Array<SpiralFormat> = arrayOf(TGAFormat, SHTXFormat, JPEGFormat)

    override fun isFormat(source: DataSource): Boolean =
            source.use {
                ImageIO.getImageReaders(ImageIO.createImageInputStream(it))
                        .asSequence()
                        .any { it.formatName.toLowerCase() == "png" }
            }

    override fun convert(format: SpiralFormat, source: DataSource, output: OutputStream, params: Map<String, Any?>) {
        super.convert(format, source, output, params)

        source.use {
            when (format) {
                is TGAFormat -> output.write(ImageIO.read(it).toTGA())
                is JPEGFormat -> ImageIO.write(ImageIO.read(it).toJPG(), "JPG", output)
                is SHTXFormat -> {
                    output.write("SHTX".toByteArray())

                    val img = ImageIO.read(it)
                    var palette: List<Color> = img.run {
                        val pixels = ArrayList<Color>()

                        for(y in 0 until height)
                            for(x in 0 until width)
                                pixels.add(Color(getRGB(x, y), true))

                        return@run pixels
                    }

                    if(palette.distinctBy { it.rgb }.size > 256) {
                        output.write("Ff".toByteArray())
                        output.writeNumber(img.width.toLong(), 2, true)
                        output.writeNumber(img.height.toLong(), 2, true)
                        output.writeNumber(0, 2, true)

                        palette.forEach { colour -> output.write(byteArrayOf(colour.red, colour.green, colour.blue, colour.alpha)) }
                    } else {
                        palette = palette.distinctBy { it.rgb }

                        output.write("Fs".toByteArray())
                        output.writeNumber(img.width.toLong(), 2, true)
                        output.writeNumber(img.height.toLong(), 2, true)
                        output.writeNumber(0, 2, true)

                        palette.forEach { colour -> output.write(byteArrayOf(colour.red, colour.green, colour.blue, colour.alpha)) }

                        img.run {
                            for(y in 0 until height)
                                for(x in 0 until width)
                                    output.write(palette.indexOf(Color(getRGB(x, y), true)))
                        }
                    }
                }
                else -> {
                }
            }
        }
    }
}