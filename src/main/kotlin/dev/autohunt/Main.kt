
import dev.autohunt.dsl.autoHunt
import dev.autohunt.models.RandomizedFrakturedNames
import dev.autohunt.models.UsernameBased
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    logger.error(throwable) { "Whoopsie ${throwable.message}" }
}

private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

suspend fun main() {
    val bot = autoHunt {
        funnels(entryCategoryName = "Rch Hunter TRIO") {
            funnel(id = "t_mmr*") {
                entryChannelName = "Remplissage TRIO"
                outputFromFunnels("t_mmr3-4", "t_mmr4-5", "t_mmr5-6")
            }
            funnel(id = "t_mmr3-4") {
                entryChannelName = "Rch MMR 3-4 TRIO"
                output(outputCategoryName = "TRIO MMR 3-4") {
                    tempChannelNameStrategy = RandomizedFrakturedNames()
                    maxUsers = 3
                }
            }
            funnel(id = "t_mmr4-5") {
                entryChannelName = "Rch MMR 4-5 TRIO"
                output(outputCategoryName = "TRIO MMR 4-5") {
                    tempChannelNameStrategy = RandomizedFrakturedNames()
                    maxUsers = 3
                }
            }
            funnel(id = "t_mmr5-6") {
                entryChannelName = "Rch MMR 5-6 TRIO"
                output(outputCategoryName = "TRIO MMR 5-6") {
                    tempChannelNameStrategy = RandomizedFrakturedNames()
                    maxUsers = 3
                }
            }
            funnel(id = "t_custom") {
                entryChannelName = "Custom TRIO"
                disableBlacklist = true
                disableFillUp = true
                output(outputCategoryName = "Custom TRIOs") {
                    tempChannelNameStrategy = UsernameBased()
                    maxUsers = 8
                }
            }
        }
        funnels(entryCategoryName = "Rch Hunter DUO") {
            funnel(id = "d_mmr*") {
                entryChannelName = "Remplissage DUO"
                outputFromFunnels("d_mmr3-4", "d_mmr4-5", "d_mmr5-6")
            }
            funnel(id = "d_mmr3-4") {
                entryChannelName = "Rch MMR 3-4 DUO"
                output(outputCategoryName = "DUO MMR 3-4") {
                    tempChannelNameStrategy = RandomizedFrakturedNames()
                    maxUsers = 2
                }
            }
            funnel(id = "d_mmr4-5") {
                entryChannelName = "Rch MMR 4-5 DUO"
                output(outputCategoryName = "DUO MMR 4-5") {
                    tempChannelNameStrategy = RandomizedFrakturedNames()
                    maxUsers = 2
                }
            }
            funnel(id = "d_mmr5-6") {
                entryChannelName = "Rch MMR 5-6 DUO"
                output(outputCategoryName = "DUO MMR 5-6") {
                    tempChannelNameStrategy = RandomizedFrakturedNames()
                    maxUsers = 2
                }
            }
            funnel(id = "d_custom") {
                entryChannelName = "Custom DUO"
                disableBlacklist = true
                disableFillUp = true
                output(outputCategoryName = "Custom DUOs") {
                    tempChannelNameStrategy = UsernameBased()
                    maxUsers = 8
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