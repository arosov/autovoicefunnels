package dev.autovoicefunnels.bot.state

import dev.autovoicefunnels.models.EntryChannelsGroup
import dev.kord.common.entity.Snowflake
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

private val configPath = "dslstate.json"
private val logger = KotlinLogging.logger {}
private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Volatile
private lateinit var currentConfig: DslState
private val reentrantReadWriteLockConfigFile = ReentrantReadWriteLock()


@Serializable
data class DslState(
    var entryChannelGroups: List<EntryChannelsGroup> = mutableListOf()
)

suspend fun readDslState(path: String = configPath): DslState {
    return withContext(Dispatchers.IO) {
        reentrantReadWriteLockConfigFile.readLock().withLock<DslState> {
            currentConfig = when (!File(path).exists()) {
                true -> DslState()
                false -> prettyJson.decodeFromString<DslState>(File(path).readText())
            }
            currentConfig
        }
    }
}

suspend fun DslState.writeDslState(path: String = configPath) {
    val conf = this
    withContext(Dispatchers.IO) {
        reentrantReadWriteLockConfigFile.writeLock().withLock {
            val json = prettyJson.encodeToString(conf)
            val file = File(path)
            file.printWriter().use { out -> out.print(json) }
            currentConfig = conf
        }
    }
}
internal fun DslState.deleteFile() {
    File(configPath).delete()
}
