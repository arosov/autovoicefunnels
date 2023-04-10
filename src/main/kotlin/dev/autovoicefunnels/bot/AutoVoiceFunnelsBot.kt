package dev.autovoicefunnels.bot

import dev.autovoicefunnels.AutoVoiceFunnels.BuildConfig.DISCORD_TOKEN
import dev.autovoicefunnels.bot.state.*
import dev.autovoicefunnels.bot.state.reset
import dev.autovoicefunnels.exceptionHandler
import dev.autovoicefunnels.models.EntryChannelsGroup
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.CategoryBehavior
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.guild.GuildDeleteEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger { }

data class EntryChannel(val id: Snowflake, val idCategory: Snowflake, val funnelEntryChannelName: String)
data class TempChannel(val id: Snowflake, val idCategory: Snowflake, val funnelEntryChannelName: String)
data class TransitChannel(val id: Snowflake, val idCategory: Snowflake, val funnelEntryChannelName: String)
data class TempChannelCategory(val id: Snowflake, val funnelEntryChannelName: String)
data class TransitCategory(val id: Snowflake, val funnelEntryChannelName: String)

internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

class AutoVoiceFunnelsBot(internal val funnelsGroups: List<EntryChannelsGroup>) {
    internal lateinit var bot: Kord

    private val blacklistCommandName = "blacklist"
    private val blacklistRemoveCommandName = "blacklistremove"
    private val blacklistStatusCommandName = "blackliststatus"
    internal val blacklist = mutableMapOf<Long, MutableList<Long>>()

    internal val entryChannels = mutableListOf<EntryChannel>()
    internal val tempChannels = mutableListOf<TempChannel>()
    internal val tempChannelCategories = mutableListOf<TempChannelCategory>()
    internal val transitChannels = mutableListOf<TransitChannel>()
    internal val transitCategories = mutableListOf<TransitCategory>()

    internal val transitJobs = mutableMapOf<Snowflake, Job>()

    // Only use in dev context
    private val DEV_CLEANUP = false
    internal lateinit var autoVoiceFunnelsState: SnowflakesState

    suspend fun start(isCleanup: Boolean) {
        bot = Kord(DISCORD_TOKEN)
        autoVoiceFunnelsState = readSnowflakesState()
        val savedDslState = readDslState()
        val hasDslChanged = savedDslState.entryChannelGroups != funnelsGroups
        if (hasDslChanged) {
            savedDslState.entryChannelGroups = funnelsGroups
            savedDslState.writeDslState()
        }
        bot.on<ReadyEvent> {
            logger.debug { "Ready: DSL changed $hasDslChanged" }
            if (DEV_CLEANUP) cleanupOnStartDevMode(this)
            if (hasDslChanged || isCleanup) {
                guilds.forEach {
                    cleanPreviouslyCreatedIds(it.asGuild())
                }
                autoVoiceFunnelsState.reset()
                if (isCleanup) {
                    autoVoiceFunnelsState.deleteFile()
                    savedDslState.deleteFile()
                    exitProcess(1)
                }
            }
            guilds.forEach {
                // noop for guilds that are already known
                // initialization for guilds added while bot was down
                createEntryChannelsAndCategories(it.asGuild())
            }
        }
        bot.on<GuildCreateEvent> {
            createEntryChannelsAndCategories(this.guild)
        }
        bot.on<GuildDeleteEvent> {
            guild?.let {
                dropStateForGuild(it)
            }
        }
        bot.on<VoiceStateUpdateEvent> {
            logger.debug { "VoiceStateUpdateEvent $this" }
            autoManageUserInEntryChannel(this)
            autoMoveFromTransitChannels(this)
            tempChannelCleanup(this)
            logger.debug { "entrychannels $entryChannels" }
            logger.debug { "tempChannels $tempChannels" }
            logger.debug { "transitChannels $transitChannels" }
            logger.debug { "categorytempChannels $tempChannelCategories" }
            logger.debug { "funnelsGroups $funnelsGroups" }
        }
        //bot.on<GuildUserCommandInteractionCreateEvent> {
        //    when (interaction.invokedCommandName) {
        //        blacklistCommandName -> blacklistCommand(this)
        //        blacklistRemoveCommandName -> blacklistRemoveCommand(this)
        //        blacklistStatusCommandName -> blacklistStatus(this)
        //    }
        //}
        //bot.guilds.toList().forEach {
        //    bot.createGuildUserCommand(it.id, blacklistCommandName)
        //    bot.createGuildUserCommand(it.id, blacklistRemoveCommandName)
        //    bot.createGuildUserCommand(it.id, blacklistStatusCommandName)
        //}
        bot.login()
    }

