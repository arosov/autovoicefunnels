package dev.autovoicefunnels.dsl

import dev.autovoicefunnels.bot.AutoVoiceFunnelsBot
import dev.autovoicefunnels.models.*

@DslMarker
annotation class AutoVoiceFunnelsDsl

data class FunnelDefaults(
    val disableBlacklist: Boolean? = null,
    val disableFillUp: Boolean? = null,
    val rolesVisibility: Pair<List<String>?, List<String>?>,
    val noTextForRoles: List<String>?,
    val noTextNoVocalForRoles: List<String>?
)

data class TransitDefaults(
    val transitCategoryName: String?,
    val transitChannelNamingStrategy: NamingStrategy?,
    val secondsBeforeMoveToOutput: Long?
)

data class OutputDefaults(
    val outputCategoryName: String?, val tempChannelNamingStrategy: NamingStrategy?, val maxUsers: Int?
)

class FunnelDefaultsBuilder {
    @AutoVoiceFunnelsDsl
    var disableBlacklist: Boolean = true

    @AutoVoiceFunnelsDsl
    var disableFillUp: Boolean = true

    private var visibleForRoles: List<String>? = null
    private var notVisibleForRoles: List<String>? = null
    private var noTextForRoles: List<String>? = null
    private var noTextNoVoiceForRoles: List<String>? = null

    @AutoVoiceFunnelsDsl
    fun visibleForRoles(vararg roles: String) {
        visibleForRoles = roles.toList()
    }

    @AutoVoiceFunnelsDsl
    fun notVisibleForRoles(vararg roles: String) {
        notVisibleForRoles = roles.toList()
    }

    @AutoVoiceFunnelsDsl
    fun noTextForRoles(vararg roles: String) {
        noTextForRoles = roles.toList()
    }

    @AutoVoiceFunnelsDsl
    fun noTextNoVoiceForRoles(vararg roles: String) {
        noTextNoVoiceForRoles = roles.toList()
    }

    fun build(): FunnelDefaults {
        return FunnelDefaults(
            disableBlacklist,
            disableFillUp,
            Pair(visibleForRoles, notVisibleForRoles),
            noTextForRoles,
            noTextNoVoiceForRoles
        )
    }
}

class TransitDefaultsBuilder {
    @AutoVoiceFunnelsDsl
    var transitCategoryName: String? = null

    @AutoVoiceFunnelsDsl
    var transitChannelNamingStrategy: NamingStrategy? = null

    @AutoVoiceFunnelsDsl
    var secondsBeforeMoveToOutput: Long? = null

    fun build(): TransitDefaults {
        return TransitDefaults(transitCategoryName, transitChannelNamingStrategy, secondsBeforeMoveToOutput)
    }
}

class OutputDefaultsBuilder {
    @AutoVoiceFunnelsDsl
    var outputCategoryName: String? = null

    @AutoVoiceFunnelsDsl
    var tempChannelNamingStrategy: NamingStrategy? = null

    @AutoVoiceFunnelsDsl
    var maxUsers: Int? = null

    fun build(): OutputDefaults {
        return OutputDefaults(outputCategoryName, tempChannelNamingStrategy!!, maxUsers!!)
    }
}

class OutputBuilder {
    @AutoVoiceFunnelsDsl
    var tempChannelNamingStrategy: NamingStrategy? = null

    @AutoVoiceFunnelsDsl
    var maxUsers: Int? = null
    fun build(): TempChannelGenerator {
        return TempChannelGenerator(tempChannelNamingStrategy!!, maxUsers!!)
    }
}

class TransitBuilder {
    @AutoVoiceFunnelsDsl
    lateinit var transitCategoryName: String

    @AutoVoiceFunnelsDsl
    var transitChannelNamingStrategy: NamingStrategy? = null

    @AutoVoiceFunnelsDsl
    var secondsBeforeMoveToOutput: Long? = null

    fun build(): FunnelTransit {
        return FunnelTransit(transitCategoryName, transitChannelNamingStrategy!!, secondsBeforeMoveToOutput!!)
    }
}

class FunnelBuilder {
    @AutoVoiceFunnelsDsl
    var rolesVisibility: Pair<List<String>?, List<String>?>? = null

    @AutoVoiceFunnelsDsl
    var noTextForRoles: List<String>? = null

    @AutoVoiceFunnelsDsl
    var noTextNoVocalForRoles: List<String>? = null

