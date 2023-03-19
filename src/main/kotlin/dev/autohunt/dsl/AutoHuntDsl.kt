package dev.autohunt.dsl

import dev.autohunt.bot.AutoHuntBot
import dev.autohunt.models.*

@DslMarker
annotation class AutoHuntDsl

data class FunnelDefaults(val disableBlacklist: Boolean? = null, val disableFillUp: Boolean? = null)
data class TransitDefaults(val transitCategoryName: String?, val transitChannelNamingStrategy: NamingStrategy?, val secondsBeforeMoveToOutput: Long?)
data class OutputDefaults(
    val outputCategoryName: String?, val tempChannelNamingStrategy: NamingStrategy?,val maxUsers: Int?
)

class FunnelDefaultsBuilder {
    @AutoHuntDsl
    var disableBlacklist: Boolean = true

    @AutoHuntDsl
    var disableFillUp: Boolean = true
    fun build(): FunnelDefaults {
        return FunnelDefaults(disableBlacklist, disableFillUp)
    }
}

class TransitDefaultsBuilder {
    @AutoHuntDsl
    var transitCategoryName: String? = null

    @AutoHuntDsl
    var transitChannelNamingStrategy: NamingStrategy? = null

    @AutoHuntDsl
    var secondsBeforeMoveToOutput: Long? = null

    fun build(): TransitDefaults {
        return TransitDefaults(transitCategoryName, transitChannelNamingStrategy, secondsBeforeMoveToOutput)
    }
}

class OutputDefaultsBuilder {
    @AutoHuntDsl
    var outputCategoryName: String? = null

    @AutoHuntDsl
    var tempChannelNamingStrategy: NamingStrategy? = null

    @AutoHuntDsl
    var maxUsers: Int? = null

    fun build(): OutputDefaults {
        return OutputDefaults(outputCategoryName, tempChannelNamingStrategy!!, maxUsers!!)
    }
}

class OutputBuilder {
    @AutoHuntDsl
    var tempChannelNamingStrategy: NamingStrategy? = null

    @AutoHuntDsl
    var maxUsers: Int? = null
    fun build(): TempChannelGenerator {
        return TempChannelGenerator(tempChannelNamingStrategy!!, maxUsers!!)
    }
}

class TransitBuilder {
    @AutoHuntDsl
    lateinit var transitCategoryName: String

    @AutoHuntDsl
    var transitChannelNamingStrategy: NamingStrategy? = null

    @AutoHuntDsl
    var secondsBeforeMoveToOutput: Long? = null

    fun build(): FunnelTransit {
        return FunnelTransit(transitCategoryName!!, transitChannelNamingStrategy!!, secondsBeforeMoveToOutput!!)
    }
}

class FunnelBuilder() {
    @AutoHuntDsl
    var disableBlacklist: Boolean? = true

    @AutoHuntDsl
    var disableFillUp: Boolean? = true

    @AutoHuntDsl
    var tag: String? = null

    lateinit var entryChannelName: String

    private var voiceFunnelOutput: VoiceFunnelOutput? = null
    private var funnelTransit: FunnelTransit? = null

    var transitDefaults: TransitDefaults? = null
    var outputDefaults: OutputDefaults? = null

    @AutoHuntDsl
    fun output(
        outputCategoryName: String, setup: OutputBuilder.() -> Unit = {}
    ) {
        val tmpChanBuilder = OutputBuilder()
        tmpChanBuilder.setup()
        outputDefaults?.tempChannelNamingStrategy?.let {
            tmpChanBuilder.tempChannelNamingStrategy = tmpChanBuilder.tempChannelNamingStrategy ?: it
        }
        outputDefaults?.maxUsers?.let {
            tmpChanBuilder.maxUsers = tmpChanBuilder.maxUsers ?: it
        }
        voiceFunnelOutput = SimpleFunnelOutput(outputCategoryName, tmpChanBuilder.build())
    }

    @AutoHuntDsl
    fun outputFromFunnels(vararg funnelTags: String) {
        val funnelIds = funnelTags.toList()
        voiceFunnelOutput = AggregatedFunnelOutput(funnelIds)
    }

    @AutoHuntDsl
    fun transit(transitCategoryName: String, setup: TransitBuilder.() -> Unit) {
        val transitBuilder = TransitBuilder()
        transitBuilder.setup()
        transitDefaults?.secondsBeforeMoveToOutput?.let {
            transitBuilder.secondsBeforeMoveToOutput = transitBuilder.secondsBeforeMoveToOutput ?: it
        }
        transitDefaults?.transitChannelNamingStrategy?.let {
            transitBuilder.transitChannelNamingStrategy = transitBuilder.transitChannelNamingStrategy ?: it
        }
        transitBuilder.transitCategoryName = transitCategoryName
        funnelTransit = transitBuilder.build()
    }

    fun build(): VoiceFunnel {
        transitDefaults?.transitCategoryName?.let {
            funnelTransit = FunnelTransit(it, transitDefaults?.transitChannelNamingStrategy!!, transitDefaults?.secondsBeforeMoveToOutput!!)
        }
        // This crashes if no default nor specific output is specified
        outputDefaults?.outputCategoryName?.let {
            voiceFunnelOutput = SimpleFunnelOutput(outputDefaults?.outputCategoryName!!, TempChannelGenerator(outputDefaults?.tempChannelNamingStrategy!!, outputDefaults?.maxUsers!!))
        }
        return VoiceFunnel(
            entryChannelName, funnelTransit, voiceFunnelOutput!!, disableBlacklist!!, disableFillUp!!, tag
        )
    }
}

class EntryChannelsGroupBuilder(private val entryCategoryName: String) {

    private val funnels = mutableListOf<VoiceFunnel>()
    private var funnelDefaults: FunnelDefaults? = null
    private var transitDefaults: TransitDefaults? = null
    private var outputDefaults: OutputDefaults? = null

    @AutoHuntDsl
    fun funnel(entryChannelName: String, setup: FunnelBuilder.() -> Unit = {}) {
        val builder = FunnelBuilder()
        builder.entryChannelName = entryChannelName
        builder.transitDefaults = transitDefaults
        builder.outputDefaults = outputDefaults
        builder.setup()
        funnelDefaults?.disableBlacklist.let {
            builder.disableBlacklist = builder.disableBlacklist ?: it
        }
        funnelDefaults?.disableFillUp?.let {
            builder.disableFillUp = builder.disableFillUp ?: it
        }
        funnels += builder.build()
    }

    @AutoHuntDsl
    fun funnelDefaults(setup: FunnelDefaultsBuilder.() -> Unit) {
        val builder = FunnelDefaultsBuilder()
        builder.setup()
        funnelDefaults = builder.build()
    }

    @AutoHuntDsl
    fun transitDefaults(setup: TransitDefaultsBuilder.() -> Unit) {
        val builder = TransitDefaultsBuilder()
        builder.setup()
        transitDefaults = builder.build()
    }

    @AutoHuntDsl
    fun outputDefaults(setup: OutputDefaultsBuilder.() -> Unit) {
        val builder = OutputDefaultsBuilder()
        builder.setup()
        outputDefaults = builder.build()
    }

    fun build(): EntryChannelsGroup {
        return EntryChannelsGroup(entryCategoryName, funnels)
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
