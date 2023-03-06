import dev.kord.common.entity.*
import dev.kord.core.Kord
import dev.kord.core.behavior.*
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

sealed class MMR(val mmrKey: String, val position: Int) {
    object STAR : MMR("*", 1)
    object THREE : MMR("3-4", 2)
    object FOUR : MMR("4-5",3)
    object FIVE : MMR("5-6",4)
    object USER : MMR("usr",5)
}

sealed class GameType(val maxHunters: Int, val name: String) {
    object Duo : GameType(2, "DUO")
    object Trio : GameType(3, "TRIO")
}

data class EntryCategory(val name: String, val gameType: GameType)
data class EntryChannel(val id: Snowflake, val idCategory: Snowflake, val mmr: MMR, val gameType: GameType)
data class TempChannel(val id: Snowflake, val idCategory: Snowflake, val mmr: MMR, val gameType: GameType)
data class TempChannelCategory(val id: Snowflake, val mmr: MMR, val gameType: GameType)

class AutoHuntBot(private val token: String) {
    private lateinit var bot: Kord
    private val entryChannelPrefix = "Rch"
    private val entryChannelKeyPrefix = "MMR"

    private val entryCategories = listOf(
        EntryCategory("TRIO - Rch hunter", GameType.Trio), EntryCategory("DUO - Rch hunter", GameType.Duo)
    )
    private val DEV_CLEANUP = true

    private val entryChannels = mutableListOf<EntryChannel>()
    private val tempChannels = mutableListOf<TempChannel>()
    private val categoriesTempChannels = mutableListOf<TempChannelCategory>()

    suspend fun start() {
        bot = Kord(token)
        bot.on<ReadyEvent> {
            logger.debug { "Ready" }
            initEntryChannels(this)
        }
        bot.on<VoiceStateUpdateEvent> {
            autoManageUserIfEventInEntryChannel(this)
            channelCleanup(this)
        }
        bot.on<ChatInputCommandCreateEvent> {
            blacklistCommand(this)
        }
        bot.login()
    }

    private fun blacklistCommand(inputCommand: ChatInputCommandCreateEvent) {
        logger.debug { "inputcommand $inputCommand" }
    }

    private suspend fun initEntryChannels(event: ReadyEvent) {
        logger.debug { "Guilds ${event.guildIds}" }
        event.guilds.forEach { guild ->
            // Manual cleanup for dev
            if (DEV_CLEANUP) {
                deleteEverything(guild)
                entryCategories.forEach {
                    deleteChannelsAndCategory(it.name, guild)
                }
                deleteChannelsAndCategory("MMR 3 TRIO", guild)
                deleteChannelsAndCategory("MMR 3-4 TRIO", guild)
                deleteChannelsAndCategory("MMR 4-5 TRIO", guild)
                deleteChannelsAndCategory("MMR 6 TRIO", guild)
                deleteChannelsAndCategory("MMR * TRIO", guild)
                deleteChannelsAndCategory("MMR * DUO", guild)
                deleteChannelsAndCategory("MMR 3 DUO", guild)
                deleteChannelsAndCategory("MMR 3-4 DUO", guild)
                deleteChannelsAndCategory("MMR 6 DUO", guild)
                deleteChannelsAndCategory("MMR 4-5 DUO", guild)
                deleteChannelsAndCategory("MMR usr DUO", guild)
                deleteChannelsAndCategory("MMR usr TRIO", guild)
                logger.debug { "Cleanup done" }
            }
            entryCategories.forEach { entryCategory ->
                val suffixEntry = entryCategory.gameType.name
                val category = getOrCreateCategory(guild, entryCategory.name)
                MMR::class.sealedSubclasses.mapNotNull { it.objectInstance }.forEach { key ->
                    val currentChannelName = when (key.mmrKey) {
                        "usr" -> "Ton Vocal $suffixEntry"
                        else -> "$entryChannelPrefix $entryChannelKeyPrefix ${key.mmrKey} $suffixEntry"
                    }
                    val voiceEntryChannelId =
                        createEntryChannelIfNotExisting(entryCategory.name, currentChannelName, guild, key.position)
                    entryChannels.add(EntryChannel(voiceEntryChannelId, category.id, key, entryCategory.gameType))
                }
            }
        }
    }

