package dev.autohunt.bot

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.interaction.GuildUserCommandInteractionCreateEvent
import kotlinx.coroutines.flow.firstOrNull
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

private suspend fun AutoHuntBot.blacklistStatus(event: GuildUserCommandInteractionCreateEvent) {
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

private suspend fun AutoHuntBot.blacklistCommand(commandEvent: GuildUserCommandInteractionCreateEvent) {
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

private suspend fun AutoHuntBot.blacklistRemoveCommand(commandEvent: GuildUserCommandInteractionCreateEvent) {
    val response = commandEvent.interaction.deferEphemeralResponse()
    val user = commandEvent.interaction.user
    val targetUser = commandEvent.interaction.target.asUser()
    val msg = "Hunter ${targetUser.username} retiré de la blacklist"
    removeUserFromBlacklist(user.id.value.toLong(), targetUser.id.value.toLong())
    response.respond {
        content = msg
    }
}

private fun AutoHuntBot.blacklistUser(blacklister: Long, blacklistee: Long): Boolean {
    if (blacklister == blacklistee || bot.selfId.value.toLong() == blacklistee) return false
    val userBlacklist = blacklist[blacklister]
    if (userBlacklist == null) {
        blacklist[blacklister] = mutableListOf<Long>().apply { add(blacklistee) }
    } else {
        blacklist[blacklister]?.add(blacklistee)
    }
    return true
}

private fun AutoHuntBot.removeUserFromBlacklist(blacklister: Long, blacklistee: Long) {
    blacklist[blacklister]?.remove(blacklistee)
}

internal suspend fun AutoHuntBot.channelHasNoBlacklistedUser(userId: Long, guild: Guild, tempChannel: TempChannel): Boolean {
    if (userId<0) return true
    val blacklistedFirstUserFound = guild.getChannelOf<VoiceChannel>(tempChannel.id).voiceStates.firstOrNull {
        blacklist[userId]?.contains(it.userId.value.toLong()) == true
    }
    logger.debug { "BLACKLIST DEBUG $blacklistedFirstUserFound" }
    return (blacklistedFirstUserFound ?: true) as Boolean
}

