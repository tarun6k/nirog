package com.nirog.app

/**
 * Offline intent router for voice Q&A: maps a Hindi transcript to a topic the
 * app can answer from its OWN data (diary, PHI, outbreak radar) or to the scan
 * flow. Deliberately no generative answers — a wrong agronomy answer is the
 * same risk class as a wrong pesticide, so unknown questions route to the
 * camera or the crop doctor, never to a guess.
 */
enum class QaTopic { HARVEST, SPEND, NEARBY, SCAN }

// ponytail: keyword matching, not NLU. Upgrade path is Bhashini/ULCA intent
// models behind the same function signature once credentials exist.
private val HARVEST_WORDS = listOf("कटाई", "काटू", "काट सकत", "कब काट", "हार्वेस्ट", "मंडी कब")
private val SPEND_WORDS = listOf("खर्च", "लागत", "पैसा", "पैसे", "रुपय", "रुपए", "कितना लगा")
private val NEARBY_WORDS = listOf("आस-पास", "आसपास", "आस पास", "पड़ोस", "गांव में", "इलाक", "फैल रहा", "फैला")

fun routeQuestion(transcript: String): QaTopic {
    val t = transcript.trim()
    return when {
        HARVEST_WORDS.any { t.contains(it) } -> QaTopic.HARVEST
        SPEND_WORDS.any { t.contains(it) } -> QaTopic.SPEND
        NEARBY_WORDS.any { t.contains(it) } -> QaTopic.NEARBY
        // symptoms, diseases, "which medicine" — the photo is the honest answer
        else -> QaTopic.SCAN
    }
}
