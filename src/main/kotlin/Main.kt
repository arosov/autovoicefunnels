import dev.autohunt.AutoHunt.BuildConfig
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    logger.error(throwable) { "Whoopsie ${throwable.message}" }
}

private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

suspend fun main(args: Array<String>) {
    val bot = AutoHuntBot(BuildConfig.DISCORD_TOKEN)
    while(true) {
        coroutineScope.launch {
            bot.start()
        }.join()
    }
}