package org.abimon.spiral.mvc.gurren

import com.jakewharton.fliptables.FlipTable
import org.abimon.imperator.impl.InstanceOrder
import org.abimon.spiral.core.SpiralFormats
import org.abimon.spiral.core.debug
import org.abimon.spiral.core.formats.*
import org.abimon.spiral.core.isDebug
import org.abimon.spiral.mvc.SpiralModel
import org.abimon.spiral.mvc.SpiralModel.Command
import org.abimon.spiral.util.MediaWrapper
import org.abimon.visi.collections.copyFrom
import org.abimon.visi.collections.group
import org.abimon.visi.collections.joinToPrefixedString
import org.abimon.visi.io.*
import org.abimon.visi.lang.EnumOS
import org.abimon.visi.lang.replaceLast
import java.io.File
import java.io.FileFilter
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.*
import kotlin.system.measureTimeMillis

@Suppress("unused")
object Gurren {
    val os = EnumOS.determineOS()
    val ignoreFilters: Array<FileFilter> = arrayOf(
            FileFilter { file -> !file.name.startsWith(".") },
            FileFilter { file -> !file.name.startsWith("__") },
            FileFilter { file -> !Files.isHidden(file.toPath()) },
            FileFilter { file -> !Files.isSymbolicLink(file.toPath()) },
            FileFilter { file -> Files.isReadable(file.toPath()) }
    )
    val identifyFormats: Array<SpiralFormat> = SpiralFormats.formats.filter { it !is TXTFormat }.toTypedArray()
    val separator: String = File.separator
    var keepLooping: Boolean = true

    val helpTable: String = FlipTable.of(
            arrayOf("Command", "Arguments", "Description", "Example Command"),
            arrayOf(
                    arrayOf("help", "", "Display this message", ""),
                    arrayOf("find", "", "Try to find the WAD files for DR1/2, if they're in their normal locations", ""),
                    arrayOf("locate", "[directory]", "Search [directory] for all WAD files. Note: Will take a fair amount of time, and requires confirmation for each WAD file.", "find \"Steam${separator}steamapps${separator}common${separator}Danganronpa: Trigger Happy Havoc\""),
                    arrayOf("register", "[wad]", "Register an individual WAD file", "register \"dr1_data_us.wad\""),
                    arrayOf("registered", "", "Display the registered WAD files", ""),
                    arrayOf("formats", "", "Display the formats table", ""),
                    arrayOf("identify", "[file|directory]", "Identify the format of either the provided [file], or the files in the provided [directory]", "identify \"images\""),
                    arrayOf("exit", "", "Exits the program", "")
            )
    )

    val formatTable: String = FlipTable.of(
            arrayOf("Format", "Can Convert To"),
            arrayOf(
                    arrayOf("WAD", WADFormat.conversions.joinToString { it.name }),
                    arrayOf("PAK", PAKFormat.conversions.joinToString { it.name }),
                    arrayOf("TGA", TGAFormat.conversions.joinToString { it.name }),
                    arrayOf("SHTX", SHTXFormat.conversions.joinToString { it.name }),
                    arrayOf("PNG", PNGFormat.conversions.joinToString { it.name }),
                    arrayOf("JPG", JPEGFormat.conversions.joinToString { it.name }),
                    arrayOf("LIN", LINFormat.conversions.joinToString { it.name }),
                    arrayOf("Nonstop DAT", NonstopFormat.conversions.joinToString { it.name })
            )
    )