    private suspend fun dropStateForGuild(guild: Guild) {
        autoVoiceFunnelsState.guilds.find { (guildId,_) -> guild.id == guildId }?.let {
            autoVoiceFunnelsState.guilds.remove(it)
        }
        autoVoiceFunnelsState.write()
    }

    private suspend fun cleanPreviouslyCreatedIds(guild: Guild) {
        autoVoiceFunnelsState.guilds.find { it.guildId == guild.id }?.let { (guildId, categories) ->
            categories.forEach { (name, categoryId, channels) ->
                // Delete known channels
                channels.forEach { channel ->
                    guild.channels.firstOrNull { channel.channelId == it.id }?.delete()
                }
                guild.channels.firstOrNull { it.id == categoryId }?.let { topGuildChannel ->
                    val category = topGuildChannel as Category
                    // make sure we also delete untracked channels in known categories
                    category.channels.toList().forEach {
                        it.delete()
                    }
                    // delete categories
                    category.delete()
                }
            }
        }
    }

    private suspend fun cleanupOnStartDevMode(event: ReadyEvent) {
        event.guilds.forEach { guild ->
            // Cleanup for dev
            deleteEverythingVocalButGeneral(guild)
            logger.debug { "Cleanup done" }
        }
    }

    private fun CategoryBehavior.isTransitOrOutputCategory(): Boolean {
        val toCheck = transitCategories.map { (id) -> id } + tempChannelCategories.map { (id) -> id }
        return toCheck.any { idCheck -> idCheck == id }
    }

    private suspend fun tempChannelCleanup(voiceStateUpdateEvent: VoiceStateUpdateEvent) {
        val voiceChannelId = voiceStateUpdateEvent.old?.channelId
        voiceChannelId?.let {
            val voiceChannel = voiceStateUpdateEvent.state.getGuild().getChannel(voiceChannelId) as VoiceChannel
            logger.debug { "cleanup got voicechan $voiceChannel" }
            val tempChannel = tempChannels.find { it.id == voiceChannelId }
            tempChannel?.let {
                if (voiceChannel.voiceStates.toList().isEmpty()) {
                    tempChannels.remove(tempChannel)
                }
            }
            val transitChannel = transitChannels.find { it.id == voiceChannelId }
            transitChannel?.let {
                if (voiceChannel.voiceStates.toList().isEmpty()) {
                    transitChannels.remove(transitChannel)
                }
            }
            if (voiceChannel.category?.isTransitOrOutputCategory() == true && voiceChannel.voiceStates.toList()
                    .isEmpty()
            ) {
                voiceChannel.delete()
            }
        }
    }

    private suspend fun deleteEverythingVocalButGeneral(guild: GuildBehavior) {
        guild.channels.filterIsInstance<VoiceChannel>().filterNot { it.name.contains("General") }.toList()
            .forEach { voiceChannel ->
                voiceChannel.delete()
            }
        guild.channels.filterIsInstance<Category>().toList().forEach { category ->
            val isEmpty = category.channels.toList().all {
                it is VoiceChannel && it.voiceStates.toList().isEmpty() && it.name != "General"
            }
            if (isEmpty) category.delete()
        }
    }

    internal suspend fun deleteVocalsUnderCategory(guild: GuildBehavior, snowflake: Snowflake) {
        val categoryKord = guild.channels.filterIsInstance<Category>().firstOrNull { it.id == snowflake }
        categoryKord?.channels?.toList()?.forEach {
            it.delete()
        }
    }
}