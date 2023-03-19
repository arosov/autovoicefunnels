package dev.autohunt.bot

import dev.autohunt.frakturedStates
import dev.autohunt.models.*
import dev.autohunt.writeConfig
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.exception.EntityNotFoundException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }


internal fun AutoHuntBot.findFunnel(funnelId: String): VoiceFunnel? {
    return funnelsGroups.flatMap {
        it.entryChannelsGroup
    }.firstOrNull {
        it.entryChannelName == funnelId
    }
}

internal suspend fun AutoHuntBot.autoManageUserInEntryChannel(event: VoiceStateUpdateEvent) {
    val guild = event.state.getGuild()
    val entryChannel = entryChannels.find { event.state.channelId == it.id } ?: return
    val funnel = findFunnel(entryChannel.funnelEntryChannelName) ?: return
    val userId = event.state.userId.value.toLong()
    when {
        funnel.voiceFunnelOutput is SimpleFunnelOutput && funnel.funnelTransit == null -> {
            val category = getOrCreateCategory(guild, funnel.voiceFunnelOutput.categoryName)
            if (tempChannelCategories.find { it.funnelEntryChannelName == funnel.entryChannelName } == null) {
                tempChannelCategories.add(TempChannelCategory(category.id, funnel.entryChannelName))
            }
            val tempChannel = getOrCreateTemporaryChannel(guild, userId, funnel) ?: return
            event.state.getMember().edit {
                voiceChannelId = tempChannel.id
            }
        }

        funnel.voiceFunnelOutput is SimpleFunnelOutput && funnel.funnelTransit != null -> {
            val category = getOrCreateCategory(guild, funnel.funnelTransit.transitCategoryName)
            if (transitCategories.find { it.funnelEntryChannelName == funnel.entryChannelName } == null) {
                transitCategories.add(TransitCategory(category.id, funnel.entryChannelName))
            }
            val tempChannel = getOrCreateTransitChannel(guild, userId, funnel) ?: return
            event.state.getMember().edit {
                voiceChannelId = tempChannel.id
            }

        }

        funnel.voiceFunnelOutput is AggregatedFunnelOutput -> {
            throw UnsupportedOperationException("Disabled output aggregation for now")
            logger.debug { "TMP CHANNELS $tempChannels" }
            logger.debug { "funnelIds ${funnel.voiceFunnelOutput.funnelIds}" }
            tempChannels.find {
                val chanCurrentUsers = guild.getChannelOf<VoiceChannel>(it.id).voiceStates.toList().size
                val funnelMaxUsers =
                    (findFunnel(it.funnelEntryChannelName)!!.voiceFunnelOutput as SimpleFunnelOutput).tempChannelGenerator.maxUsers
                logger.debug { "current user max $chanCurrentUsers $funnelMaxUsers" }/*funnel.voiceFunnelOutput.funnelIds.contains(it.funnelId) &&*/
                chanCurrentUsers < funnelMaxUsers && (!funnel.disableBlacklist && channelHasNoBlacklistedUser(
                    userId, guild, it
                ))
            }?.let { tmpChan ->
                event.state.getMember().edit {
                    voiceChannelId = tmpChan.id
                }
            }
        }
    }

}

internal suspend fun AutoHuntBot.autoMoveFromTransitChannels(voiceStateUpdateEvent: VoiceStateUpdateEvent) {
    val guild = voiceStateUpdateEvent.state.getGuild()
    val transitChanId = voiceStateUpdateEvent.state.channelId ?: return
    val transitChannel = transitChannels.find { voiceStateUpdateEvent.state.channelId == it.id } ?: return
    val funnel = findFunnel(transitChannel.funnelEntryChannelName) ?: return
    val funnelOutput = funnel.voiceFunnelOutput as SimpleFunnelOutput
    if (guild.getChannelOf<VoiceChannel>(transitChanId).voiceStates.toList().size < funnelOutput.tempChannelGenerator.maxUsers) return
    val delayValue = funnel.funnelTransit?.timeBeforeMoveToOutput!!
    val targetIdCategory = getOrCreateCategory(guild, funnelOutput.categoryName).id
    transitJobs[transitChanId]?.cancel()
    transitJobs[transitChanId] = coroutineScope.launch {
        logger.debug { "Start delay $delayValue seconds" }
        delay(delayValue * 1000L)
        try {
            if (guild.getChannelOf<VoiceChannel>(transitChanId).voiceStates.toList().size < funnelOutput.tempChannelGenerator.maxUsers) return@launch
        } catch (e: EntityNotFoundException) {
            logger.warn { "Transit channel $transitChanId not found after transit delay" }
        }
        guild.channels.filterIsInstance<VoiceChannel>().firstOrNull { it.id == transitChanId }?.let {
            it.edit {
                parentId = targetIdCategory
                name = newTempChannelFrakturedName(guild)
            }
            transitChannels.removeIf { it.id == transitChanId }
            tempChannels.add(
                TempChannel(
                    transitChanId, transitChannel.idCategory, transitChannel.funnelEntryChannelName
                )
            )
        }
    }
}

