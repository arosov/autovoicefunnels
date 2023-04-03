package dev.autovoicefunnels

import dev.autovoicefunnels.dsl.autoVoiceFunnels
import dev.autovoicefunnels.models.NumberedWithScheme
import dev.autovoicefunnels.models.RandomizedFrakturedNames
import dev.autovoicefunnels.models.SimpleName


fun testDsl() = autoVoiceFunnels {
        // 1 entry channel, 1 transit zone, 1 output but user limit for moving out of transit is 3
        funnels(entryCategoryName = "funnel group I") {
            funnel(entryChannelName = "entry channel I") {
                transit(transitCategoryName = "transit testing I") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit I #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output testing I") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }
        // 1 entry channel, 1 transit zone, 1 output but user limit for moving out of transit is 1
        funnels(entryCategoryName = "funnel group II") {
            funnel(entryChannelName = "entry channel II") {
                transit(transitCategoryName = "transit testing II") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit II #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output testing II") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }
        // 1 entry channel with 1 output
        funnels(entryCategoryName = "funnel group III") {
            funnel(entryChannelName = "entry channel III") {
                output(outputCategoryName = "output testing III") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }
        // 2 entry channels with no transit zone but sharing the same output
        funnels(entryCategoryName = "funnel group IV") {
            funnel(entryChannelName = "entry channel IV 1") {
                output(outputCategoryName = "output testing IV 1") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
            funnel(entryChannelName = "entry channel IV 2") {
                output(outputCategoryName = "output testing IV 1") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }
        // 2 entry channels going to the same transit zone but each have their own output
        funnels(entryCategoryName = "funnel group V") {
            transitDefaults {
                transitCategoryName = "transit testing V"
                secondsBeforeMoveToOutput = 8
                transitChannelNamingStrategy = NumberedWithScheme("Transit V #%%")
            }
            funnel(entryChannelName = "entry channel V 1") {
                output(outputCategoryName = "output testing V 1") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
            funnel(entryChannelName = "entry channel V 2") {
                output(outputCategoryName = "output testing V 2") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }
        // 2 entry channels, 2 transit zones, 2 outputs
        funnels(entryCategoryName = "funnel entry VI") {
            funnel(entryChannelName = "entry channel VI 1") {
                transit(transitCategoryName = "transit testing VI 1") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit VI 1 #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output testing VI 1") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
            funnel(entryChannelName = "entry channel VI 2") {
                transit(transitCategoryName = "transit testing VI 2") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit VI 2 #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output testing VI 2") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }

        // Visibility test
        funnels(entryCategoryName = "testing visibility") {
            funnelDefaults {
                visibleForRoles("testingvisibility")
            }
            funnel(entryChannelName = "visibility entry") {
                disableBlacklist = true
                disableFillUp = true
                tag = "idForAggregation"
                transit(transitCategoryName = "transit visibility") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit visibility #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output visibility") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }

        funnels(entryCategoryName = "PS testing") {
            funnelDefaults {
                visibleForRoles("PlayStation")
                notVisibleForRoles("Xbox")
                noTextForRoles("Mute")
                noTextNoVoiceForRoles("Super Mute")
            }
            funnel(entryChannelName = "PS entry") {
                disableBlacklist = true
                disableFillUp = true
                tag = "idForAggregation"
                transit(transitCategoryName = "PS transit") {
                    transitChannelNamingStrategy = NumberedWithScheme("PS Transit visibility #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "PS output") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }
        funnels(entryCategoryName = "Xboite testing") {
            funnelDefaults {
                visibleForRoles("Xbox")
                notVisibleForRoles("PlayStation")
                noTextForRoles("Mute")
                noTextNoVoiceForRoles("Super Mute")
            }
            funnel(entryChannelName = "Xboite entry") {
                disableBlacklist = true
                disableFillUp = true
                tag = "idForAggregation"
                transit(transitCategoryName = "Xboite transit") {
                    transitChannelNamingStrategy = NumberedWithScheme("Xboite Transit visibility #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "Xboite output") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }

        // try to minimize funnel declaration, for many funnels with similar properties
        funnels(entryCategoryName = "Full defaults") {
            funnelDefaults {
                disableBlacklist = true
                disableFillUp = true
                //visibleForRoles("PlayStation")
                //notVisibleForRoles("Xbox")
                //noTextForRoles("Mute")
                //noTextNoVoiceForRoles("Super Mute")
            }
            transitDefaults {
                transitCategoryName = "defaults transit trio"
                transitChannelNamingStrategy = NumberedWithScheme("default transit channel naming #%%")
                secondsBeforeMoveToOutput = 5
            }
            outputDefaults {
                outputCategoryName = "defaultoutput"
                maxUsers = 7
                tempChannelNamingStrategy = RandomizedFrakturedNames
            }
            funnel(entryChannelName = "full defaults entry")
        }

        // Nothing set as defaults, for funnels with distinct characteristics
        funnels(entryCategoryName = "Full spec") {
            funnel(entryChannelName = "Full spec entry") {
                disableBlacklist = true
                disableFillUp = true
                tag = "idForAggregation"
                transit(transitCategoryName = "transit full specs") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit specs #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output specs") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames
                }
            }
        }
    }
