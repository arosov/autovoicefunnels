package dev.autohunt.bot

import dev.autohunt.AutoHunt.BuildConfig.DISCORD_TOKEN
import dev.autohunt.frakturedStates
import dev.autohunt.models.*
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.*
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.GuildUserCommandInteractionCreateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import kotlinx.coroutines.flow.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

data class EntryChannel(val id: Snowflake, val idCategory: Snowflake, val funnelId: String)
data class TempChannel(val id: Snowflake, val idCategory: Snowflake, val funnelId: String)
data class TempChannelCategory(val id: Snowflake, val funnelId: String)

class AutoHuntBot(private val funnelsGroups: List<EntryChannelsGroup>) {
    // old stuff below
    private lateinit var bot: Kord

    private val blacklistCommandName = "blacklist"
    private val blacklistRemoveCommandName = "blacklistremove"
    private val blacklistStatusCommandName = "blackliststatus"
    private val blacklist = mutableMapOf<Long, MutableList<Long>>()

    private val DEV_CLEANUP = true

    private val entryChannels = mutableListOf<EntryChannel>()
    private val tempChannels = mutableListOf<TempChannel>()
    private val categoriesTempChannels = mutableListOf<TempChannelCategory>()

    suspend fun start() {
        bot = Kord(DISCORD_TOKEN)
        bot.on<ReadyEvent> {
            logger.debug { "Ready" }
            initEntryChannels(this)
        }
        bot.on<VoiceStateUpdateEvent> {
            autoManageUserInEntryChannel(this)
            channelCleanup(this)
            logger.debug { "entrychannels $tempChannels" }
            logger.debug { "tempChannels $tempChannels" }
            logger.debug { "categorytempChannels $categoriesTempChannels" }
            logger.debug { "funnelsGroups $funnelsGroups" }
        }
        bot.on<GuildUserCommandInteractionCreateEvent> {
            when (interaction.invokedCommandName) {
                blacklistCommandName -> blacklistCommand(this)
                blacklistRemoveCommandName -> blacklistRemoveCommand(this)
                blacklistStatusCommandName -> blacklistStatus(this)
            }
        }
        bot.guilds.toList().forEach {
            bot.createGuildUserCommand(it.id, blacklistCommandName)
            bot.createGuildUserCommand(it.id, blacklistRemoveCommandName)
            bot.createGuildUserCommand(it.id, blacklistStatusCommandName)
        }
        bot.login()
    }

    private suspend fun blacklistStatus(event: GuildUserCommandInteractionCreateEvent) {
        val response = event.interaction.deferEphemeralResponse()
        val usersBlacklisted = blacklist[event.interaction.user.id.value.toLong()]
        val guild = event.interaction.guild
        val msg = if (usersBlacklisted.isNullOrEmpty()) {
            "Empty blacklist"
        } else {
            "Blacklist content\n${usersBlacklisted.map { guild.getMember(Snowflake(it)).displayName }}"
        }
        response.respond {
            content = msg
        }
    }

    private suspend fun blacklistCommand(commandEvent: GuildUserCommandInteractionCreateEvent) {
        val response = commandEvent.interaction.deferEphemeralResponse()
        val user = commandEvent.interaction.user
        logger.debug { "${user.id} ${commandEvent.interaction.targetId}" }
        val targetId = commandEvent.interaction.targetId
        val msg = when (blacklistUser(user.id.value.toLong(), targetId.value.toLong())) {
            true -> "Hunter ${commandEvent.interaction.target.asUser().username} blacklisté"
            false -> "Erreur"
        }
        response.respond {
            content = msg
        }
    }

    private suspend fun blacklistRemoveCommand(commandEvent: GuildUserCommandInteractionCreateEvent) {
        val response = commandEvent.interaction.deferEphemeralResponse()
        val user = commandEvent.interaction.user
        val targetUser = commandEvent.interaction.target.asUser()
        val msg = "Hunter ${targetUser.username} retiré de la blacklist"
        removeUserFromBlacklist(user.id.value.toLong(), targetUser.id.value.toLong())
        response.respond {
            content = msg
        }
    }

    private fun blacklistUser(blacklister: Long, blacklistee: Long): Boolean {
        if (blacklister == blacklistee || bot.selfId.value.toLong() == blacklistee) return false
        val userBlacklist = blacklist[blacklister]
        if (userBlacklist == null) {
            blacklist[blacklister] = mutableListOf<Long>().apply { add(blacklistee) }
        } else {
            blacklist[blacklister]?.add(blacklistee)
        }
        return true
    }

    private fun removeUserFromBlacklist(blacklister: Long, blacklistee: Long) {
        blacklist[blacklister]?.remove(blacklistee)
    }

