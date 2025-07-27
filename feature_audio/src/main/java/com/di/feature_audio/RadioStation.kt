// feature_audio/src/main/java/com/di/feature_audio/RadioStation.kt

package com.di.feature_audio

data class RadioStation(
    val id: String,          // unique key, e.g. "rnz_national"
    val name: String,        // display name, e.g. "RNZ National"
    val stream: String,      // direct .m3u8 / .mp3 / .aac URL
    val description: String  // short tagline shown in the UI
)

val presetStations = listOf(
    // ── Spoken‑word · NZ ──
    RadioStation(
        id = "rnz_national",
        name = "RNZ National",
        stream = "https://stream-ice.radionz.co.nz/national.mp3",
        description = "New Zealand’s public‑service network for news, current affairs and culture."
    ),

    // ── Global news ──
    RadioStation(
        id = "bbc_world_service",
        name = "BBC World Service",
        // Preferred HLS feed (96 kb/s AAC)
        stream = "https://as-hls-ww-live.akamaized.net/pool_87948813/live/ww/bbc_world_service/bbc_world_service.isml/bbc_world_service-audio=96000.norewind.m3u8",
        description = "The BBC’s 24‑hour worldwide news and analysis service."
    ),

    // ── US news / politics ──
    RadioStation(
        id = "npr_program",
        name = "NPR 24‑Hour Program Stream",
        stream = "https://npr-ice.streamguys1.com/live.mp3",
        description = "Round‑the‑clock U.S. and global news from NPR’s flagship shows."
    ),

    // ── Global affairs + eclectic music ──
    RadioStation(
        id = "monocle_24",
        name = "Monocle 24",
        stream = "https://playerservices.streamtheworld.com/api/livestream-redirect/MONOCLE_24AAC.aac",
        description = "Stylish global‑affairs talk blended with hand‑picked music from Monocle."
    ),

    // ── Jazz ──
    RadioStation(
        id = "jazz24",
        name = "Jazz24",
        stream = "https://live.amperwave.net/direct/ppm-jazz24mp3-ibc1",
        description = "Commercial‑free jazz classics and new discoveries curated by KNKX Seattle."
    ),

    // ── Up‑beat eclectic music ──
    RadioStation(
        id = "radio_paradise",
        name = "Radio Paradise (Main Mix)",
        stream = "https://stream.radioparadise.com/aac-320",
        description = "Human‑programmed, ad‑free mix of rock, pop, world and electronica."
    ),

    // ── Australia · news & ideas ──
    RadioStation(
        id = "abc_radio_national",
        name = "ABC Radio National",
        stream = "https://mediaserviceslive.akamaized.net/hls/live/2038318/rnnsw/index.m3u8",
        description = "Australia’s premier talk network for national and international news, science and ideas."
    ),

    // ── New York · US politics ──
    RadioStation(
        id = "wnyc_fm",
        name = "WNYC 93.9 FM",
        stream = "https://fm939.wnyc.org/wnycfm",
        description = "New York’s leading public station delivering sharp U.S. politics, culture and call‑in shows."
    ),

    // ── Chill‑out / exercise cool‑down ──
    RadioStation(
        id = "somafm_groove_salad",
        name = "SomaFM – Groove Salad",
        stream = "https://ice1.somafm.com/groovesalad-128-mp3",
        description = "Laid‑back downtempo and ambient beats from listener‑supported SomaFM."
    )
)