    val help = Command("help", "default") { println(helpTable) }
    val find = Command("find") {
        when (os) {
            EnumOS.WINDOWS -> {
                for (root in File.listRoots()) {
                    for (programFolder in arrayOf(File(root, "Program Files (x86)"), File(root, "Program Files"))) {
                        val steamFolder = File(programFolder, "Steam")
                        if (steamFolder.exists()) {
                            val common = File(steamFolder, "steamapps${File.separator}common")
                            for (game in common.listFiles { file -> file.isDirectory && file.name.contains("Danganronpa") })
                                SpiralModel.wads.addAll(game.listFiles { file -> file.isFile && file.extension == "wad" && !file.name.contains(".backup") })
                        }
                    }
                }
            }
            EnumOS.MACOSX -> {
                val steamFolder = os.getStorageLocation("Steam")
                if (steamFolder.exists()) {
                    val common = File(steamFolder, "steamapps${File.separator}common")
                    for (game in common.listFiles { file -> file.isDirectory && file.name.contains("Danganronpa") }) {
                        val appDirs = game.listFiles { file -> file.isDirectory && file.extension == "app" }
                        if (appDirs.isNotEmpty()) {
                            SpiralModel.wads.addAll(
                                    appDirs.flatMap<File, File> { app ->
                                        File(app, "Contents${File.separator}Resources").listFiles { file ->
                                            file.isFile && file.extension == "wad" && !file.name.contains(".backup")
                                        }.toList()
                                    }
                            )
                        }
                    }
                }
            }
            else -> println("No behaviour defined for ${os}!")
        }

        if (SpiralModel.wads.isEmpty())
            errPrintln("Error: No WAD files detected! You can manually add them via the register command, or by running the locate command!")
        else
            println("WADs: ${SpiralModel.wads.joinToPrefixedString("", "\n\t")}")
    }
    val locate = Command("locate") { (operation) ->
        if (operation.size == 1) {
            errPrintln("Error: No directory provided!")
            return@Command
        }

        val dir = File(operation.copyFrom(1).joinToString(" "))
        if (!dir.exists()) {
            errPrintln("Error: $dir does not exist!")
            return@Command
        }

        if (question("Warning: This operation will take quite some time. Do you wish to proceed to scan $dir (Y/N)? ", "Y")) {
            val time = measureTimeMillis {
                dir.iterate(filters = arrayOf(
                        FileFilter { file -> !file.name.startsWith(".") },
                        FileFilter { file -> file.isDirectory || (file.isFile && file.extension == "wad" && !file.name.contains(".backup")) }
                )).forEach { wad ->
                    if (question("WAD Found ($wad). Would you like to add this to the internal registry (Y/N)? ", "Y"))
                        SpiralModel.wads.add(wad)
                }
            }
            println("Took $time ms.")
            if (SpiralModel.wads.isEmpty())
                errPrintln("Error: No WAD files detected! You can manually add them via the register command, or by running the locate command!")
            else
                println("WADs: ${SpiralModel.wads.joinToPrefixedString("", "\n\t")}")
        }
    }
    val register = Command("register") { (operation) ->
        if (operation.size == 1) {
            errPrintln("Error: No file provided!")
            return@Command
        }

        val wad = File(operation.copyFrom(1).joinToString(" "))
        if (!wad.exists()) {
            errPrintln("Error: $wad does not exist!")
            return@Command
        }

        if (!wad.isFile) {
            errPrintln("Error: $wad is not a file!")
            return@Command
        }

        if (wad.extension != "wad") {
            errPrintln("Error: $wad is not a .wad file!")
            return@Command
        }

        SpiralModel.wads.add(wad)
        println("Registered $wad!")
    }
    val registered = Command("registered") { println("Registered WADs: ${SpiralModel.wads.joinToPrefixedString("", "\n\t")}") }
    val formats = Command("formats") { println(formatTable) }