    private suspend fun initEntryChannels(event: ReadyEvent) {
        logger.debug { "Guilds ${event.guildIds}" }
        event.guilds.forEach { guild ->
            // Manual cleanup for dev
            if (DEV_CLEANUP) {
                deleteEverything(guild)
                funnelsGroups.forEach {
                    deleteChannelsAndCategory(it.groupName, guild)
                }
                deleteChannelsAndCategory("TRIO MMR 3-4", guild)
                deleteChannelsAndCategory("TRIO MMR 4-5", guild)
                deleteChannelsAndCategory("TRIO MMR 5-6", guild)
                deleteChannelsAndCategory("Remplissage TRIO", guild)
                deleteChannelsAndCategory("Custom TRIOs", guild)
                deleteChannelsAndCategory("DUO MMR 3-4", guild)
                deleteChannelsAndCategory("DUO MMR 4-5", guild)
                deleteChannelsAndCategory("DUO MMR 5-6", guild)
                deleteChannelsAndCategory("Remplissage DUO", guild)
                deleteChannelsAndCategory("Custom DUOs", guild)
                logger.debug { "Cleanup done" }
            }
            funnelsGroups.forEach { entryCategory ->
                val category = getOrCreateCategory(guild, entryCategory.groupName)

                entryCategory.entryChannelsGroup.forEach {
                    entryChannels.add(
                        EntryChannel(
                            createEntryChannelIfNotExisting(guild, entryCategory.groupName, it.entryChannelName),
                            category.id,
                            it.id
                        )
                    )
                }
            }
        }
    }

    private fun findFunnel(funnelId: String): VoiceFunnel? {
        return funnelsGroups.flatMap {
            it.entryChannelsGroup
        }.firstOrNull {
            it.id == funnelId
        }
    }

    private suspend fun autoManageUserInEntryChannel(event: VoiceStateUpdateEvent) {
        val guild = event.state.getGuild()
        val entryChannel = entryChannels.find { event.state.channelId == it.id } ?: return
        val funnel = findFunnel(entryChannel.funnelId) ?: return
        val userId = event.state.userId.value.toLong()
        when (funnel.voiceFunnelOutput) {
            is SimpleFunnelOutput -> {
                val category = getOrCreateCategory(guild, funnel.voiceFunnelOutput.categoryName)
                if (categoriesTempChannels.find { it.funnelId == funnel.id } == null) {
                    categoriesTempChannels.add(TempChannelCategory(category.id, funnel.id))
                }
                val tempChannel = getOrCreateTemporaryChannel(guild, userId, funnel) ?: return
                event.state.getMember().edit {
                    voiceChannelId = tempChannel.id
                }
            }

            is AggregatedFunnelOutput -> {
                logger.debug { "TMP CHANNELS $tempChannels" }
                logger.debug { "funnelIds ${funnel.voiceFunnelOutput.funnelIds}"}
                tempChannels.find {
                    val chanCurrentUsers = guild.getChannelOf<VoiceChannel>(it.id).voiceStates.toList().size
                    val funnelMaxUsers = (findFunnel(it.funnelId)!!.voiceFunnelOutput as SimpleFunnelOutput).tempChannelGenerator.maxUsers
                    logger.debug { "current user max $chanCurrentUsers $funnelMaxUsers"}
                    funnel.voiceFunnelOutput.funnelIds.contains(it.funnelId) &&
                            chanCurrentUsers < funnelMaxUsers &&
                            (!funnel.disableBlacklist && channelHasNoBlacklistedUser(
                        userId, guild, it))
                }?.let { tmpChan ->
                    event.state.getMember().edit {
                        voiceChannelId = tmpChan.id
                    }
                }
            }
        }
    }

