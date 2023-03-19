package dev.autovoicefunnels.models

interface NamingStrategy
class RandomizedFrakturedNames : NamingStrategy
class UsernameBased : NamingStrategy
data class NumberedWithScheme(val scheme: String) : NamingStrategy

data class TempChannelGenerator(val nameStrategy: NamingStrategy, val maxUsers: Int)

interface VoiceFunnelOutput
data class SimpleFunnelOutput(
    val categoryName: String,
    val tempChannelGenerator: TempChannelGenerator
) : VoiceFunnelOutput

data class AggregatedFunnelOutput(val funnelIds: List<String>) : VoiceFunnelOutput

data class FunnelTransit(
    val transitCategoryName: String,
    val transitChannelNameStrategy: NamingStrategy,
    val timeBeforeMoveToOutput: Long
)

data class VoiceFunnel(
    val entryChannelName: String,
    val funnelTransit: FunnelTransit?,
    val voiceFunnelOutput: VoiceFunnelOutput,
    val disableBlacklist: Boolean,
    val disableFillUp: Boolean,
    val tag: String? = null
)

data class EntryChannelsGroup(val groupName: String, val entryChannelsGroup: List<VoiceFunnel>)
