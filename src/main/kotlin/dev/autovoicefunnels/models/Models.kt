package dev.autovoicefunnels.models

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NamingStrategy

@Serializable
object RandomizedFrakturedNames : NamingStrategy

@Serializable
object UsernameBased : NamingStrategy
@Serializable
data class NumberedWithScheme(val scheme: String) : NamingStrategy
@Serializable
data class SimpleName(val name: String) : NamingStrategy

@Serializable
data class TempChannelGenerator(val nameStrategy: NamingStrategy, val maxUsers: Int)

@Serializable
sealed interface VoiceFunnelOutput
@Serializable
data class SimpleFunnelOutput(
    val categoryName: String, val tempChannelGenerator: TempChannelGenerator
) : VoiceFunnelOutput

@Serializable
data class AggregatedFunnelOutput(val funnelIds: List<String>) : VoiceFunnelOutput

@Serializable
data class FunnelTransit(
    val transitCategoryName: String, val transitChannelNameStrategy: NamingStrategy, val timeBeforeMoveToOutput: Long
)

@Serializable
enum class Roles {
    VISIBLE,
    NOTVISIBLE,
    NOTEXT,
    NOTEXTNOVOCAL
}

@Serializable
data class VoiceFunnel(
    val entryChannelName: String,
    val funnelTransit: FunnelTransit?,
    val voiceFunnelOutput: VoiceFunnelOutput,
    val disableBlacklist: Boolean,
    val disableFillUp: Boolean,
    val tag: String? = null,
    val roles : Map<Roles, List<String>?> = emptyMap(),
)

@Serializable
data class EntryChannelsGroup(val groupName: String, val entryChannelsGroup: List<VoiceFunnel>)