    private suspend fun channelCleanup(voiceStateUpdateEvent: VoiceStateUpdateEvent) {
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
        }
    }

    private suspend fun deleteEverything(guild: GuildBehavior) {
        guild.channels.filterIsInstance<VoiceChannel>().filterNot { it.name.contains("General") }.toList()
            .forEach { it.delete() }
    }

    private suspend fun deleteChannelsAndCategory(categoryName: String, guild: GuildBehavior) {
        val guildChannels = guild.channels.toList()
        val existingCategory = guildChannels.find { it.type == ChannelType.GuildCategory && it.name == categoryName }
        guild.channels.filterIsInstance<VoiceChannel>().filter { it.category?.id == existingCategory?.id }.toList()
            .forEach {
                it.delete("Cleanup channel")
            }
        existingCategory?.delete("Cleanup category")
    }

    private suspend fun createEntryChannelIfNotExisting(
        guild: GuildBehavior, categoryName: String, currentChannelName: String
    ): Snowflake {
        val guildChannels = guild.channels.toList()
        val category = getOrCreateCategory(guild, categoryName)
        val channel =
            guildChannels.find { it.name == currentChannelName } ?: guild.createVoiceChannel(currentChannelName) {
                parentId = category.id
                //position = positionEntry
            }
        return channel.id
    }

    private suspend fun getOrCreateCategory(
        guild: GuildBehavior, categoryName: String
    ): TopGuildChannel {
        val existingCategory =
            guild.channels.toList().find { it.type == ChannelType.GuildCategory && it.name == categoryName }
        if (existingCategory != null) {
            return existingCategory
        }

        val everyoneRole = guild.getRoleOrNull(guild.id)
        val category = guild.createCategory(categoryName) {
            //permissionOverwrites.add(
            //    Overwrite(
            //        id = everyoneRole?.id!!, type = OverwriteType.Role, allow = Permissions(), deny = Permissions(
            //            Permission.ManageChannels
            //        )
            //    )
            //)
        }
        return category
    }


    private suspend fun getOrCreateTemporaryChannel(
        guild: Guild, userId: Long, voiceFunnel: VoiceFunnel
    ): VoiceChannel? {
        val voiceFunnelSimpleOutput =
            if (voiceFunnel.voiceFunnelOutput is SimpleFunnelOutput) voiceFunnel.voiceFunnelOutput else return null
        val maxUsers = voiceFunnelSimpleOutput.tempChannelGenerator.maxUsers
        val tempChannel = if(voiceFunnel.disableFillUp) null else tempChannels.firstOrNull {
            it.funnelId == voiceFunnel.id
                    && guild.getChannelOf<VoiceChannel>(it.id).voiceStates.toList().size < maxUsers
                    && (!voiceFunnel.disableBlacklist && channelHasNoBlacklistedUser(
                userId, guild, it))
        }
        return when (tempChannel) {
            null -> {
                val tempChannelsCategory = categoriesTempChannels.find { it.funnelId == voiceFunnel.id } ?: return null
                val channelName = when (voiceFunnel.voiceFunnelOutput.tempChannelGenerator.nameStrategy) {
                    is UsernameBased -> "${guild.getMember(Snowflake(userId)).displayName} channel"
                    is RandomizedFrakturedNames -> newTempChannelFrakturedName(guild)
                    else -> {"Naming strategy problem !"}
                }
                val voiceChannel = guild.createVoiceChannel(channelName) {
                    parentId = tempChannelsCategory.id
                    userLimit = maxUsers
                }
                tempChannels.add(TempChannel(voiceChannel.id, tempChannelsCategory.id, voiceFunnel.id))
                voiceChannel
            }

            else -> guild.getChannelOf<VoiceChannel>(tempChannel.id)
        }
        // filtering on these categories
        //val categoryIdsWhitelist = mutableListOf<Snowflake>().apply {
        //    when (mmr.mmrKey) {
        //        "*" -> {
        //            categoriesTempChannels.filter { it.gameType == gameType }.forEach { tempChan ->
        //                add(tempChan.id)
        //            }
        //        }

        //        "usr" -> {
        //            // noop, also unused for now
        //        }

        //        else -> {
        //            categoriesTempChannels.filter { it.gameType == gameType && it.mmr == mmr }.forEach { tempChan ->
        //                add(tempChan.id)
        //            }
        //        }
        //    }
        //}
        //logger.debug { categoryIdsWhitelist }
        //val tempChannel = tempChannels.firstOrNull {
        //    categoryIdsWhitelist.contains(it.idCategory) && guild.getChannelOf<VoiceChannel>(it.id).voiceStates.toList().size < gameType.maxHunters && channelHasNoBlacklistedUser(
        //        userId, guild, it
        //    )
        //}
        //return when (tempChannel) {
        //    null -> {
        //        when (mmr) {
        //            MMR.STAR -> null
        //            else -> {
        //                val categoryName = "$entryChannelKeyPrefix ${mmr.mmrKey} ${gameType.name}"
        //                val tempChannelsCategory = getOrCreateCategory(guild, categoryName)
        //                if (categoriesTempChannels.find { it.id == tempChannelsCategory.id } == null) {
        //                    categoriesTempChannels.add(TempChannelCategory(tempChannelsCategory.id, mmr, gameType))
        //                }
        //                val voiceChannel = guild.createVoiceChannel(newTempChannelName(guild)) {
        //                    parentId = tempChannelsCategory.id
        //                }
        //                tempChannels.add(TempChannel(voiceChannel.id, tempChannelsCategory.id, mmr, gameType))
        //                voiceChannel
        //            }
        //        }
        //    }

        //    else -> guild.getChannelOf<VoiceChannel>(tempChannel.id)
        //}
    }

    private suspend fun channelHasNoBlacklistedUser(userId: Long, guild: Guild, tempChannel: TempChannel): Boolean {
        if (userId<0) return true
        val blacklistedFirstUserFound = guild.getChannelOf<VoiceChannel>(tempChannel.id).voiceStates.firstOrNull {
            blacklist[userId]?.contains(it.userId.value.toLong()) == true
        }
        logger.debug { "BLACKLIST DEBUG $blacklistedFirstUserFound" }
        return when (blacklistedFirstUserFound) {
            null -> true
            else -> false
        }
    }

    private suspend fun newTempChannelFrakturedName(guild: Guild): String {
        val usedNames = guild.channels.filterIsInstance<VoiceChannel>().filter {
            it.id in tempChannels.map { chan -> chan.id }
        }.map { it.name }.toSet()
        return frakturedStates.minus(usedNames).random()
    }
}