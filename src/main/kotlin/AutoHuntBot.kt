import dev.kord.common.entity.*
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.createCategory
import dev.kord.core.behavior.createVoiceChannel
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ChatInputCommandCreateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import kotlinx.coroutines.flow.*
import mu.KotlinLogging


private val logger = KotlinLogging.logger { }

class AutoHuntBot(private val token: String) {
    private lateinit var bot: Kord
    private val entryChannelPrefix = "Rch"
    private val entryChannelKeyPrefix = "MMR"
    private val entryChannelKeys = listOf("*", "3", "4-5", "6", "usr")
    private val entryCategories = listOf("TRIO - Rch hunter", "DUO - Rch hunter")
    private val entryChannelIds = mutableListOf<Triple<Snowflake, String, String>>()
    private val tempChannelsIds = mutableListOf<Triple<Snowflake, String, String>>()

    suspend fun start() {
        bot = Kord(token)
        bot.on<ReadyEvent> {
            logger.debug { "Ready" }
            initEntryChannels(this)
        }
        bot.on<VoiceStateUpdateEvent> {
            autoManageUserIfEventInEntryChannel(this)
            channelCleanup(this)
            logger.debug { "DEBUG entry channel" }
            entryChannelIds.forEach {
                logger.debug { it }
            }
            logger.debug { "DEBUG temp channel" }
            tempChannelsIds.forEach {
                logger.debug { it }
            }
        }
        bot.on<ChatInputCommandCreateEvent> {
            blacklistCommand(this)
        }
        //bot.on<Event> {
        //    logger.debug { this }
        //}
        bot.login()
    }

    private fun blacklistCommand(inputCommand: ChatInputCommandCreateEvent) {
        logger.debug { "inputcommand $inputCommand" }
    }

    private suspend fun initEntryChannels(event: ReadyEvent) {
        logger.debug { "Guilds ${event.guildIds}" }
        event.guilds.forEach { guild ->
            // Manual cleanup for dev
            deleteEverything(guild)
            entryCategories.forEach {
                deleteChannelsAndCategory(it, guild)
            }
            deleteChannelsAndCategory("MMR 3 TRIO", guild)
            deleteChannelsAndCategory("MMR 4-5 TRIO", guild)
            deleteChannelsAndCategory("MMR 6 TRIO", guild)
            deleteChannelsAndCategory("MMR * TRIO", guild)
            deleteChannelsAndCategory("MMR * DUO", guild)
            deleteChannelsAndCategory("MMR 3 DUO", guild)
            deleteChannelsAndCategory("MMR 6 DUO", guild)
            deleteChannelsAndCategory("MMR 4-5 DUO", guild)
            deleteChannelsAndCategory("MMR usr DUO", guild)
            deleteChannelsAndCategory("MMR usr TRIO", guild)
            logger.debug { "Cleanup done" }
            entryCategories.forEach { category ->
                val suffixEntry = category.split("-").first().trim()
                entryChannelKeys.forEach { key ->
                    val currentChannelName = when (key) {
                        "usr" -> "Ton Vocal $suffixEntry"
                        else -> "$entryChannelPrefix $entryChannelKeyPrefix $key $suffixEntry"
                    }
                    val voiceEntryChannelId = createEntryChannelIfNotExisting(category, currentChannelName, guild)
                    entryChannelIds.add(Triple(voiceEntryChannelId, key, suffixEntry))
                }
            }
        }
    }

