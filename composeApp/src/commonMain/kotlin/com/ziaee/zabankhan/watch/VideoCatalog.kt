package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.*

/** Permanent channel IDs keep suggestions valid when a channel renames its handle.
 * [description] is keyed by interface language ("en", "fr", "fa"); [level] is a CEFR range such as "A2–B1". */
data class VideoChannel(val channelId: String, val title: String, val level: String? = null, val description: Map<String, String> = emptyMap(), val category: String = "education", val personal: Boolean = false)
private fun d(en: String, fr: String, fa: String) = mapOf("en" to en, "fr" to fr, "fa" to fa)
data class ChannelVideo(val videoId: String, val title: String, val published: String, val thumbnail: String)

object VideoCatalog {
    fun channels(lang: LanguageSpec): List<VideoChannel> = when (lang.code) {
        Languages.FRENCH.code -> (frenchChannels + streamTvFilms).map { channel ->
            val title = channel.title.lowercase()
            val category = when {
                listOf("hugo", "monde", "brut", "france 24", "info", "bfmtv", "afp", "news", "actu", "cnews", "lc i").any(title::contains) -> "news"
                listOf("documentaire", "documentary", "reportage", "arte", "histoire", "documentaires").any(title::contains) -> "documentaries"
                listOf("série", "serie", "films", "film", "streamtvfilms", "peppa", "caillou", "masha", "disney", "miraculous").any(title::contains) -> "films"
                else -> "education"
            }
            channel.copy(category = category)
        }
        Languages.ENGLISH.code -> englishChannels
        else -> emptyList()
    }
    val categories = listOf("education", "films", "news", "documentaries", "other")
    private val streamTvFilms = listOf(VideoChannel("UCKqmnQFF2PgokPDdX-L2fwQ", "StreamTVFilms", null,
        d("French films and series.", "Films et séries en français.", "فیلم‌ها و سریال‌های فرانسوی."), "films"))
    private val englishChannels = listOf(
        VideoChannel("UCHaHD477h-FeBbVh9Sh7syA", "BBC Learning English", "A2–B2", d("Short lessons on grammar, vocabulary and pronunciation, plus English in the news.",
            "Courtes leçons de grammaire, de vocabulaire et de prononciation, et l'anglais de l'actualité.", "درس‌های کوتاه دستور، واژگان و تلفظ، همراه با انگلیسیِ اخبار.")),
        VideoChannel("UCKyTokYo0nK2OA-az-sDijA", "VOA Learning English", "A2–B1", d("News and stories read slowly with simple words, made for learners.",
            "Actualités et histoires lues lentement avec des mots simples, pour les apprenants.", "اخبار و داستان‌هایی که آرام و با واژه‌های ساده خوانده می‌شوند؛ برای زبان‌آموزان.")),
        VideoChannel("UCz4tgANd4yy8Oe0iXCdSWfA", "English with Lucy", "B1–C1", d("British English lessons on pronunciation, vocabulary and natural expressions.",
            "Leçons d'anglais britannique : prononciation, vocabulaire et expressions naturelles.", "درس‌های انگلیسی بریتانیایی دربارهٔ تلفظ، واژگان و اصطلاح‌های طبیعی.")),
        VideoChannel("UCsooa4yRKGN_zEE8iknghZA", "TED-Ed", "B2–C1", d("Animated lessons on science, history and ideas, in clear standard English.",
            "Leçons animées sur les sciences, l'histoire et les idées, en anglais clair et standard.", "درس‌های پویانمایی دربارهٔ علم، تاریخ و اندیشه‌ها، به انگلیسی روشن و معیار.")),
        VideoChannel("UCTRHegh7UqWuKRymXoqzbzA", "Easy English", "A2–B2", d("Street interviews with everyday English, subtitled in English.",
            "Interviews dans la rue en anglais de tous les jours, sous-titrées en anglais.", "مصاحبه‌های خیابانی با انگلیسی روزمره و زیرنویس انگلیسی.")),
        VideoChannel("UCvn_XCl_mgQmt3sD753zdJA", "Rachel's English", "B1–C1", d("American English pronunciation: sounds, rhythm and connected speech.",
            "Prononciation de l'anglais américain : sons, rythme et enchaînements.", "تلفظ انگلیسی آمریکایی: صداها، ریتم و پیوستگی گفتار.")),
    )
}

