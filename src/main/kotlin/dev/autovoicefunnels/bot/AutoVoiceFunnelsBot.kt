package dev.autovoicefunnels.bot

import dev.autovoicefunnels.AutoVoiceFunnels.BuildConfig.DISCORD_TOKEN
import dev.autovoicefunnels.ConfigData
import dev.autovoicefunnels.exceptionHandler
import dev.autovoicefunnels.models.EntryChannelsGroup
import dev.autovoicefunnels.readConfig
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

data class EntryChannel(val id: Snowflake, val idCategory: Snowflake, val funnelEntryChannelName: String)
data class TempChannel(val id: Snowflake, val idCategory: Snowflake, val funnelEntryChannelName: String)
data class TempChannelCategory(val id: Snowflake, val funnelEntryChannelName: String)
data class TransitChannel(val id: Snowflake, val idCategory: Snowflake, val funnelEntryChannelName: String)
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

    private val DEV_CLEANUP = true
    internal lateinit var autoVoiceFunnelsState: ConfigData

    suspend fun start() {
        bot = Kord(DISCORD_TOKEN)
        autoVoiceFunnelsState = readConfig()
        bot.on<ReadyEvent> {
            logger.debug { "Ready" }
            if (DEV_CLEANUP) cleanupOnStartDevMode(this)
            createEntryChannelsAndCategories(this)
        }
        bot.on<VoiceStateUpdateEvent> {
            autoManageUserInEntryChannel(this)
            tempChannelCleanup(this)
            autoMoveFromTransitChannels(this)
            logger.debug { "entrychannels $tempChannels" }
            logger.debug { "tempChannels $tempChannels" }
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

    private suspend fun cleanupOnStartDevMode(event: ReadyEvent) {
        event.guilds.forEach { guild ->
            // Cleanup for dev
            deleteEverythingVocalButGeneral(guild)
            logger.debug { "Cleanup done" }
        }
    }

    private suspend fun tempChannelCleanup(voiceStateUpdateEvent: VoiceStateUpdateEvent) {
        val voiceChannelId = voiceStateUpdateEvent.old?.channelId
        voiceChannelId?.let {
            val voiceChannel = voiceStateUpdateEvent.state.getGuild().getChannel(voiceChannelId) as VoiceChannel
            logger.debug { "cleanup got voicechan $voiceChannel" }
            val tempChannel = tempChannels.find { it.id == voiceChannelId }
            tempChannel?.let {
                if (voiceChannel.voiceStates.toList().isEmpty()) {
                    voiceChannel.delete()
                    tempChannels.remove(tempChannel)
                }
            }
            val transitChannel = transitChannels.find { it.id == voiceChannelId }
            transitChannel?.let {
                if (voiceChannel.voiceStates.toList().isEmpty()) {
                    voiceChannel.delete()
                    transitChannels.remove(transitChannel)
                }
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
        val categoryKord = guild.channels.filterIsInstance<Category>().firstOrNull { it.id == snowflake}
        categoryKord?.channels?.toList()?.forEach {
            it.delete()
        }
    }
}