    private suspend fun autoManageUserIfEventInEntryChannel(event: VoiceStateUpdateEvent) {
        val guild = event.state.getGuild()
        val triple = entryChannelIds.find { (id, _, _) -> id == event.state.channelId }
        triple?.let { (channelId, mmrKey, typeKey) ->
            when (mmrKey) {
                "usr" -> userBasedCreation(event)
                else -> {
                    val tempChannel = createOrGetTemporaryChannel(guild, mmrKey, typeKey)
                    event.state.getMember().edit {
                        voiceChannelId = tempChannel.id
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
            if (!tempChannelsIds.map { (id, _, _) -> id }.contains(voiceChannel.id)) {
                return
            }
            if (voiceChannel.voiceStates.toList().isEmpty()) {
                voiceChannel.delete()
                tempChannelsIds.remove(tempChannelsIds.find { (id, _, _) -> id == voiceChannel.id })
            }
        }
    }

    private suspend fun userBasedCreation(voiceStateUpdateEvent: VoiceStateUpdateEvent) {
        val member = voiceStateUpdateEvent.state.getMember()
        val name = member.displayName
        val type =
            entryChannelIds.find { (id, _, _) -> voiceStateUpdateEvent.state.getChannelOrNull()?.id == id }?.third
        val voiceChannel = createOrGetUserTempChannel(member.getGuild(), name, type!!)
        member.edit {
            voiceChannelId = voiceChannel.id
        }
    }

    private suspend fun createOrGetUserTempChannel(guild: Guild, name: String, type: String): VoiceChannel {
        val categoryName = forgeTempChannelCategoryName("usr", type)
        val tempChannelsCategory = getOrCreateCategory(guild, guild.channels.toList(), categoryName)
        val maxHunters = getMaxUsersFromType(type)
        val tempVoiceChannel = guild.channels.filterIsInstance<VoiceChannel>().filter {
            it.category?.id == tempChannelsCategory.id && it.voiceStates.toList().size < maxHunters
        }.firstOrNull() ?: guild.createVoiceChannel("$name hunt") {
            userLimit = maxHunters
            parentId = tempChannelsCategory.id
        }
        tempChannelsIds.add(Triple(tempVoiceChannel.id, "usr", type))
        return tempVoiceChannel
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
        categoryName: String, currentChannelName: String, guild: GuildBehavior
    ): Snowflake {
        val guildChannels = guild.channels.toList()
        val category = getOrCreateCategory(guild, guildChannels, categoryName)
        val channel =
            guildChannels.find { it.name == currentChannelName } ?: guild.createVoiceChannel(currentChannelName) {
                parentId = category.id
            }
        return channel.id
    }

    private fun forgeTempChannelCategoryName(mmrKey: String, typeKey: String): String {
        return "$entryChannelKeyPrefix $mmrKey $typeKey"
    }

    private suspend fun getOrCreateCategory(
        guild: GuildBehavior, guildChannels: List<TopGuildChannel>, categoryName: String
    ): TopGuildChannel {
        val existingCategory = guildChannels.find { it.type == ChannelType.GuildCategory && it.name == categoryName }
        if (existingCategory != null) {
            return existingCategory
        }

        val everyoneRole = guild.getRoleOrNull(guild.id)
        return guild.createCategory(categoryName) {
            //permissionOverwrites.add(
            //    Overwrite(
            //        id = everyoneRole?.id!!, type = OverwriteType.Role, allow = Permissions(), deny = Permissions(
            //            Permission.ManageChannels
            //        )
            //    )
            //)
        }
    }


    private suspend fun createOrGetTemporaryChannel(guild: Guild, mmrKey: String, typeKey: String): VoiceChannel {
        val categoryName = forgeTempChannelCategoryName(mmrKey, typeKey)
        val tempChannelsCategory = getOrCreateCategory(guild, guild.channels.toList(), categoryName)
        // filtering on these categories
        val categoryIdsWhitelist = mutableListOf<Snowflake>().apply {
            when (mmrKey) {
                "*" -> {
                    val chanIds = tempChannelsIds.filter { (id, mmr, type) -> type == typeKey && mmr != "usr" }.map{
                        (id, _, _) -> id }.toList()
                    chanIds.map { (guild.getChannel(it) as VoiceChannel).category?.id }.distinct().forEach {
                        it?.let {
                            add(it)
                        }
                    }
                }
                "usr" -> {
                    // noop
                }
                else -> {
                    add(tempChannelsCategory.id)
                }
            }
        }
        logger.debug { categoryIdsWhitelist }
        val maxHunters = getMaxUsersFromType(typeKey)
        val tempVoiceChannel = guild.channels.filterIsInstance<VoiceChannel>().filter {
            categoryIdsWhitelist.contains(it.category?.id) && it.voiceStates.toList().size < maxHunters
        }.firstOrNull() ?: guild.createVoiceChannel(forgeNewTempChannelName(guild)) {
            userLimit = maxHunters
            parentId = tempChannelsCategory.id
        }
        tempChannelsIds.add(Triple(tempVoiceChannel.id, mmrKey, typeKey))
        return tempVoiceChannel
    }

    private suspend fun forgeNewTempChannelName(guild: Guild): String {
        val usedNames = guild.channels.filterIsInstance<VoiceChannel>().filter {
            it.id in tempChannelsIds.map { (id, _, _) -> id }
        }.map { it.name }.toSet()
        logger.debug { "used names $usedNames" }
        val value = frakturedStates.minus(usedNames).random()
        logger.debug { value }
        return value
    }

    private fun getMaxUsersFromType(typeKey: String): Int {
        return when (typeKey) {
            "TRIO" -> 3
            "DUO" -> 2
            else -> {
                logger.error { "Bad stuff about to happen here, typeKey unknown $typeKey" }
                -1
            }
        }
    }
}