fun mergeVideoChannels(curated: List<VideoChannel>, personal: List<VideoChannel>): List<VideoChannel> =
    (curated + personal).distinctBy { it.channelId }

fun filterVideoChannelsByCategory(channels: List<VideoChannel>, category: String?): List<VideoChannel> =
    if (category == null) channels else channels.filter { it.category == category }

private const val YOUTUBE_CHANNEL_ID = "UC[A-Za-z0-9_-]{22}"

private data class YoutubeChannelAddress(val explicitId: String?, val fetchUrl: String)

private fun channelAddress(input: String): YoutubeChannelAddress? = runCatching {
    val value = input.trim()
    val candidate = when {
        value.startsWith("@") -> "https://www.youtube.com/$value"
        "://" in value -> value
        else -> "https://$value"
    }
    val url = io.ktor.http.Url(candidate)
    if (url.protocol.name !in listOf("http", "https") || url.host.lowercase() !in setOf("youtube.com", "www.youtube.com", "m.youtube.com")) return null
    if (!url.user.isNullOrEmpty() || !url.password.isNullOrEmpty()) return null
    if (url.port != url.protocol.defaultPort) return null
    val path = url.encodedPath.removePrefix("/").removeSuffix("/").split('/')
    val explicitId = if (path.size == 2 && path[0] == "channel" && Regex(YOUTUBE_CHANNEL_ID).matches(path[1])) path[1] else null
    val isHandle = path.size == 1 && runCatching {
        val handle = path[0].decodeURLPart()
        handle.startsWith('@') && handle.length in 4..31 && handle.drop(1).all { ch ->
            ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-' || ch.code > 127 && !ch.isWhitespace() && ch.code !in 0..31 && ch.code != 127
        }
    }.getOrDefault(false)
    if (explicitId == null && !isHandle) return null
    val canonicalHost = "www.youtube.com"
    val canonicalPath = if (explicitId != null) "/channel/$explicitId" else "/${path.single()}"
    YoutubeChannelAddress(explicitId, "https://$canonicalHost$canonicalPath")
}.getOrNull()

fun youtubeChannelId(input: String): String? = channelAddress(input)?.explicitId

fun validateYoutubeChannelUrl(input: String): Boolean = channelAddress(input) != null

