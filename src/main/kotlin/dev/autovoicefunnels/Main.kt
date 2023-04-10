package dev.autovoicefunnels

import dev.autovoicefunnels.dsl.autoVoiceFunnels
import dev.autovoicefunnels.models.RandomizedFrakturedNames
import dev.autovoicefunnels.models.SimpleName
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    logger.error(throwable) { "Whoopsie ${throwable.message}" }
}

private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

private val CLEANUP_ACTION = "cleanup"

suspend fun main(args: Array<String>) {
    var isCleanup = false
    if (args.isNotEmpty()) {
        when (args[0]) {
            CLEANUP_ACTION -> isCleanup = true
        }
    }
    while (true) {
        coroutineScope.launch {
            //autoVoiceFunnels {
            //    funnels(entryCategoryName = "Creation rch Duo") {
            //        funnelDefaults {
            //            disableBlacklist = true
            //            disableFillUp = true
            //            noTextForRoles("Mute")
            //            noTextNoVoiceForRoles("Super Mute")
            //            visibleForRoles("PC")
            //            notVisibleForRoles("Xbox", "PlayStation")
            //        }
            //        transitDefaults {
            //            secondsBeforeMoveToOutput = 8
            //        }
            //        outputDefaults {
            //            outputCategoryName = "Vocaux Duo"
            //            maxUsers = 3
            //            tempChannelNamingStrategy = RandomizedFrakturedNames
            //        }
            //        funnel(entryChannelName = "Sans MMR Duo") {
            //            transit(transitCategoryName = "Rch Duo") {
            //                transitChannelNamingStrategy = SimpleName("Rch sans MMR")
            //            }
            //        }
            //        funnel(entryChannelName = "MMR 3-4 Duo") {
            //            transit(transitCategoryName = "Rch Duo") {
            //                transitChannelNamingStrategy = SimpleName("Rch MMR 3-4")
            //            }
            //        }
            //    }
            //}.start()
            huntFrBotDsl().start(isCleanup)
            //testDsl().start()
        }.join()
        logger.warn { "Delay 10s before try starting again" }
        delay(10000)
    }
}