internal suspend fun AutoHuntBot.createEntryChannelIfNotExisting(
    guild: GuildBehavior, categoryName: String, currentChannelName: String
): Snowflake {
    val guildChannels = guild.channels.toList()
    val category = getOrCreateCategory(guild, categoryName)
    autoHuntState.snowflakeMap[guild.id]?.find { (dslname, _) -> dslname == currentChannelName }?.let {
        (_, snowflake) ->
        guildChannels.firstOrNull { it.id == snowflake }?.let {
            return snowflake
        }
    }
    val channel = guildChannels.find { it.name == currentChannelName } ?: guild.createVoiceChannel(currentChannelName) {
        parentId = category.id
        //position = positionEntry
    }
    addResourceNameToState(guild.id, channel.name, channel.id)
    return channel.id
}

internal suspend fun AutoHuntBot.addResourceNameToState(guildId: Snowflake, resourceName: String, resourceId: Snowflake) {
    val pairNameSnowflake = Pair(resourceName, resourceId)
    if (autoHuntState.snowflakeMap[guildId] == null) autoHuntState.snowflakeMap[guildId] = mutableListOf()
    if (autoHuntState.snowflakeMap[guildId]?.contains(pairNameSnowflake) == false)
        autoHuntState.snowflakeMap[guildId]?.add(pairNameSnowflake)
}