    private suspend fun autoManageUserIfEventInEntryChannel(event: VoiceStateUpdateEvent) {
        val guild = event.state.getGuild()
        val entryChannel = entryChannels.find { event.state.channelId == it.id } ?: return
        when (entryChannel.mmr) {
            MMR.USER -> createTempChannelUserMmr(event)
            else -> {
                val tempChannel = createOrGetTemporaryChannel(guild, entryChannel.mmr, entryChannel.gameType)
                val memberName = event.state.getMember().displayName
                tempChannel?.let {
                    event.state.getMember().edit {
                        voiceChannelId = tempChannel.id
                    }
                }
                if (tempChannel == null)
                    logger.info { "Not moving user $memberName since no compat chan found" }
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

    private suspend fun createTempChannelUserMmr(voiceStateUpdateEvent: VoiceStateUpdateEvent) {
            val member = voiceStateUpdateEvent.state.getMember()
            val memberName = member.displayName
            val gameType = entryChannels.firstOrNull { voiceStateUpdateEvent.state.channelId == it.id }?.gameType
            val voiceChannel = createOrGetUserTempChannel(member.getGuild(), memberName, gameType!!)
            member.edit {
                voiceChannelId = voiceChannel.id
            }
    }

    private suspend fun createOrGetUserTempChannel(guild: Guild, name: String, gameType: GameType): VoiceChannel {
        val categoryName = "$entryChannelKeyPrefix ${"usr"} ${gameType.name}"
        val tempChannelsCategory = getOrCreateCategory(guild, categoryName)
        val tempVoiceChannel = guild.createVoiceChannel("$name hunt") {
            userLimit = gameType.maxHunters
            parentId = tempChannelsCategory.id
        }
        tempChannels.add(TempChannel(tempVoiceChannel.id, tempChannelsCategory.id, MMR.USER, gameType))
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
        categoryName: String, currentChannelName: String, guild: GuildBehavior, positionEntry: Int
    ): Snowflake {
        val guildChannels = guild.channels.toList()
        val category = getOrCreateCategory(guild, categoryName)
        val channel =
            guildChannels.find { it.name == currentChannelName } ?: guild.createVoiceChannel(currentChannelName) {
                parentId = category.id
                position = positionEntry
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


    private suspend fun createOrGetTemporaryChannel(guild: Guild, mmr: MMR, gameType: GameType): VoiceChannel? {
        // filtering on these categories
        val categoryIdsWhitelist = mutableListOf<Snowflake>().apply {
            when (mmr.mmrKey) {
                "*" -> {
                    categoriesTempChannels.filter { it.gameType == gameType }.forEach {
                        add(it.id)
                    }
                }

                "usr" -> {
                    // noop
                }

                else -> {
                    categoriesTempChannels.filter { it.gameType == gameType && it.mmr == mmr }.forEach {
                        add(it.id)
                    }
                }
            }
        }
        logger.debug { categoryIdsWhitelist }
        val tempChannel = tempChannels.firstOrNull {
            categoryIdsWhitelist.contains(it.idCategory) &&
                    guild.getChannelOf<VoiceChannel>(it.id).voiceStates.toList().size < gameType.maxHunters
        }
        return when (tempChannel) {
            null -> {
                when (mmr) {
                    MMR.STAR -> null
                    else -> {
                        val categoryName = "$entryChannelKeyPrefix ${mmr.mmrKey} ${gameType.name}"
                        val tempChannelsCategory = getOrCreateCategory(guild, categoryName)
                        if (categoriesTempChannels.find { it.id == tempChannelsCategory.id } == null) {
                            categoriesTempChannels.add(TempChannelCategory(tempChannelsCategory.id, mmr, gameType))
                        }
                        val voiceChannel = guild.createVoiceChannel(newTempChannelName(guild)) {
                            parentId = tempChannelsCategory.id
                        }
                        tempChannels.add(TempChannel(voiceChannel.id, tempChannelsCategory.id, mmr, gameType))
                        voiceChannel
                    }
                }
            }

            else -> guild.getChannelOf<VoiceChannel>(tempChannel.id)
        }
    }

    private suspend fun newTempChannelName(guild: Guild): String {
        val usedNames = guild.channels.filterIsInstance<VoiceChannel>().filter {
            it.id in tempChannels.map { chan -> chan.id }
        }.map { it.name }.toSet()
        return frakturedStates.minus(usedNames).random()
    }
}