    val identify = Command("identify") { (params) ->
        if (params.size == 1)
            errPrintln("Error: No file or directory provided")

        val files = params.map { File(it) }

        files.forEach { file ->
            if (file.isFile) {

            } else if (file.isDirectory) {
                val rows = ArrayList<Array<String>>()
                file.iterate(filters = ignoreFilters).forEach dirIteration@ { subfile ->
                    val format = SpiralFormats.formatForExtension(subfile.extension) ?: SpiralFormats.formatForData(FileDataSource(subfile), identifyFormats)
                    if (format == null)
                        rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "No Identifiable Format"))
                    else
                        rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), format.name))
                }

                println(FlipTable.of(arrayOf("File", "Format"), rows.toTypedArray()))
            }
        }
    }
    val identifyAndConvert = Command("identify_and_convert") { (params) ->
        if (params.size == 1)
            return@Command errPrintln("Error: No file or directory provided")

        val file = File(params[1])
        val convertTo: SpiralFormat? = if (params.size == 2) null else SpiralFormats.formatForName(params[2]) ?: SpiralFormats.formatForExtension(params[2])

        val rows = ArrayList<Array<String>>()
        if (file.isFile) {
            val format = SpiralFormats.formatForExtension(file.extension) ?: SpiralFormats.formatForData(FileDataSource(file), identifyFormats)
            if (format == null)
                rows.add(arrayOf(file.path, "N/a", "No Identifiable Format", "N/a"))
            else {
                if (convertTo == null) {
                    if (format.conversions.isEmpty())
                        rows.add(arrayOf(file.path, "N/a", format.name, "No Convertable Formats"))
                    else {
                        val tmpConvertTo = format.conversions.first()
                        val output = File(file.absolutePath.replace(".${format.extension ?: file.extension}", "") + ".${tmpConvertTo.extension ?: "unk"}").ensureUnique()

                        try {
                            FileOutputStream(output).use { out -> format.convert(tmpConvertTo, FileDataSource(file), out, emptyMap()) }
                            rows.add(arrayOf(file.path, output.path, format.name, tmpConvertTo.name))
                        } catch (iea: IllegalArgumentException) {
                            rows.add(arrayOf(file.path, "N/a", format.name, "Could not convert to ${tmpConvertTo.name}: ${iea.localizedMessage}"))
                        } finally {
                            if (output.length() == 0L)
                                output.delete()
                        }
                    }
                } else {
                    if (format.canConvert(convertTo)) {
                        val output = File(file.absolutePath.replace(".${format.extension ?: file.extension}", "") + ".${convertTo.extension ?: "unk"}").ensureUnique()

                        try {
                            FileOutputStream(output).use { out -> format.convert(convertTo, FileDataSource(file), out, emptyMap()) }
                            rows.add(arrayOf(file.path, output.path, format.name, convertTo.name))
                        } catch (iea: IllegalArgumentException) {
                            rows.add(arrayOf(file.path, "N/a", format.name, "Could not convert to ${convertTo.name}: ${iea.localizedMessage}"))
                        } finally {
                            if (output.length() == 0L)
                                output.delete()
                        }
                    } else
                        rows.add(arrayOf(file.path, "N/a", "${format.name} cannot be converted to ${convertTo.name}", "N/a"))
                }
            }
        } else if (file.isDirectory) {
            file.iterate(filters = ignoreFilters).forEach dirIteration@ { subfile ->
                val format = SpiralFormats.formatForExtension(subfile.extension) ?: SpiralFormats.formatForData(FileDataSource(subfile), identifyFormats)
                if (format == null)
                    rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "N/a", "No Identifiable Format", "N/a"))
                else {
                    if (convertTo == null) {
                        if (format.conversions.isEmpty())
                            rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "N/a", format.name, "No Convertable Formats"))
                        else {
                            val tmpConvertTo = format.conversions.first()
                            val output = File(subfile.absolutePath.replace(".${format.extension ?: subfile.extension}", "") + ".${tmpConvertTo.extension ?: "unk"}").run {
                                if (exists())
                                    return@run File(this.absolutePath.replaceLast(".${tmpConvertTo.extension ?: "unk"}", "-${UUID.randomUUID()}.${tmpConvertTo.extension ?: "unk"}"))
                                return@run this
                            }
                            try {
                                FileOutputStream(output).use { out -> format.convert(tmpConvertTo, FileDataSource(subfile), out, emptyMap()) }
                                rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), file.name + output.absolutePath.replace(file.absolutePath, ""), format.name, tmpConvertTo.name))
                            } catch (iea: IllegalArgumentException) {
                                rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "N/a", format.name, "Could not convert to ${tmpConvertTo.name}: ${iea.localizedMessage}"))
                            } finally {
                                if (output.length() == 0L)
                                    output.delete()
                            }
                        }
                    } else {
                        if (format.canConvert(convertTo)) {
                            val output = File(subfile.absolutePath.replace(".${format.extension ?: subfile.extension}", "") + ".${convertTo.extension ?: "unk"}").run {
                                if (exists())
                                    return@run File(this.absolutePath.replaceLast(".${convertTo.extension ?: "unk"}", "-${UUID.randomUUID()}.${convertTo.extension ?: "unk"}"))
                                return@run this
                            }

                            try {
                                FileOutputStream(output).use { out -> format.convert(convertTo, FileDataSource(subfile), out, emptyMap()) }
                                rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), file.name + output.absolutePath.replace(file.absolutePath, ""), format.name, convertTo.name))
                            } catch (iea: IllegalArgumentException) {
                                rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "N/a", format.name, "Could not convert to ${convertTo.name}: ${iea.localizedMessage}"))
                            } finally {
                                if (output.length() == 0L)
                                    output.delete()
                            }
                        } else
                            rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "N/a", "${format.name} cannot be converted to ${convertTo.name}", "N/a"))
                    }
                }
            }
        }
        println(FlipTable.of(arrayOf("File", "Output File", "Old Format", "New Format"), rows.toTypedArray()))
    }
    val convert = Command("convert") { (params, str) ->
        if (params.size == 1)
            return@Command errPrintln("Error: No file or directory provided")

        if (params.size == 3)
            return@Command identifyAndConvert.command(InstanceOrder("Redirected", null, str))

        val file = File(params[1])
        val convertFrom: SpiralFormat = if (params.size == 2) return@Command errPrintln("Error: No format to convert from provided") else SpiralFormats.formatForName(params[2]) ?: SpiralFormats.formatForExtension(params[2]) ?: return@Command errPrintln("Error: No format known by name or extension ${params[2]}")
        val convertTo: SpiralFormat = SpiralFormats.formatForName(params[3]) ?: SpiralFormats.formatForExtension(params[3]) ?: return@Command errPrintln("Error: No format known by name or extension ${params[2]}")

        val rows = ArrayList<Array<String>>()
        if (file.isFile) {
            val data = FileDataSource(file)
            if (!convertFrom.isFormat(data))
                rows.add(arrayOf(file.path, "N/a", "File is not of type ${convertFrom.name}", "N/a"))
            else {
                if (convertFrom.canConvert(convertTo)) {
                    val output = File(file.absolutePath.replace(".${convertFrom.extension ?: file.extension}", "") + ".${convertTo.extension ?: "unk"}").ensureUnique()

                    try {
                        FileOutputStream(output).use { out -> convertFrom.convert(convertTo, data, out, emptyMap()) }
                        rows.add(arrayOf(file.path, output.path, convertFrom.name, convertTo.name))
                    } catch (iea: IllegalArgumentException) {
                        rows.add(arrayOf(file.path, "N/a", convertFrom.name, "Could not convert to ${convertTo.name}: ${iea.localizedMessage}"))
                    } finally {
                        if (output.length() == 0L)
                            output.delete()
                    }
                } else
                    rows.add(arrayOf(file.path, "N/a", "${convertFrom.name} cannot be converted to ${convertTo.name}", "N/a"))
            }
        } else if (file.isDirectory) {
            file.iterate(filters = ignoreFilters).forEach dirIteration@ { subfile ->
                val data = FileDataSource(subfile)
                if (!convertFrom.isFormat(data))
                    rows.add(arrayOf(file.path, "N/a", "File is not of type ${convertFrom.name}", "N/a"))
                else {
                    if (convertFrom.canConvert(convertTo)) {
                        val output = File(subfile.absolutePath.replace(".${convertFrom.extension ?: subfile.extension}", "") + ".${convertTo.extension ?: "unk"}").ensureUnique()

                        try {
                            FileOutputStream(output).use { out -> convertFrom.convert(convertTo, data, out, emptyMap()) }
                            rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), file.name + output.absolutePath.replace(file.absolutePath, ""), convertFrom.name, convertTo.name))
                        } catch (iea: IllegalArgumentException) {
                            rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "N/a", convertFrom.name, "Could not convert to ${convertTo.name}: ${iea.localizedMessage}"))
                        } finally {
                            if (output.length() == 0L)
                                output.delete()
                        }
                    } else
                        rows.add(arrayOf(file.name + subfile.absolutePath.replace(file.absolutePath, ""), "N/a", "${convertFrom.name} cannot be converted to ${convertTo.name}", "N/a"))
                }
            }
        }
        println(FlipTable.of(arrayOf("File", "Output File", "Old Format", "New Format"), rows.toTypedArray()))
    }

    val join = Command("join") { (params) ->
        if (!MediaWrapper.ffmpeg.isInstalled)
            return@Command errPrintln("Error: ffmpeg is not installed")

        if (params.size == 1)
            return@Command errPrintln("Error: No file or directory provided")

        if (params.size == 2) {
            val directory = File(params[1])

            if (!directory.exists())
                return@Command errPrintln("Error: Directory does not exist")
            else if (!directory.isDirectory)
                return@Command errPrintln("Error: Provided directory was not, in fact, a directory")

            val entries = ArrayList<Array<String>>()

            val files = directory.listFiles().filter { file -> ignoreFilters.all { filter -> filter.accept(file) } }
            files.map { it.nameWithoutExtension }.group().values.sortedBy { it.firstOrNull() ?: "" }.forEach { names ->
                if (names.size < 2) {
                    entries.add(arrayOf(names.firstOrNull() ?: "None", "", "", "", " < 2 files for provided name"))
                    return@forEach
                }
                val name = names.first()
                val audio = files.filter { it.nameWithoutExtension == name }.firstOrNull { file ->
                    (SpiralFormats.formatForExtension(file.extension, SpiralFormats.audioFormats) ?: SpiralFormats.formatForData(FileDataSource(file), SpiralFormats.audioFormats)) != null
                } ?: run {
                    entries.add(arrayOf(name, "", "", "", "No audio file for provided name"))
                    return@forEach
                }

                val video = files.filter { it.nameWithoutExtension == name }.firstOrNull { file ->
                    (SpiralFormats.formatForExtension(file.extension, SpiralFormats.videoFormats) ?: SpiralFormats.formatForData(FileDataSource(file), SpiralFormats.videoFormats)) != null
                } ?: run {
                    entries.add(arrayOf(name, audio.name, "", "", "No video file for provided name"))
                    return@forEach
                }

                debug("Joining ${audio.name} and ${video.name}")

                val output = File(directory, "$name.mp4")

                try {
                    MediaWrapper.ffmpeg.join(audio, video, output)
                } finally {
                    if (output.exists()) {
                        if (output.length() > 16) {
                            if (MP4Format.isFormat(FileDataSource(output)))
                                entries.add(arrayOf(name, audio.name, video.name, output.name, ""))
                            else
                                entries.add(arrayOf(name, audio.name, video.name, output.name, "Output is not an MP4 file"))
                        } else {
                            output.delete()
                            entries.add(arrayOf(name, audio.name, video.name, "", "Output was empty"))
                        }
                    } else
                        entries.add(arrayOf(name, audio.name, video.name, "", "Output does not exist!"))
                }
            }

            println(FlipTable.of(arrayOf("Name", "Audio File", "Video File", "Output File", "Error"), entries.toTypedArray()))
        }
    }

    val toggleDebug = Command("toggle_debug") { isDebug = !isDebug; println("Debug status is now $isDebug") }
    val exit = Command("exit", "default") { println("Bye!"); keepLooping = false }
}