/** Resolve both permanent IDs and handles from YouTube's canonical channel metadata. */
suspend fun resolveYoutubeChannel(http: HttpClient, input: String): VideoChannel {
    val address = channelAddress(input) ?: throw IllegalArgumentException("Invalid YouTube channel URL")
    val response = http.get(address.fetchUrl)
    require(response.status.value in 200..299) { "Could not resolve YouTube channel" }
    val root = parseYoutubeInitialData(response.bodyAsText())
    val metadata = root.jsonObject["metadata"]?.jsonObject?.get("channelMetadataRenderer")?.jsonObject
        ?: error("YouTube channel metadata not found")
    val resolvedId = metadata["externalId"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { Regex(YOUTUBE_CHANNEL_ID).matches(it) }
        ?: error("YouTube channel ID not found")
    require(address.explicitId == null || address.explicitId == resolvedId) { "YouTube channel ID does not match metadata" }
    val title = metadata["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: error("YouTube channel title not found")
    return VideoChannel(resolvedId, title)
}

/** Missing levels match All only; a range includes its intermediate CEFR levels. */
fun videoLevelOverlaps(level: String?, filter: String?): Boolean {
    if (filter == null) return true
    fun range(value: String): IntRange? {
        val levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
        val found = Regex("[ABC][12]").findAll(value.uppercase()).map { levels.indexOf(it.value) }.toList()
        if (found.isEmpty()) return null
        return found.min()..if (value.contains('+')) 5 else found.max()
    }
    val channel = level?.let(::range) ?: return false
    val selected = range(filter) ?: return false
    return channel.first <= selected.last && selected.first <= channel.last
}

/** Stable ranking preserves curated order within title matches and within secondary matches. */
fun searchVideoChannels(
    channels: List<VideoChannel>, query: String, level: String?, feeds: Map<String, List<ChannelVideo>>, lang: LanguageSpec,
): List<VideoChannel> {
    val search = normalizeForMatch(query.trim(), lang)
    return channels.filter { channel ->
        videoLevelOverlaps(channel.level, level) && (search.isBlank() ||
            (listOf(channel.title, channel.level.orEmpty()) + channel.description.values +
                feeds[channel.channelId].orEmpty().map { it.title })
                .any { normalizeForMatch(it, lang).contains(search) })
    }.sortedBy { if (search.isBlank() || normalizeForMatch(it.title, lang).contains(search)) 0 else 1 }
}

fun searchChannelVideos(videos: List<ChannelVideo>, query: String, lang: LanguageSpec): List<ChannelVideo> {
    val search = normalizeForMatch(query.trim(), lang)
    return videos.filter { search.isBlank() || normalizeForMatch(it.title, lang).contains(search) }
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

/** YouTube's public channel feed can return 404 while the channel page is still available. */
fun parseYoutubeChannelPage(html: String): List<ChannelVideo> {
    val initialData = parseYoutubeInitialData(html)
    val videos = mutableListOf<ChannelVideo>()
    fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    fun JsonObject?.displayText(): String? = this?.let { text("simpleText") ?: it["content"]?.jsonPrimitive?.contentOrNull
        ?: it["runs"]?.jsonArray?.joinToString("") { run -> run.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() } }
        ?.takeIf { it.isNotBlank() }
    fun collect(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                val renderer = element["videoRenderer"]?.jsonObject
                val lockup = element["lockupViewModel"]?.jsonObject
                val item = when {
                    renderer != null -> {
                        val id = renderer.text("videoId")
                        val title = renderer["title"]?.jsonObject.displayText()
                        val published = renderer["publishedTimeText"]?.jsonObject.displayText().orEmpty()
                        val thumbnail = renderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.text("url")
                        if (id != null && title != null) ChannelVideo(id, title, published, thumbnail.orEmpty()) else null
                    }
                    lockup?.text("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" -> {
                        val id = lockup.text("contentId")
                        val metadata = lockup["metadata"]?.jsonObject?.get("lockupMetadataViewModel")?.jsonObject
                        val title = metadata?.get("title")?.jsonObject.displayText()
                        val rows = metadata?.get("metadata")?.jsonObject?.get("contentMetadataViewModel")
                            ?.jsonObject?.get("metadataRows")?.jsonArray
                        val published = rows?.lastOrNull()?.jsonObject?.get("metadataParts")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("text")?.jsonObject.displayText().orEmpty()
                        val thumbnail = lockup["contentImage"]?.jsonObject?.get("thumbnailViewModel")
                            ?.jsonObject?.get("image")?.jsonObject?.get("sources")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.text("url")
                        if (id != null && title != null) ChannelVideo(id, title, published, thumbnail.orEmpty()) else null
                    }
                    else -> null
                }
                if (item != null && Regex("[A-Za-z0-9_-]{11}").matches(item.videoId)) videos += item
                else element.values.forEach(::collect)
            }
            is JsonArray -> element.forEach(::collect)
            else -> Unit
        }
    }
    collect(initialData)
    return videos.distinctBy { it.videoId }
}

/** Extract and parse the balanced ytInitialData object shared by video and channel metadata readers. */
private fun parseYoutubeInitialData(html: String): JsonElement {
    val marker = "ytInitialData = "
    val start = html.indexOf(marker)
    require(start >= 0) { "YouTube channel data not found" }
    val jsonStart = start + marker.length
    var depth = 0
    var inString = false
    var escaped = false
    var jsonEnd = -1
    for (index in jsonStart until html.length) {
        val char = html[index]
        if (inString) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') inString = false
        } else when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) { jsonEnd = index + 1; break }
            }
        }
    }
    require(jsonEnd > jsonStart) { "YouTube channel data is incomplete" }
    return Json.parseToJsonElement(html.substring(jsonStart, jsonEnd))
}