    @AutoVoiceFunnelsDsl
    var disableBlacklist: Boolean? = true

    @AutoVoiceFunnelsDsl
    var disableFillUp: Boolean? = true

    @AutoVoiceFunnelsDsl
    var tag: String? = null

    lateinit var entryChannelName: String

    private var voiceFunnelOutput: VoiceFunnelOutput? = null
    private var funnelTransit: FunnelTransit? = null

    var transitDefaults: TransitDefaults? = null
    var outputDefaults: OutputDefaults? = null

    @AutoVoiceFunnelsDsl
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

    @AutoVoiceFunnelsDsl
    fun outputFromFunnels(vararg funnelTags: String) {
        val funnelIds = funnelTags.toList()
        voiceFunnelOutput = AggregatedFunnelOutput(funnelIds)
    }

    @AutoVoiceFunnelsDsl
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
            funnelTransit = FunnelTransit(
                it,
                transitDefaults?.transitChannelNamingStrategy!!,
                transitDefaults?.secondsBeforeMoveToOutput!!
            )
        }
        // This crashes if no default nor specific output is specified
        outputDefaults?.outputCategoryName?.let {
            voiceFunnelOutput = SimpleFunnelOutput(
                outputDefaults?.outputCategoryName!!,
                TempChannelGenerator(outputDefaults?.tempChannelNamingStrategy!!, outputDefaults?.maxUsers!!)
            )
        }
        return VoiceFunnel(
            entryChannelName,
            funnelTransit,
            voiceFunnelOutput!!,
            disableBlacklist!!,
            disableFillUp!!,
            tag,
            rolesVisibility,
            noTextForRoles,
            noTextNoVocalForRoles
        )
    }
}

class EntryChannelsGroupBuilder(private val entryCategoryName: String) {

    private val funnels = mutableListOf<VoiceFunnel>()
    private var funnelDefaults: FunnelDefaults? = null
    private var transitDefaults: TransitDefaults? = null
    private var outputDefaults: OutputDefaults? = null

    @AutoVoiceFunnelsDsl
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
        funnelDefaults?.rolesVisibility?.let {
            builder.rolesVisibility = builder.rolesVisibility ?: it
        }
        funnelDefaults?.noTextForRoles?.let {
            builder.noTextForRoles = builder.noTextForRoles ?: it
        }
        funnelDefaults?.noTextNoVocalForRoles?.let {
            builder.noTextNoVocalForRoles = builder.noTextNoVocalForRoles ?: it
        }
        funnels += builder.build()
    }


    @AutoVoiceFunnelsDsl
    fun funnelDefaults(setup: FunnelDefaultsBuilder.() -> Unit) {
        val builder = FunnelDefaultsBuilder()
        builder.setup()
        funnelDefaults = builder.build()
    }

    @AutoVoiceFunnelsDsl
    fun transitDefaults(setup: TransitDefaultsBuilder.() -> Unit) {
        val builder = TransitDefaultsBuilder()
        builder.setup()
        transitDefaults = builder.build()
    }

    @AutoVoiceFunnelsDsl
    fun outputDefaults(setup: OutputDefaultsBuilder.() -> Unit) {
        val builder = OutputDefaultsBuilder()
        builder.setup()
        outputDefaults = builder.build()
    }

    fun build(): EntryChannelsGroup {
        return EntryChannelsGroup(entryCategoryName, funnels)
    }
}


class AutoVoiceFunnelsBuilder {
    private val entryChannelsGroups = mutableListOf<EntryChannelsGroup>()

    @AutoVoiceFunnelsDsl
    fun funnels(entryCategoryName: String, setup: EntryChannelsGroupBuilder.() -> Unit) {
        val entryChannelsGroupBuilder = EntryChannelsGroupBuilder(entryCategoryName)
        entryChannelsGroupBuilder.setup()
        entryChannelsGroups += entryChannelsGroupBuilder.build()
    }

    fun build(): AutoVoiceFunnelsBot {
        return AutoVoiceFunnelsBot(entryChannelsGroups)
    }
}

@AutoVoiceFunnelsDsl
fun autoVoiceFunnels(setup: AutoVoiceFunnelsBuilder.() -> Unit): AutoVoiceFunnelsBot {
    val autoVoiceFunnelsBuilder = AutoVoiceFunnelsBuilder()
    autoVoiceFunnelsBuilder.setup()
    return autoVoiceFunnelsBuilder.build()
}
