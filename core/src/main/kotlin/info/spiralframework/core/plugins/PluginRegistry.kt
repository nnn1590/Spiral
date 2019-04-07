package info.spiralframework.core.plugins

import info.spiralframework.base.config.SpiralConfig
import info.spiralframework.base.util.ensureDirectoryExists
import info.spiralframework.core.SpiralSerialisation
import info.spiralframework.core.plugins.events.BeginPluginDiscoveryEvent
import info.spiralframework.core.plugins.events.DiscoveredPluginEvent
import info.spiralframework.core.plugins.events.EndPluginDiscoveryEvent
import info.spiralframework.core.postCancellable
import info.spiralframework.core.tryReadValue
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.util.*
import java.util.zip.ZipFile
import kotlin.reflect.full.createInstance

object PluginRegistry {
    interface PojoProvider {
        val pojo: SpiralPluginDefinitionPojo
    }

    val pojoServiceLoader = ServiceLoader.load(PojoProvider::class.java)

    private val mutableLoadedPlugins: MutableList<ISpiralPlugin> = ArrayList()
    val loadedPlugins: List<ISpiralPlugin>
        get() = mutableLoadedPlugins

    private val pluginLoaders: MutableMap<String, URLClassLoader> = HashMap()

    fun discover(): List<PluginEntry> {
        if (BeginPluginDiscoveryEvent().also(EventBus.getDefault()::post).isCanceled)
            return emptyList()

        //We scan five locations
        //1. Local working folder called 'plugins'
        //2. Folder where Spiral is called 'plugins'
        //3. Plugin storage folder as defined by our project directory thing
        //4. (Optionally) a user defined path
        //5. ServiceLoader

        val ourFile = File(PluginRegistry::class.java.protectionDomain.codeSource.location.path).absoluteFile

        val localWorkingPlugins = File("plugins")
                .ensureDirectoryExists()
                .walk()
                .discoverPlugins()
        val localSpiralPlugins = File(ourFile.parentFile, "plugins")
                .ensureDirectoryExists()
                .walk()
                .discoverPlugins()
        val storagePlugins = File(SpiralConfig.projectDirectories.dataLocalDir, "plugins")
                .ensureDirectoryExists()
                .walk()
                .discoverPlugins()

        val classpathPlugins = pojoServiceLoader.asIterable().map(PojoProvider::pojo)
                .map { pojo -> PluginEntry(pojo, null) }

        val plugins = ArrayList<PluginEntry>().apply {
            addAll(localWorkingPlugins)
            addAll(localSpiralPlugins)
            addAll(storagePlugins)
            addAll(classpathPlugins)
        }.sortedBy { entry -> entry.pojo.semanticVersion }
                .asReversed()
                .distinctBy { entry -> entry.pojo.uid }
                .filter { entry -> EventBus.getDefault().postCancellable(DiscoveredPluginEvent(entry)) }

        EventBus.getDefault().post(EndPluginDiscoveryEvent())

        return plugins
    }

    fun Sequence<File>.discoverPlugins(): List<PluginEntry> = this.flatMap { file ->
        try {
            val zip = ZipFile(file)
            zip.entries().asSequence().filter { entry -> entry.name.endsWith("plugin.yaml") || entry.name.endsWith("plugin.yml") || entry.name.endsWith("plugin.json')") }
                    .mapNotNull { entry ->
                        if (entry.name.endsWith("json"))
                            zip.getInputStream(entry).use { stream -> SpiralSerialisation.JSON_MAPPER.tryReadValue<SpiralPluginDefinitionPojo>(stream) }
                        else
                            zip.getInputStream(entry).use { stream -> SpiralSerialisation.YAML_MAPPER.tryReadValue<SpiralPluginDefinitionPojo>(stream) }
                    }
                    .map { pojo -> PluginEntry(pojo, file.toURI().toURL()) }
        } catch (io: IOException) {
            return@flatMap emptySequence<PluginEntry>()
        }
    }.toList()

    //TODO: Don't use an int return value
    fun loadPlugin(pluginEntry: PluginEntry): Int {
        //First thing's first, check to make sure the plugin hasn't already been loaded

        if (loadedPlugins.any { plugin -> plugin.uid == pluginEntry.pojo.uid }) {
            val sameUID = loadedPlugins.first { plugin -> plugin.uid == pluginEntry.pojo.uid }
            //Same UID, uho
            //Check if we're trying to load a different version

            if (sameUID.version == pluginEntry.pojo.semanticVersion) {
                return -1
            } else {
                //Unload existing plugin
                unloadPlugin(sameUID)
            }
        }

        if (pluginEntry.pojo.uid in pluginLoaders) {
            return -2
        }

        //TODO: Permission checks
        val result = loadPluginInternal(pluginEntry)

        if (result < 0) {
            unloadPluginInternal(pluginEntry.pojo.uid)
        }

        return result
    }

    private fun loadPluginInternal(pluginEntry: PluginEntry): Int {
        val classLoader: ClassLoader
        if (pluginEntry.source != null && pluginLoaders.values.none { loader -> loader.urLs.any { url -> url == pluginEntry.source } }) {
            classLoader = URLClassLoader.newInstance(arrayOf(pluginEntry.source))
            pluginLoaders[pluginEntry.pojo.uid] = classLoader
        } else {
            classLoader = this::class.java.classLoader
        }

        val pluginKlass = classLoader.loadClass(pluginEntry.pojo.pluginClass).kotlin
        val plugin = (pluginKlass.objectInstance
                ?: runCatching { pluginKlass.createInstance() }.getOrDefault(null)
                ?: return -3) as? ISpiralPlugin ?: return -4

        mutableLoadedPlugins.add(plugin)
        plugin.load()

        return 0
    }

    fun unloadPlugin(plugin: ISpiralPlugin) {
        plugin.unload()
        mutableLoadedPlugins.remove(plugin)
        unloadPluginInternal(plugin.uid)
    }

    private fun unloadPluginInternal(uid: String) {
        pluginLoaders.remove(uid)?.close()
    }

    init {
        mutableLoadedPlugins.add(SpiralCorePlugin)
    }
}