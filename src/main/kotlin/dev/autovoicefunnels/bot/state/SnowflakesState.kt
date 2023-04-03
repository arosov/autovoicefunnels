package dev.autovoicefunnels.bot.state

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

private val configPath = "snowflakesstate.json"
private val logger = KotlinLogging.logger {}
private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Volatile
private lateinit var currentConfig: SnowflakesState
private val reentrantReadWriteLockConfigFile = ReentrantReadWriteLock()

@Serializable
data class GuildData(val guildId: Snowflake, val categories: MutableList<CategoryData> = mutableListOf())
@Serializable
data class CategoryData(
    val categoryDslName: String,
    val categoryId: Snowflake,
    val channels: MutableList<ChannelData> = mutableListOf()
)

@Serializable
data class ChannelData(val channelDslName: String, val channelId: Snowflake)

@Serializable
data class SnowflakesState(
    val guilds: MutableList<GuildData> = mutableListOf()
)

suspend fun readSnowflakesState(path: String = configPath): SnowflakesState {
    return withContext(Dispatchers.IO) {
        reentrantReadWriteLockConfigFile.readLock().withLock<SnowflakesState> {
            currentConfig = when (!File(path).exists()) {
                true -> SnowflakesState()
                false -> prettyJson.decodeFromString<SnowflakesState>(File(path).readText())
            }
            currentConfig
        }
    }
}

suspend fun SnowflakesState.write(path: String = configPath) {
    val config = this
    withContext(Dispatchers.IO) {
        reentrantReadWriteLockConfigFile.writeLock().withLock {
            val json = prettyJson.encodeToString(config)
            val file = File(path)
            file.printWriter().use { out -> out.print(json) }
            currentConfig = config
        }
    }
}

internal fun SnowflakesState.addGuildToState(guildId: Snowflake) {
    guilds.find { guildData -> guildData.guildId == guildId }?: kotlin.run {
        guilds.add(GuildData(guildId))
    }
}

internal fun SnowflakesState.addCategoryToState(
    guildId: Snowflake, resourceName: String, resourceId: Snowflake
) {
    addGuildToState(guildId)
    guilds.find { it.guildId == guildId }?.let { targetGuild ->
        val categoryData = CategoryData(resourceName, resourceId)
        if (!targetGuild.categories.contains(categoryData))
            targetGuild.categories.add(categoryData)
    }
}

internal fun SnowflakesState.addChannelToState(
    guildId: Snowflake, resourceName: String, resourceId: Snowflake, categoryId: Snowflake
) {
    addGuildToState(guildId)
    guilds.find { it.guildId == guildId }?.let { targetGuild ->
        targetGuild.categories.find { it.categoryId == categoryId }?.let { targetCategory ->
            val channelData = ChannelData(resourceName, resourceId)
            if (!targetCategory.channels.contains(channelData))
                targetCategory.channels.add(channelData)
        }
    }
}

internal fun SnowflakesState.reset() {
    guilds.removeAll(guilds)
}

internal fun SnowflakesState.deleteFile() {
    File(configPath).delete()
}