suspend fun fetchChannelVideos(http: HttpClient, channelId: String): List<ChannelVideo> {
    require(Regex("UC[A-Za-z0-9_-]{22}").matches(channelId))
    val feedVideos = try {
        val response = http.get("https://www.youtube.com/feeds/videos.xml") { url { parameters.append("channel_id", channelId) } }
        if (response.status.value in 200..299) parseYoutubeFeed(response.bodyAsText()) else emptyList()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
    if (feedVideos.isNotEmpty()) return feedVideos

    val response = http.get("https://www.youtube.com/channel/$channelId/videos")
    check(response.status.value in 200..299) { "HTTP ${response.status.value}" }
    val videos = parseYoutubeChannelPage(response.bodyAsText())
    check(videos.isNotEmpty()) { "No public videos found for this channel" }
    return videos
}

private val frenchChannels = listOf(
    VideoChannel("UCI4xp8qHD1MDErkqxb1dPbA", "innerFrench – YouTube", "B1–B2", d("Hugo talks in clear, natural French about culture, society and how to learn; made for intermediate learners.", "Hugo parle de culture, de société et d'apprentissage dans un français clair et naturel ; pour le niveau intermédiaire.", "هوگو با فرانسوی واضح و طبیعی دربارهٔ فرهنگ، جامعه و روش یادگیری حرف می‌زند؛ برای سطح متوسط.")),
    VideoChannel("UClEGQZlQURxTiMdwBPqiKDQ", "Français avec Nelly", "A2–B2", d("Everyday spoken French: expressions, pronunciation and the gap between textbook French and real-life French.", "Le français parlé du quotidien : expressions, prononciation et écart entre le français des manuels et celui de la vraie vie.", "فرانسوی گفتاری روزمره: اصطلاح‌ها، تلفظ و تفاوت فرانسوی کتاب‌ها با فرانسوی واقعی.")),
    VideoChannel("UCQpM25U6iqaRSO-SZxd5oDw", "Français Authentique – YouTube", "B1–C1", d("Johan explains expressions and shares learning advice, entirely in French.", "Johan explique des expressions et donne des conseils d'apprentissage, entièrement en français.", "یوهان اصطلاح‌ها را توضیح می‌دهد و نکته‌های یادگیری می‌گوید؛ تماماً به فرانسوی.")),
    VideoChannel("UC-XUpEBvcQcRqMdtLhoXmOA", "French Comprehensible Input", "A1–B1", d("Stories and topics told slowly with drawings and gestures, so beginners can follow without translation.", "Histoires et sujets racontés lentement, avec dessins et gestes, pour suivre sans traduction dès le niveau débutant.", "داستان‌ها و موضوع‌هایی که آرام و با نقاشی و حرکت دست تعریف می‌شوند تا مبتدی‌ها بدون ترجمه بفهمند.")),
    VideoChannel("UCbC9Utt1c6UXRqWw2lOTCBg", "Alice Ayel", "A1–B1", d("French taught through stories that Alice tells and draws, from complete beginner upwards.", "Le français par des histoires qu'Alice raconte et dessine, à partir du niveau grand débutant.", "یادگیری فرانسه با داستان‌هایی که آلیس تعریف و نقاشی می‌کند؛ از سطح کاملاً مبتدی.")),
    VideoChannel("UCftkAUwCOQM_FhQ4216qFhA", "Français avec Fluidité", "B1–B2", d("Fabien helps intermediate learners understand fast, natural spoken French.", "Fabien aide les apprenants de niveau intermédiaire à comprendre le français parlé, rapide et naturel.", "فابین به زبان‌آموزان سطح متوسط کمک می‌کند فرانسوی گفتاری سریع و طبیعی را بفهمند.")),
    VideoChannel("UC5E3cis5nJyfuiU5_bzvIwg", "Parlez-vous FRENCH", "A2–B2", d("Anne's lessons in French on grammar, vocabulary, pronunciation and correct usage.", "Les leçons d'Anne, en français, sur la grammaire, le vocabulaire, la prononciation et le bon usage.", "درس‌های آن به زبان فرانسوی دربارهٔ دستور، واژگان، تلفظ و کاربرد درست.")),
    VideoChannel("UC59NqFOvritu4Rw93tQT4LQ", "Madame à Paname", "A2–B2", d("Grammar and conjugation explained in French with humour, one point per video.", "La grammaire et la conjugaison expliquées en français avec humour, un point par vidéo.", "دستور و صرف فعل به زبان فرانسوی و با چاشنی طنز؛ هر ویدیو یک نکته.")),
    VideoChannel("UC_XYAAXeuD_3JPDJ-QSv1jg", "Ma prof de français", "A2–B2", d("Québec French explained by a teacher from Québec: expressions, pronunciation and culture.", "Le français québécois expliqué par une professeure du Québec : expressions, prononciation et culture.", "فرانسوی کبکی به زبان یک معلم اهل کبک: اصطلاح‌ها، تلفظ و فرهنگ.")),
    VideoChannel("UCb3u5vctZOjajpr39xy2jMA", "French School TV", "A1–B1", d("Themed lessons on vocabulary, grammar and everyday situations.", "Leçons thématiques sur le vocabulaire, la grammaire et les situations du quotidien.", "درس‌های موضوعی دربارهٔ واژگان، دستور و موقعیت‌های روزمره.")),
    VideoChannel("UC1ipKNAWcHhmcqKUEGg1RMg", "Le Monde des Langues", "B1–B2", d("Advice in French on how to learn a language well: methods, memory and motivation.", "Des conseils en français pour bien apprendre une langue : méthodes, mémoire et motivation.", "توصیه‌هایی به فرانسوی برای یادگیری بهتر زبان: روش‌ها، حافظه و انگیزه.")),
    VideoChannel("UCNw6J4vA8lLExo46eqBo4kg", "Carlito – Learn French Through Culture", null, d("French taught through French culture.", "Le français à travers la culture française.", "یادگیری فرانسه از راه فرهنگ فرانسه.")),
    VideoChannel("UCCD-nJGAxKmh1GnSuUx8Gig", "Guillaume Posé – Professeur de français", null, d("French lessons for learners on YouTube.", "Cours de français pour apprenants sur YouTube.", "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")),
    VideoChannel("UCXJhsHwX3IeKrP6iS8KnboQ", "Le French Club", null, d("French lessons for learners on YouTube.", "Cours de français pour apprenants sur YouTube.", "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")),
    VideoChannel("UCEuI3o4ZGS0W-aQU_T9vB5A", "Français avec Fred", null, d("French lessons for learners on YouTube.", "Cours de français pour apprenants sur YouTube.", "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")),
    VideoChannel("UCIUS_2a-HFSHmV_QVVHWp9A", "Le français by Alex", null, d("French lessons for learners on YouTube.", "Cours de français pour apprenants sur YouTube.", "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")),
    VideoChannel("UCSpvTtFPjWhSHiOlH5FyVzA", "Ohlala French Course", null, d("French lessons for learners on YouTube.", "Cours de français pour apprenants sur YouTube.", "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")),
    VideoChannel("UCAcAnMF0OrCtUep3Y4M-ZPw", "HugoDécrypte – Actus du jour", "B2–C1", d("The day's news summed up in about ten minutes.", "L'actualité du jour résumée en une dizaine de minutes.", "خلاصهٔ خبرهای روز در حدود ده دقیقه.")),
    VideoChannel("UCUo1RqYV8tGjV38sQ8S5p9A", "HugoDécrypte – Grands formats", "B2–C1", d("Long interviews and in-depth explainers on current affairs.", "Longs entretiens et décryptages approfondis de l'actualité.", "گفت‌وگوهای بلند و تحلیل‌های عمیق دربارهٔ رویدادهای روز.")),
    VideoChannel("UCYpRDnhk5H8h16jpS84uqsA", "Le Monde – YouTube", "B2–C1", d("Video explainers from the daily Le Monde, with maps and graphics.", "Les vidéos explicatives du quotidien Le Monde, avec cartes et infographies.", "ویدیوهای توضیحی روزنامهٔ لوموند، با نقشه و نمودار.")),
    VideoChannel("UCSKdvgqdnj72_SLggp7BDTg", "Brut", "B1–C1", d("Short news and society videos with on-screen French subtitles.", "Courtes vidéos d'actualité et de société, sous-titrées en français à l'écran.", "ویدیوهای کوتاه خبری و اجتماعی با زیرنویس فرانسوی روی تصویر.")),
    VideoChannel("UCCCPCZNChQdGa9EkATeye4g", "France 24", "B2–C1", d("International news in French around the clock, with a live stream.", "L'actualité internationale en français 24 heures sur 24, avec un direct.", "خبرهای بین‌المللی به فرانسوی در تمام شبانه‌روز، همراه با پخش زنده.")),
    VideoChannel("UCzKH70qfN_yuXq3s91fdwmg", "TV5MONDE Info", "B2–C1", d("News from the French-speaking world by TV5MONDE.", "L'actualité du monde francophone par TV5MONDE.", "خبرهای جهان فرانسوی‌زبان از TV5MONDE.")),
    VideoChannel("UCO6K_kkdP-lnSCiO3tPx7WA", "franceinfo – YouTube", "B2–C1", d("News, reports and interviews from France's public broadcaster.", "Infos, reportages et interviews du service public français.", "خبر، گزارش و مصاحبه از رسانهٔ عمومی فرانسه.")),
    VideoChannel("UCW2QcKZiU8aUGg4yxCIditg", "euronews (en français)", "B2–C1", d("European and world news in short reports.", "L'actualité européenne et internationale en courts reportages.", "خبرهای اروپا و جهان در گزارش‌های کوتاه.")),
    VideoChannel("UCckz6n8QccTd6K_xdwKqa0A", "AFP", "B2–C1", d("Short news reports from the Agence France-Presse news agency.", "Courts reportages d'actualité de l'Agence France-Presse.", "گزارش‌های خبری کوتاه از خبرگزاری فرانسه.")),
    VideoChannel("UClxaaAzHu1B5EoTtBYea7ig", "Radio-Canada Info", "B2–C1", d("News from Canada's public broadcaster, in Canadian French.", "L'actualité du diffuseur public canadien, en français du Canada.", "خبرهای رسانهٔ عمومی کانادا، با فرانسوی کانادایی.")),
    VideoChannel("UC8EzKGkEiusTm7g0mTfkWkg", "28 minutes – ARTE", "C1–C2", d("ARTE's daily current-affairs programme: a guest, then a debate.", "Le magazine d'actualité quotidien d'ARTE : un invité, puis un débat.", "برنامهٔ روزانهٔ ARTE دربارهٔ رویدادهای روز: یک مهمان و سپس یک مناظره.")),
    VideoChannel("UCTa1bPArjpvNMgSHAb_pflA", "Public Sénat", "C1–C2", d("The French parliamentary channel: debates, interviews and documentaries.", "La chaîne parlementaire : débats, entretiens et documentaires.", "شبکهٔ پارلمانی فرانسه: مناظره، مصاحبه و مستند.")),
    VideoChannel("UCfHn_8-ehdem86fEvlFg-Gw", "Le Parisien", "B2–C1", d("Reports and short news videos from the daily Le Parisien.", "Reportages et courtes vidéos d'actualité du quotidien Le Parisien.", "گزارش‌ها و ویدیوهای خبری کوتاه روزنامهٔ لو پاریزین.")),
    VideoChannel("UCCDz_XYeKWd0OIyjp95dqyQ", "Le Figaro", "B2–C1", d("News videos, interviews and debates from the daily Le Figaro.", "Vidéos d'actualité, entretiens et débats du quotidien Le Figaro.", "ویدیوهای خبری، مصاحبه‌ها و مناظره‌های روزنامهٔ لو فیگارو.")),
    VideoChannel("UCsrPUA0ZSDCNZC6wyRlR7ZA", "TF1 INFO", "B2–C1", d("Reports from the TF1 television news.", "Les reportages des journaux télévisés de TF1.", "گزارش‌های اخبار تلویزیونی شبکهٔ TF1.")),
    VideoChannel("UCXwDLMDV86ldKoFVc_g8P0g", "BFMTV", "B2–C1", d("Rolling news channel: live coverage, reports and interviews.", "Chaîne d'information en continu : directs, reportages et interviews.", "شبکهٔ خبری شبانه‌روزی: پوشش زنده، گزارش و مصاحبه.")),
    VideoChannel("UCwI-JbGNsojunnHbFAc0M4Q", "ARTE", "B2–C1", d("Documentaries and reports on culture, society, history and science.", "Documentaires et reportages sur la culture, la société, l'histoire et les sciences.", "مستندها و گزارش‌هایی دربارهٔ فرهنگ، جامعه، تاریخ و علم.")),
    VideoChannel("UCHGMBrXUzClgjEzBMei-Jdw", "Le Dessous des Cartes – ARTE", "B2–C1", d("Geopolitics explained with maps, in short episodes.", "La géopolitique expliquée par les cartes, en épisodes courts.", "ژئوپلیتیک به کمک نقشه‌ها، در قسمت‌های کوتاه.")),
    VideoChannel("UCh5JF-qt2ZXg8acejcJ15Xw", "Karambolage – ARTE", "B1–B2", d("Short animated pieces on everyday habits, objects and words in France and Germany.", "Courtes séquences animées sur les habitudes, objets et mots du quotidien en France et en Allemagne.", "قطعه‌های کوتاه انیمیشنی دربارهٔ عادت‌ها، اشیا و واژه‌های روزمره در فرانسه و آلمان.")),
    VideoChannel("UCP46_MXP_WG_auH88FnfS1A", "Nota Bene", "B2–C1", d("History told in an accessible, lively way.", "L'histoire racontée de façon accessible et vivante.", "تاریخ به زبانی ساده و پرشور.")),
    VideoChannel("UC2_OG1L8DLTzQ7UrZVOk7OA", "Axolot", "B2–C1", d("Strange and astonishing true stories from history, science and nature.", "Histoires vraies, étranges et étonnantes, tirées de l'histoire, des sciences et de la nature.", "داستان‌های واقعی عجیب و شگفت‌انگیز از تاریخ، علم و طبیعت.")),
    VideoChannel("UCd5DKToXYTKAQ6khzewww2g", "France Culture", "C1–C2", d("Interviews, ideas and short explainers from the public cultural radio station.", "Entretiens, idées et courtes vidéos explicatives de la radio culturelle publique.", "گفت‌وگو، اندیشه و ویدیوهای توضیحی کوتاه از رادیوی فرهنگی عمومی فرانسه.")),
    VideoChannel("UCzMHLUJ8xTqDq6xsk0N5IdQ", "Tout Simplement – Kurzgesagt", "B2–C1", d("Animated science explainers; the French version of Kurzgesagt.", "Vidéos de vulgarisation scientifique animées ; la version française de Kurzgesagt.", "ویدیوهای انیمیشنی علمی؛ نسخهٔ فرانسوی Kurzgesagt.")),
    VideoChannel("UCG9G2dyRv04FDSH1FSYuLBg", "Jamy – Epicurieux", "B1–B2", d("Jamy answers everyday science questions simply and clearly.", "Jamy répond simplement et clairement aux questions de science du quotidien.", "ژامی به پرسش‌های علمی روزمره ساده و روشن پاسخ می‌دهد.")),
    VideoChannel("UCaNlbnghtwlsGF-KzAFThqA", "ScienceEtonnante", "C1–C2", d("Physics, mathematics and other sciences explained in depth.", "La physique, les mathématiques et d'autres sciences expliquées en profondeur.", "توضیح عمیق فیزیک، ریاضی و علوم دیگر.")),
    VideoChannel("UCcziTK2NKeWtWQ6kB5tmQ8Q", "e-penser 2.0", "B2–C1", d("Science explained with humour.", "La science expliquée avec humour.", "علم به زبان طنز.")),
    VideoChannel("UCtqICqGbPSbTN09K1_7VZ3Q", "DirtyBiology", "B2–C1", d("Biology and evolution explained with humour.", "La biologie et l'évolution expliquées avec humour.", "زیست‌شناسی و تکامل به زبان طنز.")),
    VideoChannel("UCKjDY4joMPcoRMmd-G1yz1Q", "C'est une autre histoire", "B2–C1", d("History, art and mythology told with humour.", "L'histoire, l'art et la mythologie racontés avec humour.", "تاریخ، هنر و اسطوره‌شناسی به زبان طنز.")),
    VideoChannel("UCN4TCCaX-gqBNkrUqXdgGRA", "L'Histoire nous le dira", "B2–C1", d("History explained by a Québec historian, in Canadian French.", "L'histoire expliquée par un historien québécois, en français du Canada.", "تاریخ به روایت یک تاریخ‌دان کبکی، با فرانسوی کانادایی.")),
    VideoChannel("UCvRgiAmogg7a_BgQ_Ftm6fA", "Le Précepteur", "C1–C2", d("Philosophy explained: major thinkers and their ideas.", "La philosophie expliquée : les grands penseurs et leurs idées.", "فلسفه به زبان ساده: اندیشمندان بزرگ و ایده‌هایشان.")),
    VideoChannel("UCyJDHgrsUKuWLe05GvC2lng", "Stupid Economics", "B2–C1", d("Economics made understandable through concrete questions.", "L'économie rendue compréhensible à partir de questions concrètes.", "اقتصاد به زبان قابل‌فهم، با پرسش‌های ملموس.")),
    VideoChannel("UCofQxJWd4qkqc7ZgaLkZfcw", "Linguisticae", "B2–C1", d("Linguistics for everyone: the history of French, accents, spelling and the world's languages.", "La linguistique pour tous : histoire du français, accents, orthographe et langues du monde.", "زبان‌شناسی برای همه: تاریخ زبان فرانسه، لهجه‌ها، املا و زبان‌های جهان.")),
    VideoChannel("UCWnfDPdZw6A23UtuBpYBbAg", "Dr Nozman", "B1–B2", d("Science experiments and curious facts.", "Expériences scientifiques et faits étonnants.", "آزمایش‌های علمی و دانستنی‌های جالب.")),
    VideoChannel("UCjxfoYq1vAEGU7KD_C3I48g", "Mamytwink", "B2–C1", d("History documentaries and exploration of abandoned and historic sites.", "Documentaires d'histoire et exploration de lieux abandonnés et historiques.", "مستندهای تاریخی و کاوش در مکان‌های متروک و تاریخی.")),
    VideoChannel("UCSdJiWi0tq9CAx1bizne6mA", "Bruno Maltor", "B1–B2", d("Travel vlogs in France and around the world.", "Vlogs de voyage en France et autour du monde.", "ولاگ‌های سفر در فرانسه و سراسر جهان.")),
    VideoChannel("UCJ9tHJbCa8ZoMbapfJLy0ZQ", "imineo Documentaires", "B2–C1", d("Full-length documentaries on nature, history, travel and society.", "Documentaires complets sur la nature, l'histoire, le voyage et la société.", "مستندهای کامل دربارهٔ طبیعت، تاریخ، سفر و جامعه.")),
    VideoChannel("UCYFI5PGuw_34XaA3SGDkQOQ", "Documentaire Société", "B2–C1", d("Full-length documentaries and reports on French society.", "Documentaires et reportages complets sur la société française.", "مستندها و گزارش‌های کامل دربارهٔ جامعهٔ فرانسه.")),
    VideoChannel("UCXptamDYEVcU4JCio30hYTw", "Peppa Pig Français", "A1–A2", d("Short cartoon episodes with simple, clearly spoken everyday French.", "Courts épisodes de dessin animé, en français simple et bien articulé.", "قسمت‌های کوتاه کارتونی با فرانسوی سادهٔ روزمره و تلفظ واضح.")),
    VideoChannel("UCBwSqx6A83sNQ5SIYWpmucQ", "Caillou Français", "A1–A2", d("A cartoon about a little boy's daily life, with simple sentences.", "Un dessin animé sur le quotidien d'un petit garçon, avec des phrases simples.", "کارتونی دربارهٔ زندگی روزمرهٔ یک پسربچه، با جمله‌های ساده.")),
    VideoChannel("UCjd32KVfRCli1d9iqo4YZ5A", "Masha et Michka", "A1–A2", d("A cartoon with short, simple dialogues in French.", "Un dessin animé aux dialogues courts et simples, en français.", "کارتونی با گفت‌وگوهای کوتاه و ساده به فرانسوی.")),
    VideoChannel("UC8I-UIlXPNS4luC4iV7dRdQ", "Titounis", "A1–A2", d("Nursery rhymes and children's songs with easy-to-follow lyrics.", "Comptines et chansons pour enfants, aux paroles faciles à suivre.", "ترانه‌ها و شعرهای کودکانه با متن آسان.")),
    VideoChannel("UCIpBLlcyR1W6oEji4_qdrQA", "Miraculous – Ladybug et Chat Noir", "A2–B1", d("A French animated series with natural dialogue; a step up from preschool cartoons.", "Une série d'animation française aux dialogues naturels ; un cran au-dessus des dessins animés pour tout-petits.", "یک مجموعهٔ انیمیشن فرانسوی با گفت‌وگوهای طبیعی؛ یک پله بالاتر از کارتون‌های خردسالان.")),
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
