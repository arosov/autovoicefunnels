package dev.autohunt

import dev.autohunt.dsl.autoHunt
import dev.autohunt.models.NumberedWithScheme
import dev.autohunt.models.RandomizedFrakturedNames
import dev.autohunt.readConfig
import dev.autohunt.writeConfig
import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    logger.error(throwable) { "Whoopsie ${throwable.message}" }
}

private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

suspend fun main() {
    val bot = autoHunt {
        // 1 entry channel, 1 transit zone, 1 output but user limit for moving out of transit is 3
        funnels(entryCategoryName = "funnel group I") {
            funnel(entryChannelName = "entry channel I") {
                transit(transitCategoryName = "transit testing I") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit I #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output testing I") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
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
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
                }
            }
        }
        // 1 entry channel with 1 output
        funnels(entryCategoryName = "funnel group III") {
            funnel(entryChannelName = "entry channel III") {
                output(outputCategoryName = "output testing III") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
                }
            }
        }
        // 2 entry channels with no transit zone but sharing the same output
        funnels(entryCategoryName = "funnel group IV") {
            funnel(entryChannelName = "entry channel IV 1") {
                output(outputCategoryName = "output testing IV 1") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
                }
            }
            funnel(entryChannelName = "entry channel IV 2") {
                output(outputCategoryName = "output testing IV 1") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
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
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
                }
            }
            funnel(entryChannelName = "entry channel V 2") {
                output(outputCategoryName = "output testing V 2") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
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
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
                }
            }
            funnel(entryChannelName = "entry channel VI 2") {
                transit(transitCategoryName = "transit testing VI 2") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit VI 2 #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output testing VI 2") {
                    maxUsers = 1
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
                }
            }
        }

        // try to minimize funnel declaration, for many funnels with similar properties
        funnels(entryCategoryName = "Full defaults") {
            funnelDefaults {
                disableBlacklist = false
                disableFillUp = false
            }
            transitDefaults {
                transitCategoryName = "defaults transit trio"
                transitChannelNamingStrategy = NumberedWithScheme("default transit channel naming #%%")
                secondsBeforeMoveToOutput = 5
            }
            outputDefaults {
                outputCategoryName = "defaultoutput"
                maxUsers = 7
                tempChannelNamingStrategy = RandomizedFrakturedNames()
            }
            funnel(entryChannelName = "full defaults entry")
        }

        // Nothing set as defaults, for funnels with distinct characteristics
        funnels(entryCategoryName = "Full spec") {
            funnel(entryChannelName = "Full spec entry") {
                disableBlacklist = false
                disableFillUp = false
                tag = "idForAggregation"
                transit(transitCategoryName = "transit full specs") {
                    transitChannelNamingStrategy = NumberedWithScheme("Transit specs #%%")
                    secondsBeforeMoveToOutput = 8
                }
                output(outputCategoryName = "output specs") {
                    maxUsers = 3
                    tempChannelNamingStrategy = RandomizedFrakturedNames()
                }
            }
        }
    }

    while (true) {
        coroutineScope.launch {
            bot.start()
        }.join()
    }
}