internal suspend fun AutoHuntBot.getOrCreateCategory(
    guild: GuildBehavior, categoryName: String
): TopGuildChannel {
    autoHuntState.snowflakeMap[guild.id]?.find { (dslName,_) -> dslName == categoryName }?.let {
            (_, snowflake) ->
        guild.channels.filterIsInstance<Category>().firstOrNull{ it.id == snowflake }?.let {
            return it
        }
    }

    val existingCategory =
        guild.channels.filterIsInstance<Category>().firstOrNull() { it.name == categoryName }

    if (existingCategory != null) {
        addResourceNameToState(guild.id, categoryName, existingCategory.id)
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
    addResourceNameToState(guild.id, categoryName, category.id)
    return category
}

internal suspend fun AutoHuntBot.getOrCreateTransitChannel(
    guild: Guild, userId: Long, voiceFunnel: VoiceFunnel
): VoiceChannel? {
    val funnelTransit = voiceFunnel.funnelTransit
    val voiceFunnelSimpleOutput =
        if (voiceFunnel.voiceFunnelOutput is SimpleFunnelOutput) voiceFunnel.voiceFunnelOutput else return null
    val maxUsers = voiceFunnelSimpleOutput.tempChannelGenerator.maxUsers
    val transitChannel = if (voiceFunnel.disableFillUp) null else transitChannels.firstOrNull {
        it.funnelEntryChannelName == voiceFunnel.entryChannelName && guild.getChannelOf<VoiceChannel>(it.id).voiceStates.toList().size < maxUsers/*&& (!voiceFunnel.disableBlacklist && channelHasNoBlacklistedUser(
            userId, guild, it)) */
    }
    return when (transitChannel) {
        null -> {
            val transitChannelsCategory =
                transitCategories.find { it.funnelEntryChannelName == voiceFunnel.entryChannelName } ?: return null
            val channelName = when (funnelTransit?.transitChannelNameStrategy) {
                is UsernameBased -> "${guild.getMember(Snowflake(userId)).displayName} channel"
                is RandomizedFrakturedNames -> newTempChannelFrakturedName(guild)
                is NumberedWithScheme -> newNumberedWithSchemeName(guild, funnelTransit.transitChannelNameStrategy)
                else -> {
                    "Naming strategy problem !"
                }
            }
            val voiceChannel = guild.createVoiceChannel(channelName) {
                parentId = transitChannelsCategory.id
                userLimit = maxUsers
            }
            transitChannels.add(
                TransitChannel(
                    voiceChannel.id,
                    transitChannelsCategory.id,
                    voiceFunnel.entryChannelName
                )
            )
            voiceChannel
        }

        else -> guild.getChannelOf<VoiceChannel>(transitChannel.id)
    }
}

suspend fun AutoHuntBot.newNumberedWithSchemeName(guild: Guild, transitChannelNameStrategy: NamingStrategy): String {
    val numberedWithScheme = transitChannelNameStrategy as NumberedWithScheme
    return numberedWithScheme.scheme.replace("%%", (transitChannels.size +1).toString())
}


internal suspend fun AutoHuntBot.getOrCreateTemporaryChannel(
    guild: Guild, userId: Long, voiceFunnel: VoiceFunnel
): VoiceChannel? {
    val voiceFunnelSimpleOutput =
        if (voiceFunnel.voiceFunnelOutput is SimpleFunnelOutput) voiceFunnel.voiceFunnelOutput else return null
    val maxUsers = voiceFunnelSimpleOutput.tempChannelGenerator.maxUsers
    val tempChannel = if (voiceFunnel.disableFillUp) null else tempChannels.firstOrNull {
        it.funnelEntryChannelName == voiceFunnel.entryChannelName && guild.getChannelOf<VoiceChannel>(it.id).voiceStates.toList().size < maxUsers && (!voiceFunnel.disableBlacklist && channelHasNoBlacklistedUser(
            userId, guild, it
        ))
    }
    return when (tempChannel) {
        null -> {
            val tempChannelsCategory =
                tempChannelCategories.find { it.funnelEntryChannelName == voiceFunnel.entryChannelName } ?: return null
            val channelName = when (voiceFunnel.voiceFunnelOutput.tempChannelGenerator.nameStrategy) {
                is UsernameBased -> "${guild.getMember(Snowflake(userId)).displayName} channel"
                is RandomizedFrakturedNames -> newTempChannelFrakturedName(guild)
                else -> {
                    "Naming strategy problem !"
                }
            }
            val voiceChannel = guild.createVoiceChannel(channelName) {
                parentId = tempChannelsCategory.id
                userLimit = maxUsers
            }
            tempChannels.add(TempChannel(voiceChannel.id, tempChannelsCategory.id, voiceFunnel.entryChannelName))
            voiceChannel
        }

        else -> guild.getChannelOf<VoiceChannel>(tempChannel.id)
    }
}

internal suspend fun AutoHuntBot.newTempChannelFrakturedName(guild: Guild): String {
    val usedNames = guild.channels.filterIsInstance<VoiceChannel>().filter {
        it.id in tempChannels.map { chan -> chan.id }
    }.map { it.name }.toSet()
    return frakturedStates.minus(usedNames).random()
}

internal suspend fun AutoHuntBot.createEntryChannelsAndCategories(event: ReadyEvent) {
    logger.debug { "Guilds ${event.guildIds}" }
    event.guilds.forEach { guild ->
        funnelsGroups.forEach { entryCategory ->
            logger.debug { "DSL DEBUG $entryCategory" }
            val category = getOrCreateCategory(guild, entryCategory.groupName)
            entryCategory.entryChannelsGroup.forEach { voiceFunnel ->
                entryChannels.add(
                    EntryChannel(
                        createEntryChannelIfNotExisting(
                            guild, entryCategory.groupName, voiceFunnel.entryChannelName
                        ), category.id, voiceFunnel.entryChannelName
                    )
                )
            }
        }
        funnelsGroups.forEach { entryCategory ->
            entryCategory.entryChannelsGroup.forEach { voiceFunnel ->
                voiceFunnel.funnelTransit?.let { funnelTransit ->
                    val transitCategoryKord = getOrCreateCategory(guild, funnelTransit.transitCategoryName)
                    deleteVocalsUnderCategory(guild, transitCategoryKord.id)
                    val transitCategory = TransitCategory(transitCategoryKord.id, voiceFunnel.entryChannelName)
                    if (!transitCategories.contains(transitCategory)) transitCategories.add(transitCategory)
                }
            }
        }
        funnelsGroups.forEach { entryCategory ->
            entryCategory.entryChannelsGroup.forEach { voiceFunnel ->
                val outputCategoryName = (voiceFunnel.voiceFunnelOutput as SimpleFunnelOutput).categoryName
                val outputcategoryKord = getOrCreateCategory(guild, outputCategoryName)
                deleteVocalsUnderCategory(guild, outputcategoryKord.id)
                val tempChannelCategory = TempChannelCategory(outputcategoryKord.id, voiceFunnel.entryChannelName)
                if (!tempChannelCategories.contains(tempChannelCategory)) tempChannelCategories.add(
                    tempChannelCategory
                )
            }
        }
    }
    writeConfig(autoHuntState)
}
