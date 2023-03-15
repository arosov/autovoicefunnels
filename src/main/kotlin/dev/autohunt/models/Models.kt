package dev.autohunt.models

interface NamingStrategy
class RandomizedFrakturedNames : NamingStrategy
class UsernameBased : NamingStrategy
data class TempChannelGenerator(val nameStrategy: NamingStrategy, val maxUsers: Int)
data class SimpleFunnelOutput(val categoryName: String, val tempChannelGenerator: TempChannelGenerator) :
        VoiceFunnelOutput

data class AggregatedFunnelOutput(val funnelIds: List<String>) : VoiceFunnelOutput
interface VoiceFunnelOutput
data class VoiceFunnel(
    val id: String,
    val entryChannelName: String,
    val voiceFunnelOutput: VoiceFunnelOutput,
    val disableBlacklist: Boolean = false,
    val disableFillUp: Boolean = false
)

data class EntryChannelsGroup(val groupName: String, val entryChannelsGroup: List<VoiceFunnel>)
