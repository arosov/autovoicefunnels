package dev.autovoicefunnels

import dev.autovoicefunnels.dsl.autoVoiceFunnels
import dev.autovoicefunnels.models.RandomizedFrakturedNames
import dev.autovoicefunnels.models.SimpleName

fun huntFrBotDsl() = autoVoiceFunnels {
    funnels(entryCategoryName = "Creation rch Duo") {
        funnelDefaults {
            disableBlacklist = true
            disableFillUp = true
            noTextForRoles("Mute")
            noTextNoVoiceForRoles("Super Mute")
            visibleForRoles("PC")
            notVisibleForRoles("Xbox", "PlayStation")
        }
        transitDefaults {
            secondsBeforeMoveToOutput = 8
        }
        outputDefaults {
            outputCategoryName = "Vocaux Duo"
            maxUsers = 1
            tempChannelNamingStrategy = RandomizedFrakturedNames
        }
        funnel(entryChannelName = "Sans MMR Duo") {
            transit(transitCategoryName = "Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch sans MMR")
            }
        }
        funnel(entryChannelName = "MMR 3-4 Duo") {
            transit(transitCategoryName = "Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 3-4")
            }
        }
        funnel(entryChannelName = "MMR 4-5 Duo") {
            transit(transitCategoryName = "Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 4-5")
            }
        }
        funnel(entryChannelName = "MMR 5-6 Duo") {
            transit(transitCategoryName = "Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 5-6")
            }
        }
    }
    funnels(entryCategoryName = "Creation rch Trio") {
        funnelDefaults {
            disableBlacklist = true
            disableFillUp = true
            noTextForRoles("Mute")
            noTextNoVoiceForRoles("Super Mute")
            visibleForRoles("PC")
            notVisibleForRoles("Xbox", "PlayStation")
        }
        transitDefaults {
            secondsBeforeMoveToOutput = 10
        }
        outputDefaults {
            outputCategoryName = "Vocaux Trio"
            maxUsers = 3
            tempChannelNamingStrategy = RandomizedFrakturedNames
        }
        funnel(entryChannelName = "Sans MMR Trio") {
            transit(transitCategoryName = "Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch sans MMR")
            }
        }
        funnel(entryChannelName = "MMR 3-4 Trio") {
            transit(transitCategoryName = "Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 3-4")
            }
        }
        funnel(entryChannelName = "MMR 4-5 Trio") {
            transit(transitCategoryName = "Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 4-5")
            }
        }
        funnel(entryChannelName = "MMR 5-6 Trio") {
            transit(transitCategoryName = "Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 5-6")
            }
        }
    }
    funnels(entryCategoryName = "PS Creation rch Duo") {
        funnelDefaults {
            disableBlacklist = true
            disableFillUp = true
            noTextForRoles("Mute")
            noTextNoVoiceForRoles("Super Mute")
            visibleForRoles("PlayStation")
            notVisibleForRoles("Xbox", "PC")
        }
        transitDefaults {
            secondsBeforeMoveToOutput = 30
        }
        outputDefaults {
            outputCategoryName = "PS Vocaux Duo"
            maxUsers = 2
            tempChannelNamingStrategy = RandomizedFrakturedNames
        }
        funnel(entryChannelName = "PS Sans MMR Duo") {
            transit(transitCategoryName = "PS Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch sans MMR")
            }
        }
        funnel(entryChannelName = "PS MMR 3-4 Duo") {
            transit(transitCategoryName = "PS Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 3-4")
            }
        }
        funnel(entryChannelName = "PS MMR 4-5 Duo") {
            transit(transitCategoryName = "PS Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 4-5")
            }
        }
        funnel(entryChannelName = "PS MMR 5-6 Duo") {
            transit(transitCategoryName = "PS Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 5-6")
            }
        }
    }
    funnels(entryCategoryName = "PS Creation rch Trio") {
        funnelDefaults {
            disableBlacklist = true
            disableFillUp = true
            noTextForRoles("Mute")
            noTextNoVoiceForRoles("Super Mute")
            visibleForRoles("PlayStation")
            notVisibleForRoles("Xbox", "PC")
        }
        transitDefaults {
            secondsBeforeMoveToOutput = 8
        }
        outputDefaults {
            outputCategoryName = "PS Vocaux Trio"
            maxUsers = 3
            tempChannelNamingStrategy = RandomizedFrakturedNames
        }
        funnel(entryChannelName = "PS Sans MMR Trio") {
            transit(transitCategoryName = "PS Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch sans MMR")
            }
        }
        funnel(entryChannelName = "PS MMR 3-4 Trio") {
            transit(transitCategoryName = "PS Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 3-4")
            }
        }
        funnel(entryChannelName = "PS MMR 4-5 Trio") {
            transit(transitCategoryName = "PS Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 4-5")
            }
        }
        funnel(entryChannelName = "PS MMR 5-6 Trio") {
            transit(transitCategoryName = "PS Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 5-6")
            }
        }
    }
    funnels(entryCategoryName = "Xbox Creation rch Duo") {
        funnelDefaults {
            disableBlacklist = true
            disableFillUp = true
            noTextForRoles("Mute")
            noTextNoVoiceForRoles("Super Mute")
            visibleForRoles("Xbox")
            notVisibleForRoles("PC", "PlayStation")
        }
        transitDefaults {
            secondsBeforeMoveToOutput = 8
        }
        outputDefaults {
            outputCategoryName = "Xbox Vocaux Duo"
            maxUsers = 2
            tempChannelNamingStrategy = RandomizedFrakturedNames
        }
        funnel(entryChannelName = "Xbox Sans MMR Duo") {
            transit(transitCategoryName = "Xbox Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch sans MMR")
            }
        }
        funnel(entryChannelName = "Xbox MMR 3-4 Duo") {
            transit(transitCategoryName = "Xbox Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 3-4")
            }
        }
        funnel(entryChannelName = "Xbox MMR 4-5 Duo") {
            transit(transitCategoryName = "Xbox Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 4-5")
            }
        }
        funnel(entryChannelName = "Xbox MMR 5-6 Duo") {
            transit(transitCategoryName = "Xbox Rch Duo") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 5-6")
            }
        }
    }
    funnels(entryCategoryName = "Xbox Creation rch Trio") {
        funnelDefaults {
            disableBlacklist = true
            disableFillUp = true
            noTextForRoles("Mute")
            noTextNoVoiceForRoles("Super Mute")
            visibleForRoles("Xbox")
            notVisibleForRoles("PC", "PlayStation")
        }
        transitDefaults {
            secondsBeforeMoveToOutput = 8
        }
        outputDefaults {
            outputCategoryName = "Xbox Vocaux Trio"
            maxUsers = 3
            tempChannelNamingStrategy = RandomizedFrakturedNames
        }
        funnel(entryChannelName = "Xbox Sans MMR Trio") {
            transit(transitCategoryName = "Xbox Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch sans MMR")
            }
        }
        funnel(entryChannelName = "Xbox MMR 3-4 Trio") {
            transit(transitCategoryName = "Xbox Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 3-4")
            }
        }
        funnel(entryChannelName = "Xbox MMR 4-5 Trio") {
            transit(transitCategoryName = "Xbox Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 4-5")
            }
        }
        funnel(entryChannelName = "Xbox MMR 5-6 Trio") {
            transit(transitCategoryName = "Xbox Rch Trio") {
                transitChannelNamingStrategy = SimpleName("Rch MMR 5-6")
            }
        }
    }
}
