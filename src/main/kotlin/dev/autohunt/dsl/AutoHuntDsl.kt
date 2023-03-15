package dev.autohunt.dsl

import dev.autohunt.bot.AutoHuntBot
import dev.autohunt.models.*

@DslMarker
annotation class AutoHuntDsl

class TempChannelGeneratorBuilder(namingStrategyInit: NamingStrategy = UsernameBased(), maxUsersInit: Int = 0) {
    @AutoHuntDsl
    var tempChannelNameStrategy: NamingStrategy = namingStrategyInit

    @AutoHuntDsl
    var maxUsers: Int = maxUsersInit

    fun build(): TempChannelGenerator {
        return TempChannelGenerator(tempChannelNameStrategy, maxUsers)
    }
}

class FunnelBuilder(private val id: String, channelName: String = "") {
    @AutoHuntDsl
    var entryChannelName: String = channelName
    @AutoHuntDsl
    var disableBlacklist: Boolean = false
    @AutoHuntDsl
    var disableFillUp: Boolean = false

    private lateinit var voiceFunnelOutput: VoiceFunnelOutput

    @AutoHuntDsl
    fun output(
        outputCategoryName: String,
        setup: TempChannelGeneratorBuilder.() -> Unit
    ) {
        val tmpChanBuilder = TempChannelGeneratorBuilder()
        tmpChanBuilder.setup()
        voiceFunnelOutput = SimpleFunnelOutput(outputCategoryName, tmpChanBuilder.build())
    }

    @AutoHuntDsl
    fun outputFromFunnels(funnelId: String, vararg funnels: String) {
        val funnelIds = funnels.toList() + funnelId
        voiceFunnelOutput = AggregatedFunnelOutput(funnelIds)
    }

    fun build(): VoiceFunnel {
        return VoiceFunnel(id, entryChannelName, voiceFunnelOutput, disableBlacklist, disableFillUp)
    }
}

class EntryChannelsGroupBuilder(private val groupName: String) {
    private val funnels = mutableListOf<VoiceFunnel>()

    @AutoHuntDsl
    fun funnel(id: String, setup: FunnelBuilder.() -> Unit) {
        val builder = FunnelBuilder(id)
        builder.setup()
        funnels += builder.build()
    }

    fun build(): EntryChannelsGroup {
        return EntryChannelsGroup(groupName, funnels)
    }
}

class AutoHuntBuilder {
    private val entryChannelsGroups = mutableListOf<EntryChannelsGroup>()

    @AutoHuntDsl
    fun funnels(entryCategoryName: String, setup: EntryChannelsGroupBuilder.() -> Unit) {
        val entryChannelsGroupBuilder = EntryChannelsGroupBuilder(entryCategoryName)
        entryChannelsGroupBuilder.setup()
        entryChannelsGroups += entryChannelsGroupBuilder.build()
    }

    fun build(): AutoHuntBot {
        return AutoHuntBot(entryChannelsGroups)
    }
}

@AutoHuntDsl
fun autoHunt(setup: AutoHuntBuilder.() -> Unit): AutoHuntBot {
    val autoHuntBuilder = AutoHuntBuilder()
    autoHuntBuilder.setup()
    return autoHuntBuilder.build()
}
