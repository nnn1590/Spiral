package info.spiralframework.core.formats

import info.spiralframework.formats.game.DRGame
import info.spiralframework.formats.utils.BLANK_DATA_CONTEXT
import info.spiralframework.formats.utils.DataContext
import info.spiralframework.formats.utils.DataSource
import java.io.OutputStream

interface SpiralFormat {
    //val name: String
    //val extension: String?
}

/**
 * A Spiral format that supports reading from a source
 */
interface ReadableSpiralFormat<T>: SpiralFormat {

    /**
     * Attempts to read the data source as [T]
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     * @param source A function that returns an input stream
     *
     * @return a FormatResult containing either [T] or null, if the stream does not contain the data to form an object of type [T]
     */
    fun read(name: String? = null, game: DRGame? = null, context: DataContext = BLANK_DATA_CONTEXT, source: DataSource): FormatResult<T>
}

/**
 * A Spiral format that supports writing to a stream
 */
interface WritableSpiralFormat: SpiralFormat {
    /**
     * Does this format support writing [data]?
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     *
     * @return If we are able to write [data] as this format
     */
    fun supportsWriting(data: Any): Boolean

    /**
     * Writes [data] to [stream] in this format
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     * @param data The data to wrote
     * @param stream The stream to write to
     *
     * @return An enum for the success of the operation
     */
    fun write(name: String? = null, game: DRGame? = null, context: DataContext = BLANK_DATA_CONTEXT, data: Any, stream: OutputStream): EnumFormatWriteResponse
}

enum class EnumFormatWriteResponse {
    SUCCESS,
    WRONG_FORMAT,
    FAIL
}