package dev.autovoicefunnels.bot

import dev.autovoicefunnels.frakturedChannelNames
import dev.autovoicefunnels.models.*
import dev.autovoicefunnels.writeConfig
import dev.kord.common.entity.*
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

internal fun AutoVoiceFunnelsBot.findFunnel(funnelId: String): VoiceFunnel? {
    return funnelsGroups.flatMap {
        it.entryChannelsGroup
    }.firstOrNull {
        it.entryChannelName == funnelId
    }
}

internal suspend fun AutoVoiceFunnelsBot.autoManageUserInEntryChannel(event: VoiceStateUpdateEvent) {
    val guild = event.state.getGuild()
    val entryChannel = entryChannels.find { event.state.channelId == it.id } ?: return
    val funnel = findFunnel(entryChannel.funnelEntryChannelName) ?: return
    val userId = event.state.userId.value.toLong()
    when {
        funnel.voiceFunnelOutput is SimpleFunnelOutput && funnel.funnelTransit == null -> {
            val category = getOrCreateCategory(guild, funnel.voiceFunnelOutput.categoryName, funnel.rolesVisibility, funnel.noTextForRoles, funnel.noTextNoVoiceForRoles)
            if (tempChannelCategories.find { it.funnelEntryChannelName == funnel.entryChannelName } == null) {
                tempChannelCategories.add(TempChannelCategory(category.id, funnel.entryChannelName))
            }
            val tempChannel = getOrCreateTemporaryChannel(guild, userId, funnel) ?: return
            event.state.getMember().edit {
                voiceChannelId = tempChannel.id
            }
        }

        funnel.voiceFunnelOutput is SimpleFunnelOutput && funnel.funnelTransit != null -> {
            val category = getOrCreateCategory(guild, funnel.funnelTransit.transitCategoryName, funnel.rolesVisibility, funnel.noTextForRoles, funnel.noTextNoVoiceForRoles)
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

internal suspend fun AutoVoiceFunnelsBot.autoMoveFromTransitChannels(voiceStateUpdateEvent: VoiceStateUpdateEvent) {
    val guild = voiceStateUpdateEvent.state.getGuild()
    val transitChanId = voiceStateUpdateEvent.state.channelId ?: return
    val transitChannel = transitChannels.find { voiceStateUpdateEvent.state.channelId == it.id } ?: return
    val funnel = findFunnel(transitChannel.funnelEntryChannelName) ?: return
    val funnelOutput = funnel.voiceFunnelOutput as SimpleFunnelOutput
    if (guild.getChannelOf<VoiceChannel>(transitChanId).voiceStates.toList().size < funnelOutput.tempChannelGenerator.maxUsers) {
        logger.info { "Transit move timer not started: below maxusers threshold" }
        return
    }
    val delayValue = funnel.funnelTransit?.timeBeforeMoveToOutput!!
    val targetIdCategory = getOrCreateCategory(guild, funnelOutput.categoryName, funnel.rolesVisibility, funnel.noTextForRoles, funnel.noTextNoVoiceForRoles).id
    transitJobs[transitChanId]?.cancel()
    transitJobs[transitChanId] = coroutineScope.launch {
        logger.info { "Transit move timer starting delay $delayValue seconds" }
        delay(delayValue * 1000L)
        try {
            if (guild.getChannelOf<VoiceChannel>(transitChanId).voiceStates.toList().size < funnelOutput.tempChannelGenerator.maxUsers){
                logger.info { "Transit move canceled: below maxusers threshold after delay" }
                return@launch
            }
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

internal suspend fun AutoVoiceFunnelsBot.createEntryChannelIfNotExisting(
    guild: GuildBehavior, categoryName: String, voiceFunnel: VoiceFunnel
): Snowflake {
    val guildChannels = guild.channels.filterIsInstance<VoiceChannel>().toList()
    val category = getOrCreateCategory(guild, categoryName, voiceFunnel.rolesVisibility, voiceFunnel.noTextForRoles, voiceFunnel.noTextNoVoiceForRoles)
    autoVoiceFunnelsState.snowflakeMap[guild.id]?.find { (dslname, _) -> categoryName+dslname == voiceFunnel.entryChannelName }
        ?.let { (_, snowflake) ->
            guildChannels.firstOrNull { it.id == snowflake }?.let {
                return snowflake
            }
        }
    val channel = guildChannels.find { it.name == voiceFunnel.entryChannelName }
        ?: guild.createVoiceChannel(voiceFunnel.entryChannelName) {
            parentId = category.id
            //position = positionEntry
        }
    addResourceNameToState(guild.id, categoryName+channel.name, channel.id)
    return channel.id
}

internal suspend fun AutoVoiceFunnelsBot.addResourceNameToState(
    guildId: Snowflake, resourceName: String, resourceId: Snowflake
) {
    val pairNameSnowflake = Pair(resourceName, resourceId)
    if (autoVoiceFunnelsState.snowflakeMap[guildId] == null) autoVoiceFunnelsState.snowflakeMap[guildId] =
        mutableListOf()
    if (autoVoiceFunnelsState.snowflakeMap[guildId]?.contains(pairNameSnowflake) == false) autoVoiceFunnelsState.snowflakeMap[guildId]?.add(
        pairNameSnowflake
    )
}

internal suspend fun AutoVoiceFunnelsBot.getOrCreateCategory(
    guild: GuildBehavior,
    categoryName: String,
    rolesVisibility: Pair<List<String>?, List<String>?>?,
    noTextForRoles: List<String>?,
    noTextNoVoiceForRoles: List<String>?
): TopGuildChannel {
    autoVoiceFunnelsState.snowflakeMap[guild.id]?.find { (dslName, _) -> dslName == categoryName }
        ?.let { (_, snowflake) ->
            guild.channels.filterIsInstance<Category>().firstOrNull { it.id == snowflake }?.let {
                return it
            }
        }

    val existingCategory = guild.channels.filterIsInstance<Category>().firstOrNull { it.name == categoryName }

    if (existingCategory != null) {
        addResourceNameToState(guild.id, categoryName, existingCategory.id)
        return existingCategory
    }

    val category = guild.createCategory(categoryName) {
        rolesVisibility?.let { (visibilityRoles, noVisibilityRoles) ->
            guild.roles.filter { visibilityRoles?.contains(it.name) == true }.toList().forEach { roleWithVisibility ->
                permissionOverwrites.add(
                    Overwrite(
                        id = roleWithVisibility.id,
                        type = OverwriteType.Role,
                        allow = Permissions(Permission.ViewChannel),
                        deny = Permissions()
                    )
                )
            }
            guild.roles.filter { noVisibilityRoles?.contains(it.name) == true }.toList().forEach { roleWithVisibility ->
                permissionOverwrites.add(
                    Overwrite(
                        id = roleWithVisibility.id,
                        type = OverwriteType.Role,
                        allow = Permissions(),
                        deny = Permissions(Permission.ViewChannel)
                    )
                )
            }
        }
        guild.roles.filter { noTextForRoles?.contains(it.name) == true }.toList().forEach { roleWithoutText ->
            permissionOverwrites.add(
                Overwrite(
                    id = roleWithoutText.id,
                    type = OverwriteType.Role,
                    allow = Permissions(),
                    deny = Permissions(Permission.SendMessages)
                )
            )
        }
        guild.roles.filter { noTextNoVoiceForRoles?.contains(it.name) == true }.toList()
            .forEach { roleWithoutTextNorVoice ->
                permissionOverwrites.add(
                    Overwrite(
                        id = roleWithoutTextNorVoice.id,
                        type = OverwriteType.Role,
                        allow = Permissions(),
                        deny = Permissions(Permission.SendMessages, Permission.Connect)
                    )
                )
            }
    }
    addResourceNameToState(guild.id, categoryName, category.id)
    return category
}

internal suspend fun AutoVoiceFunnelsBot.getOrCreateTransitChannel(
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
                is SimpleName -> (funnelTransit.transitChannelNameStrategy as SimpleName).name
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
                    voiceChannel.id, transitChannelsCategory.id, voiceFunnel.entryChannelName
                )
            )
            voiceChannel
        }

        else -> guild.getChannelOf<VoiceChannel>(transitChannel.id)
    }
}

suspend fun AutoVoiceFunnelsBot.newNumberedWithSchemeName(
    guild: Guild, transitChannelNameStrategy: NamingStrategy
): String {
    val numberedWithScheme = transitChannelNameStrategy as NumberedWithScheme
    return numberedWithScheme.scheme.replace("%%", (transitChannels.size + 1).toString())
}


internal suspend fun AutoVoiceFunnelsBot.getOrCreateTemporaryChannel(
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

internal suspend fun AutoVoiceFunnelsBot.newTempChannelFrakturedName(guild: Guild): String {
    val usedNames = guild.channels.filterIsInstance<VoiceChannel>().filter {
        it.id in tempChannels.map { chan -> chan.id }
    }.map { it.name }.toSet()
    return frakturedChannelNames.minus(usedNames).random()
}

internal suspend fun AutoVoiceFunnelsBot.createEntryChannelsAndCategories(event: ReadyEvent) {
    logger.debug { "Guilds ${event.guildIds}" }
    event.guilds.forEach { guild ->
        funnelsGroups.forEach { entryCategory ->
            logger.debug { "DSL DEBUG $entryCategory" }
            entryCategory.entryChannelsGroup.forEach { voiceFunnel ->
                val category = getOrCreateCategory(guild, entryCategory.groupName, voiceFunnel.rolesVisibility, voiceFunnel.noTextForRoles, voiceFunnel.noTextNoVoiceForRoles)
                entryChannels.add(
                    EntryChannel(
                        createEntryChannelIfNotExisting(
                            guild, entryCategory.groupName, voiceFunnel
                        ), category.id, voiceFunnel.entryChannelName
                    )
                )
            }
        }
        funnelsGroups.forEach { entryCategory ->
            entryCategory.entryChannelsGroup.forEach { voiceFunnel ->
                voiceFunnel.funnelTransit?.let { funnelTransit ->
                    val transitCategoryKord =
                        getOrCreateCategory(guild, funnelTransit.transitCategoryName, voiceFunnel.rolesVisibility, voiceFunnel.noTextForRoles, voiceFunnel.noTextNoVoiceForRoles)
                    deleteVocalsUnderCategory(guild, transitCategoryKord.id)
                    val transitCategory = TransitCategory(transitCategoryKord.id, voiceFunnel.entryChannelName)
                    if (!transitCategories.contains(transitCategory)) transitCategories.add(transitCategory)
                }
            }
        }
        funnelsGroups.forEach { entryCategory ->
            entryCategory.entryChannelsGroup.forEach { voiceFunnel ->
                val outputCategoryName = (voiceFunnel.voiceFunnelOutput as SimpleFunnelOutput).categoryName
                val outputcategoryKord = getOrCreateCategory(guild, outputCategoryName, voiceFunnel.rolesVisibility,voiceFunnel.noTextForRoles, voiceFunnel.noTextNoVoiceForRoles)
                deleteVocalsUnderCategory(guild, outputcategoryKord.id)
                val tempChannelCategory = TempChannelCategory(outputcategoryKord.id, voiceFunnel.entryChannelName)
                if (!tempChannelCategories.contains(tempChannelCategory)) tempChannelCategories.add(
                    tempChannelCategory
                )
            }
        }
    }
    writeConfig(autoVoiceFunnelsState)
}
