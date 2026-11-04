package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/** Permanent channel IDs keep suggestions valid when a channel renames its handle. */
data class VideoChannel(val channelId: String, val title: String)
data class ChannelVideo(val videoId: String, val title: String, val published: String, val thumbnail: String)

object VideoCatalog {
    fun channels(lang: LanguageSpec): List<VideoChannel> = when (lang.code) {
        Languages.FRENCH.code -> frenchChannels
        Languages.ENGLISH.code -> englishChannels
        else -> emptyList()
    }
    private val englishChannels = listOf(
        VideoChannel("UCHaHD477h-FeBbVh9Sh7syA", "BBC Learning English"),
        VideoChannel("UCKyTokYo0nK2OA-az-sDijA", "VOA Learning English"),
        VideoChannel("UCz4tgANd4yy8Oe0iXCdSWfA", "English with Lucy"),
        VideoChannel("UCsooa4yRKGN_zEE8iknghZA", "TED-Ed"),
        VideoChannel("UCTRHegh7UqWuKRymXoqzbzA", "Easy English"),
        VideoChannel("UCvn_XCl_mgQmt3sD753zdJA", "Rachel's English"),
    )
}

/** Small Atom reader: only direct entry title/published fields are used (not media metadata). */
fun parseYoutubeFeed(xml: String): List<ChannelVideo> {
    require(Regex("<(?:[\\w-]+:)?feed(?:\\s|/?>)").containsMatchIn(xml) && !Regex("<!DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "Invalid YouTube feed" }
    fun value(text: String, name: String): String {
        val raw = Regex("<$name(?:\\s[^>]*)?>([\\s\\S]*?)</$name\\s*>").find(text)?.groupValues?.get(1).orEmpty()
        return decodeSubtitleEntities(raw.removePrefix("<![CDATA[").removeSuffix("]]>").trim())
    }
    return Regex("<(?:[\\w-]+:)?entry(?:\\s[^>]*)?>([\\s\\S]*?)</(?:[\\w-]+:)?entry\\s*>").findAll(xml).mapNotNull { match ->
        val entry = match.groupValues[1]
        val id = value(entry, "(?:[\\w-]+:)?videoId")
        if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return@mapNotNull null
        val withoutMedia = entry.replace(Regex("<media:group(?:\\s[^>]*)?>[\\s\\S]*?</media:group>"), "")
        val thumbnail = Regex("<(?:[\\w-]+:)?thumbnail\\b[^>]*\\burl\\s*=\\s*['\"]([^'\"]+)['\"]").find(entry)?.groupValues?.get(1)?.let(::decodeSubtitleEntities)
        ChannelVideo(id, value(withoutMedia, "(?:atom:)?title"), value(withoutMedia, "(?:atom:)?published"), thumbnail ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg")
    }.distinctBy { it.videoId }.toList()
}

suspend fun fetchChannelVideos(http: HttpClient, channelId: String): List<ChannelVideo> {
    require(Regex("UC[A-Za-z0-9_-]{22}").matches(channelId))
    val response = http.get("https://www.youtube.com/feeds/videos.xml") { url { parameters.append("channel_id", channelId) } }
    check(response.status.value in 200..299) { "HTTP ${response.status.value}" }
    return parseYoutubeFeed(response.bodyAsText())
}

private val frenchChannels = listOf(
    VideoChannel("UCI4xp8qHD1MDErkqxb1dPbA", "innerFrench – YouTube"),
    VideoChannel("UClEGQZlQURxTiMdwBPqiKDQ", "Français avec Nelly"),
    VideoChannel("UCQpM25U6iqaRSO-SZxd5oDw", "Français Authentique – YouTube"),
    VideoChannel("UC-XUpEBvcQcRqMdtLhoXmOA", "French Comprehensible Input"),
    VideoChannel("UCbC9Utt1c6UXRqWw2lOTCBg", "Alice Ayel"),
    VideoChannel("UCftkAUwCOQM_FhQ4216qFhA", "Français avec Fluidité"),
    VideoChannel("UC5E3cis5nJyfuiU5_bzvIwg", "Parlez-vous FRENCH"),
    VideoChannel("UC59NqFOvritu4Rw93tQT4LQ", "Madame à Paname"),
    VideoChannel("UC_XYAAXeuD_3JPDJ-QSv1jg", "Ma prof de français"),
    VideoChannel("UCb3u5vctZOjajpr39xy2jMA", "French School TV"),
    VideoChannel("UC1ipKNAWcHhmcqKUEGg1RMg", "Le Monde des Langues"),
    VideoChannel("UCNw6J4vA8lLExo46eqBo4kg", "Carlito – Learn French Through Culture"),
    VideoChannel("UCCD-nJGAxKmh1GnSuUx8Gig", "Guillaume Posé – Professeur de français"),
    VideoChannel("UCXJhsHwX3IeKrP6iS8KnboQ", "Le French Club"),
    VideoChannel("UCEuI3o4ZGS0W-aQU_T9vB5A", "Français avec Fred"),
    VideoChannel("UCIUS_2a-HFSHmV_QVVHWp9A", "Le français by Alex"),
    VideoChannel("UCSpvTtFPjWhSHiOlH5FyVzA", "Ohlala French Course"),
    VideoChannel("UCAcAnMF0OrCtUep3Y4M-ZPw", "HugoDécrypte – Actus du jour"),
    VideoChannel("UCUo1RqYV8tGjV38sQ8S5p9A", "HugoDécrypte – Grands formats"),
    VideoChannel("UCYpRDnhk5H8h16jpS84uqsA", "Le Monde – YouTube"),
    VideoChannel("UCSKdvgqdnj72_SLggp7BDTg", "Brut"),
    VideoChannel("UCCCPCZNChQdGa9EkATeye4g", "France 24"),
    VideoChannel("UCzKH70qfN_yuXq3s91fdwmg", "TV5MONDE Info"),
    VideoChannel("UCO6K_kkdP-lnSCiO3tPx7WA", "franceinfo – YouTube"),
    VideoChannel("UCW2QcKZiU8aUGg4yxCIditg", "euronews (en français)"),
    VideoChannel("UCckz6n8QccTd6K_xdwKqa0A", "AFP"),
    VideoChannel("UClxaaAzHu1B5EoTtBYea7ig", "Radio-Canada Info"),
    VideoChannel("UC8EzKGkEiusTm7g0mTfkWkg", "28 minutes – ARTE"),
    VideoChannel("UCTa1bPArjpvNMgSHAb_pflA", "Public Sénat"),
    VideoChannel("UCfHn_8-ehdem86fEvlFg-Gw", "Le Parisien"),
    VideoChannel("UCCDz_XYeKWd0OIyjp95dqyQ", "Le Figaro"),
    VideoChannel("UCsrPUA0ZSDCNZC6wyRlR7ZA", "TF1 INFO"),
    VideoChannel("UCXwDLMDV86ldKoFVc_g8P0g", "BFMTV"),
    VideoChannel("UCwI-JbGNsojunnHbFAc0M4Q", "ARTE"),
    VideoChannel("UCHGMBrXUzClgjEzBMei-Jdw", "Le Dessous des Cartes – ARTE"),
    VideoChannel("UCh5JF-qt2ZXg8acejcJ15Xw", "Karambolage – ARTE"),
    VideoChannel("UCP46_MXP_WG_auH88FnfS1A", "Nota Bene"),
    VideoChannel("UC2_OG1L8DLTzQ7UrZVOk7OA", "Axolot"),
    VideoChannel("UCd5DKToXYTKAQ6khzewww2g", "France Culture"),
    VideoChannel("UCzMHLUJ8xTqDq6xsk0N5IdQ", "Tout Simplement – Kurzgesagt"),
    VideoChannel("UCG9G2dyRv04FDSH1FSYuLBg", "Jamy – Epicurieux"),
    VideoChannel("UCaNlbnghtwlsGF-KzAFThqA", "ScienceEtonnante"),
    VideoChannel("UCcziTK2NKeWtWQ6kB5tmQ8Q", "e-penser 2.0"),
    VideoChannel("UCtqICqGbPSbTN09K1_7VZ3Q", "DirtyBiology"),
    VideoChannel("UCKjDY4joMPcoRMmd-G1yz1Q", "C'est une autre histoire"),
    VideoChannel("UCN4TCCaX-gqBNkrUqXdgGRA", "L'Histoire nous le dira"),
    VideoChannel("UCvRgiAmogg7a_BgQ_Ftm6fA", "Le Précepteur"),
    VideoChannel("UCyJDHgrsUKuWLe05GvC2lng", "Stupid Economics"),
    VideoChannel("UCofQxJWd4qkqc7ZgaLkZfcw", "Linguisticae"),
    VideoChannel("UCWnfDPdZw6A23UtuBpYBbAg", "Dr Nozman"),
    VideoChannel("UCjxfoYq1vAEGU7KD_C3I48g", "Mamytwink"),
    VideoChannel("UCSdJiWi0tq9CAx1bizne6mA", "Bruno Maltor"),
    VideoChannel("UCJ9tHJbCa8ZoMbapfJLy0ZQ", "imineo Documentaires"),
    VideoChannel("UCYFI5PGuw_34XaA3SGDkQOQ", "Documentaire Société"),
    VideoChannel("UCXptamDYEVcU4JCio30hYTw", "Peppa Pig Français"),
    VideoChannel("UCBwSqx6A83sNQ5SIYWpmucQ", "Caillou Français"),
    VideoChannel("UCjd32KVfRCli1d9iqo4YZ5A", "Masha et Michka"),
    VideoChannel("UC8I-UIlXPNS4luC4iV7dRdQ", "Titounis"),
    VideoChannel("UCIpBLlcyR1W6oEji4_qdrQA", "Miraculous – Ladybug et Chat Noir"),
    VideoChannel("UCVgW9ZQaGBk6fsiPgE2mYDg", "Français avec Pierre"),
    VideoChannel("UCFu6uJDqU_6vCIyN9KpMPLg", "Home Language"),
    VideoChannel("UCByWJsWPztkY3Rta2B62tgg", "Hugoposé"),
    VideoChannel("UCIMGfEAERXjmWwQeg15BFsg", "Europe 1"),
    VideoChannel("UCewhc0fvja891XkpIPGRMxQ", "LCI"),
    VideoChannel("UC9GGzAhhvhJO1hL10-BcgNA", "LeHuffPost"),
    VideoChannel("UCXKJrYczY2_fJEZgFPGY0HQ", "CNEWS"),
    VideoChannel("UCT67YOMntJxfRnO_9bXDpvw", "Le Média"),
    VideoChannel("UC__xRB5L4toU9yYawt_lIKg", "BLAST, Le souffle de l'info"),
    VideoChannel("UCar0yo-PpeMk51m116826pQ", "Journal l'Humanité"),
    VideoChannel("UC9hHeywcPBnLglqnQRaNShQ", "Le Fil d'Actu - Officiel"),
    VideoChannel("UCLmlUMA_bGiMWWgfDwfNDgw", "Info ou Mytho ?"),
    VideoChannel("UCnf0fDz1vTYW-sl36wbVMbg", "Les Revues du Monde"),
    VideoChannel("UCpi-4daExRSchtsJzO06rwA", "Quotidien"),
    VideoChannel("UC50uCdUubc04xXZz7bqv5ng", "Investigation"),
    VideoChannel("UC7DHcyYJpGiSO0NIE7w_Eug", "100% DOCS - Reportages & Documentaires"),
    VideoChannel("UC_majyzBb8HZV0wCmU4Z4Ag", "imineo"),
    VideoChannel("UClhucq0kKFA5tSmk3H7Yz6w", "Le Corps La Maison L'esprit"),
    VideoChannel("UChG8nxeVTk6jQJyH-_TG7xw", "InPower Podcast"),
    VideoChannel("UCQfakletOMk9zXVRSHUdyDA", "Solange te parle"),
    VideoChannel("UCTafEJoRl5myC8A50plIrng", "FabienOlicard"),
    VideoChannel("UCah8C0gmLkdtvsy0b2jrjrw", "Cyrus North"),
    VideoChannel("UCI-8QMy9n3I5Pv6mTmXoAXA", "Gaspard G"),
    VideoChannel("UCHQda5vLxrH0Ff0I0kMq4zw", "Konbini"),
    VideoChannel("UCmHfCD8c8CmjlyWa8d4GObg", "Clique TV"),
    VideoChannel("UCAdyNOE80FsFPYlFliyXfwQ", "France tv science & nature"),
    VideoChannel("UCbdwaLd91oU_tWcHJn9-hdg", "Gauthier Dm - Science"),
    VideoChannel("UCRhyS_ylPQ5GWBl1lK92ftA", "Léo - TechMaker"),
    VideoChannel("UCzyN_JSs95aDgDFrfOxagEw", "Food Story"),
    VideoChannel("UCKq9JxyISqBHDd-fXfV3QtQ", "FastGoodCuisine"),
    VideoChannel("UCBmGPY1gJ4XnXfek849BjTA", "La Folle Histoire"),
    VideoChannel("UCCGRtSqLfljpX9mzCYDsQIg", "Questions d'Histoire"),
    VideoChannel("UCeLnsoEsuM4rWzzCU9RIlPQ", "Notre Histoire"),
    VideoChannel("UCky8Z5AYEHA55-r2TFNC_KA", "L'étoile Noire"),
    VideoChannel("UC_yP2DpIgs5Y1uWC0T03Chw", "Joueur Du Grenier"),
    VideoChannel("UCCMxHHciWRBBouzk-PGzmtQ", "Bazar du Grenier"),
    VideoChannel("UCoBCHzefW1ZGnGCA_r6spdw", "Benoit Chevalier"),
    VideoChannel("UCC_r3spMB5i5QKgtdNVO0OQ", "Pierre Croce"),
    VideoChannel("UC4ii4_aeS8iOFzsHuhJTq2w", "Poisson Fécond"),
    VideoChannel("UCqfANd8RDXTcQddXMwwgUmA", "Louis-San"),
    VideoChannel("UCyWqModMQlbIo8274Wh_ZsQ", "Cyprien"),
    VideoChannel("UCww2zZWg4Cf5xcRKG-ThmXQ", "Norman"),
    VideoChannel("UCtihF1ZtlYVzoaj_bKLQZ-Q", "Natoo"),
    VideoChannel("UCWeg2Pkate69NFdBeuRFTAw", "SQUEEZIE"),
    VideoChannel("UCJruTcTs7Gn2Tk7YC-ENeHQ", "Golden Moustache (M6)"),
    VideoChannel("UCZ8kV8vuMdDLSerCIFfWnFQ", "Studio Bagel"),
    VideoChannel("UCoZoRz4-y6r87ptDp4Jk74g", "Palmashow"),
    VideoChannel("UCPHJfLg1VeNBsfzzTszpmmQ", "Montreux Comedy"),
    VideoChannel("UCMqzZ17aTG2hKj2mVl7U4MA", "Les Parodie Bros"),
    VideoChannel("UCvYqgo9dGBPVvaa4paVIhwQ", "Lou! La Série [Officiel]"),
    VideoChannel("UCv88958LRDfndKV_Y7XmAnA", "Les Kassos - CANAL+"),
    VideoChannel("UCl0u_YK8kwwZanklN6W-3tg", "Ça commence aujourd'hui - France Télévisions"),
    VideoChannel("UCeqsLJGWhZXEerY5JWvwLkg", "Le Monde à L'Envers"),
    VideoChannel("UCgvqvBoSHB1ctlyyhoHrGwQ", "Amixem"),
    VideoChannel("UC9pxNghOaqpW4FzW74_KS1Q", "Les P'tits z'Amis"),
    VideoChannel("UCdLdz4Jf5HDBrHv7_1acKiw", "Les Lapins Crétins Invasion"),
    VideoChannel("UCJ9JIVGOL07BqXp0CoG83fw", "Disney Junior FR"),
    VideoChannel("UCX2dIyMEQMWy3AQBlq1CYXQ", "TéléCrayon"),
    VideoChannel("UCH0HvBshlVE7gCoV6as51og", "Madame Récré FR"),
)
