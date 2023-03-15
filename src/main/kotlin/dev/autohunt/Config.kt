package dev.autohunt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

private val configPath = "config.json"
private val logger = KotlinLogging.logger {}
private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Volatile
private lateinit var currentConfig: ConfigData
private val reentrantReadWriteLockConfigFile = ReentrantReadWriteLock()

@Serializable
data class ConfigData(
    var notReallyused: Boolean? = false,
)

suspend fun readConfig(path: String = configPath): ConfigData {
    return withContext(Dispatchers.IO) {
        reentrantReadWriteLockConfigFile.readLock().withLock<ConfigData> {
            currentConfig = when (!File(path).exists()) {
                true -> ConfigData()
                false -> prettyJson.decodeFromString<ConfigData>(File(path).readText())
            }
            currentConfig
        }
    }
}

suspend fun writeConfig(conf: ConfigData, path: String = configPath) {
    withContext(Dispatchers.IO) {
        reentrantReadWriteLockConfigFile.writeLock().withLock {
            val json = prettyJson.encodeToString(conf)
            val file = File(path)
            file.printWriter().use { out -> out.print(json) }
            currentConfig = conf
        }
    }
}
