package dev.autovoicefunnels

import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

private val frakturMap = mapOf(
    'a' to "𝔞",
    'b' to "𝔟",
    'c' to "𝔠",
    'd' to "𝔡",
    'e' to "𝔢",
    'f' to "𝔣",
    'g' to "𝔤",
    'h' to "𝔥",
    'i' to "𝔦",
    'j' to "𝔧",
    'k' to "𝔨",
    'l' to "𝔩",
    'm' to "𝔪",
    'n' to "𝔫",
    'o' to "𝔬",
    'p' to "𝔭",
    'q' to "𝔮",
    'r' to "𝔯",
    's' to "𝔰",
    't' to "𝔱",
    'u' to "𝔲",
    'v' to "𝔳",
    'w' to "𝔴",
    'x' to "𝔵",
    'y' to "𝔶",
    'z' to "𝔷",
    'A' to "𝔄",
    'B' to "𝔅",
    'C' to "ℭ",
    'D' to "𝔇",
    'E' to "𝔈",
    'F' to "𝔉",
    'G' to "𝔊",
    'H' to "ℌ",
    'I' to "ℑ",
    'J' to "𝔍",
    'K' to "𝔎",
    'L' to "𝔏",
    'M' to "𝔐",
    'N' to "𝔑",
    'O' to "𝔒",
    'P' to "𝔓",
    'Q' to "𝔔",
    'R' to "ℜ",
    'S' to "𝔖",
    'T' to "𝔗",
    'U' to "𝔘",
    'V' to "𝔙",
    'W' to "𝔚",
    'X' to "𝔛",
    'Y' to "𝔜",
    'Z' to "ℨ",
    '0' to "𝟘",
    '1' to "𝟙",
    '2' to "𝟚",
    '3' to "𝟛",
    '4' to "𝟜",
    '5' to "𝟝",
    '6' to "𝟞",
    '7' to "𝟟",
    '8' to "𝟠",
    '9' to "𝟡"
)
internal val statesOfTheUSofA: List<String> = listOf(
    "Alabama",
    "Alaska",
    "Arizona",
    "Arkansas",
    "California",
    "Colorado",
    "Connecticut",
    "Delaware",
    "Florida",
    "Georgia",
    "Hawaii",
    "Idaho",
    "Illinois",
    "Indiana",
    "Iowa",
    "Kansas",
    "Kentucky",
    "Louisiana",
    "Maine",
    "Maryland",
    "Massachusetts",
    "Michigan",
    "Minnesota",
    "Mississippi",
    "Missouri",
    "Montana",
    "Nebraska",
    "Nevada",
    "New Hampshire",
    "New Jersey",
    "New Mexico",
    "New York",
    "North Carolina",
    "North Dakota",
    "Ohio",
    "Oklahoma",
    "Oregon",
    "Pennsylvania",
    "Rhode Island",
    "South Carolina",
    "South Dakota",
    "Tennessee",
    "Texas",
    "Utah",
    "Vermont",
    "Virginia",
    "Washington",
    "West Virginia",
    "Wisconsin",
    "Wyoming"
)

private fun fraktur(state: String): String {
    val sb = StringBuilder()
    state.forEach { char ->
        sb.append(
            frakturMap[char] ?: char
        )
    }
    return sb.toString()
}

private val fileNameChannelsNames = "channel_names.txt"

private fun initializeChannelNamesFile() {
    val fileChanNames = File(fileNameChannelsNames)
    if (fileChanNames.exists()) return
    fileChanNames.printWriter().use {
        out -> statesOfTheUSofA.forEach { state -> out.println(state) }
    }
}

val frakturedChannelNames: List<String> = run {
    initializeChannelNamesFile()
    loadChannelNamesFile().map { name -> fraktur(name) }
}

fun loadChannelNamesFile(): List<String> {
    return mutableListOf<String>().apply {
        val fileChanNames = File(fileNameChannelsNames)
        fileChanNames.reader().use {
            reader -> addAll(reader.readLines())
        }
    }
}
