# AutoVoiceFunnels

Discord bot to create temporary voice channels organized in categories with
optional transit category.

Intended for usage by Hunt FR Discord.

## Setup for hosting

You'll need to provide a Discord Bot Token to build this bot.
Insert the token in `gradle.properties` file and replace the default dummy value.

To get a Bot Token, create a Discord app https://discord.com/developers/applications

## Build from gradle

`./gradlew uberjar` will generate a jar with `uber` in the filename under `build/libs/` suitable for hosting purposes.

`./gradlew run` will run the bot locally for test purposes.

## Permissions and bot install link

Under `https://discord.com/developers/applications/` select your bot then go to
*OAuth2 -> URL Generator*

Check *Bot* in the `Scopes` category.

In the `Bot Permissions` category, check:
* Manage Channels
* Move members

The resulting link can be used to add the bot to any server you own.

