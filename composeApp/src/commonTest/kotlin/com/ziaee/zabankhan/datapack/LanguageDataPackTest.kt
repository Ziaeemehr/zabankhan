package com.ziaee.zabankhan.datapack

import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.watch.*
import com.ziaee.zabankhan.grammar.*
import com.ziaee.zabankhan.exams.*
import kotlin.test.*
import kotlinx.serialization.json.*

class LanguageDataPackTest {
    @Test fun lemmaFormsConnectInflectionsAndComprehension() {
        val lexicon = loadLemmaLexicon("mangeaient\tmanger\nchevaux\tcheval\nbroken\n", "le\n", Languages.FRENCH)
        assertEquals(setOf("manger"), lexicon.lemmas("mangeaient"))
        assertTrue(lexicon.containsForm("chevaux"))
        assertEquals(100.0, ComprehensionAnalyzer(Languages.FRENCH, lexicon).analyze("le mangeaient chevaux", setOf("manger", "cheval")).knownWordPercent)
        val matcher = IdiomMatcher(loadIdiomLexicon("avoir faim\tverb\tbe hungry", Languages.FRENCH), Languages.FRENCH,
            loadLemmaLexicon("a\tavoir", "", Languages.FRENCH))
        assertEquals("avoir faim", matcher.match("il a faim").single().expression)
    }
    @Test fun onlyFrenchHasBundledLearningData() {
        assertTrue(LanguageDataPacks.available(Languages.FRENCH.code))
        assertFalse(LanguageDataPacks.available(Languages.ENGLISH.code))
        assertFalse(LanguageDataPacks.available(Languages.PERSIAN.code))
    }
    @Test fun orderedBlocksAndUtf16HighlightsSurvivePort() {
        val notes = parseGrammarNotes(grammarFixture, Languages.FRENCH)
        assertEquals(2, notes.size)
        assertEquals(notes.first().titleLearning, notes.first().titleEnglish)
        val translated = grammarFixture.replaceFirst("\"titleFr\":", "\"titleEn\":\"Although with subjunctive\",\"titleFr\":")
        assertEquals("Although with subjunctive", parseGrammarNotes(translated, Languages.FRENCH).first().titleEnglish)
        val note = notes.first { it.id == "bien-que" }
        assertTrue(note.blocks.first() is GrammarBlock.Prose)
        val example = (note.blocks.first { it is GrammarBlock.Examples } as GrammarBlock.Examples).items.first()
        assertEquals("Bien qu'", example.learning.text.substring(example.learning.highlights.first().start, example.learning.highlights.first().end))
        assertTrue(notes.flatMap { it.blocks }.any { it is GrammarBlock.Infographic })
    }
    @Test fun malformedNoteAndInvalidHighlightsDoNotHideValidNeighbors() {
        val root = Json.parseToJsonElement(grammarFixture).jsonObject
        val notes = root.getValue("notes").jsonArray
        val broken = buildJsonObject { put("id", "broken") }
        assertEquals(2, parseGrammarNotes(JsonObject(root + ("notes" to JsonArray(notes + broken))).toString(), Languages.FRENCH).size)
        val badNote = JsonObject(notes.first().jsonObject + ("group" to JsonPrimitive("unknown")))
        assertEquals(1, parseGrammarNotes(JsonObject(root + ("notes" to JsonArray(listOf(badNote, notes[1])))).toString(), Languages.FRENCH).size)
        val badHighlights = grammarFixture.replaceFirst("[0,8]", "[-1,999]")
        assertEquals(1, parseGrammarNotes(badHighlights, Languages.FRENCH).size)
    }
    @Test fun grammarSearchMatchesLearningLanguageAndPersianAliases() {
        val notes = parseGrammarNotes(grammarFixture, Languages.FRENCH)
        assertEquals(listOf("bien-que"), GrammarSearchIndex(notes, Languages.FRENCH).search("bien que").map { it.note }.map { it.id })
        assertEquals("bien-que", GrammarSearchIndex(notes, Languages.FRENCH).search("با اینکه").first().note.id)
    }
    @Test fun questionParserSkipsMalformedItemsAndTasks() {
        val fixture = """{"version":1,"variant":"TCF Canada","source":{"title":"source","url":"https://example.org"},"tasks":[{"id":"ee1","skill":"EE","task":1,"titleFr":"Message","exportedOn":"2026-10-11","items":[{"id":"a","count":2,"months":["2026-10"],"texts":[{"fr":"Invitez vos amis.","fa":"دعوت کنید"}]},{"id":"broken","count":1,"months":[],"texts":[]}]},{"id":"broken"}]}"""
        val set = parseExamQuestions(fixture)
        assertEquals(1, set.tasks.size)
        assertEquals("Invitez vos amis.", set.tasks.single().items.single().texts.single().fr)
        assertEquals("2026-10", set.tasks.single().items.single().lastSeen)
        assertFailsWith<IllegalArgumentException> { parseExamQuestions(fixture.replace("\"version\":1", "\"version\":2")) }
    }
}

private val grammarFixture = """{"version":1,"notes":[{"id":"bien-que","group":"structures","level":"B1","titleFr":"Bien que + subjonctif","titleFa":"bien que: با اینکه، هرچند","aliasesFr":["bien que","quoique","concession","bien que subjonctif"],"aliasesFa":["با اینکه","هرچند","اگرچه","با وجود اینکه","تضاد"],"related":["phrases-avec-si","subjonctif-emplois","pour-que","avant-que","opposition-concession","cause-consequence","subjonctif-passe","subjonctif-vs-indicatif"],"blocks":[{"type":"summary","markdown":[{"kind":"p","text":"`bien que` یعنی «با اینکه / هرچند» و در فرانسوی معیار، فعلِ بعد از آن در وجه التزامی (subjonctif) می‌آید، حتی وقتی دربارهٔ یک واقعیت حرف می‌زنیم."}]},{"type":"usage","markdown":[{"kind":"p","text":"برای بیان تضاد میان دو واقعیت: چیزی درست است، ولی نتیجه‌ای که انتظار داریم رخ نمی‌دهد. «با اینکه خسته است، به کار ادامه می‌دهد.»"},{"kind":"p","text":"بیشتر در نوشتار و گفتار رسمی‌تر شنیده می‌شود؛ در گفتار روزمره `mais` و `pourtant` رایج‌ترند."}]},{"type":"structure","patterns":["bien que + sujet + subjonctif"],"text":[{"kind":"p","text":"برای کاری که پیش از جملهٔ اصلی تمام شده، subjonctif passé می‌آید: `bien que + sujet + avoir / être (subjonctif) + participe passé`."}]},{"type":"examples","items":[{"id":"ex1","fr":"Bien qu'il soit fatigué, il continue à travailler.","fa":"با اینکه خسته است، به کار ادامه می‌دهد.","highlights":[[0,8],[11,15]]},{"id":"ex2","fr":"Bien qu'elle ait peu d'argent, elle est heureuse.","fa":"با اینکه پول کمی دارد، خوشحال است.","highlights":[[0,8],[13,16]]},{"id":"ex3","fr":"Nous sortons bien qu'il pleuve.","fa":"با اینکه باران می‌بارد، بیرون می‌رویم.","highlights":[[13,21],[24,30]]},{"id":"ex4","fr":"Bien que j'aie étudié, je n'ai pas réussi l'examen.","fa":"با اینکه درس خوانده بودم، در امتحان قبول نشدم.","highlights":[[0,8],[11,21]]}]},{"type":"mistake","wrong":"Bien qu'il est malade, il travaille.","right":"Bien qu'il soit malade, il travaille.","why":"در فرانسوی معیار بعد از bien que از subjonctif استفاده می‌شود. چون جمله یک واقعیت را می‌گوید، زبان‌آموز به‌اشتباه indicatif می‌گذارد."},{"type":"more","markdown":[{"kind":"li","text":"`quoique` همان معنی و همان ساختار را دارد و رسمی‌تر است: `Quoiqu'il soit tard, elle travaille encore.`"},{"kind":"li","text":"`même si` (حتی اگر) با indicatif می‌آید: `Même s'il pleut, nous sortons.`"},{"kind":"li","text":"پیش از اسم از `malgré` استفاده می‌شود: `Malgré la pluie, nous sortons.`"},{"kind":"li","text":"وقتی فاعل دو بخش یکی است، باز هم جملهٔ کامل با subjonctif می‌آید: `Bien que je sois fatigué, je sors.` در نوشتار گاهی فاعل و فعل حذف می‌شوند: `Bien que fatigué, je sors.`"},{"kind":"li","text":"`bien que` پیش از مصوت یا h بی‌صدا به `bien qu'` تبدیل می‌شود."},{"kind":"li","text":"در گفتار، indicatif بعد از `bien que` هم شنیده می‌شود، ولی در نوشتار و آزمون غلط به شمار می‌آید."}]}]},{"id":"adjectifs-possessifs","group":"basics","level":"A1","titleFr":"Les adjectifs possessifs","titleFa":"صفت‌های ملکی: mon، ma، mes","aliasesFr":["adjectifs possessifs","mon ma mes","son sa ses","notre nos","leur leurs"],"aliasesFa":["صفت ملکی","مالکیت","mon یا ma","فرق leur و leurs","son یعنی چه"],"related":["articles","genre-et-nombre","demonstratifs","pronoms-possessifs","tu-vs-vous"],"blocks":[{"type":"summary","markdown":[{"kind":"p","text":"صفت ملکی با اسمی که بعد از آن می‌آید در جنس و عدد هماهنگ می‌شود، نه با مالک. برای همین `son père` هم یعنی «پدرِ آن مرد» و هم «پدرِ آن زن»."}]},{"type":"usage","markdown":[{"kind":"p","text":"برای نشان‌دادن مالکیت یا نسبت: «برادرم»، «کلیدهایت»، «خانه‌مان». در فارسی این معنی با ضمیر پیوسته (ـم، ـت، ـش) یا «مالِ …» بیان می‌شود."}]},{"type":"structure","patterns":["mon / ma / mes + nom"],"text":[{"kind":"p","text":"شکل صفت ملکی را اسمِ بعدی تعیین می‌کند: مذکر مفرد `mon`، مؤنث مفرد `ma`، جمع `mes`."}]},{"type":"examples","items":[{"id":"ex1","fr":"Mon frère et ma sœur habitent à Lyon.","fa":"برادرم و خواهرم در لیون زندگی می‌کنند.","highlights":[[0,3],[13,15]]},{"id":"ex2","fr":"Marie adore son père.","fa":"ماری پدرش را خیلی دوست دارد.","highlights":[[12,15]]},{"id":"ex3","fr":"Où sont tes clés ?","fa":"کلیدهایت کجاست؟","highlights":[[8,11]]},{"id":"ex4","fr":"Notre maison est petite, mais nos voisins sont sympathiques.","fa":"خانه‌مان کوچک است، ولی همسایه‌هایمان خوش‌برخوردند.","highlights":[[0,5],[30,33]]},{"id":"ex5","fr":"Les enfants ont oublié leurs livres.","fa":"بچه‌ها کتاب‌هایشان را فراموش کرده‌اند.","highlights":[[23,28]]}]},{"type":"mistake","wrong":"C'est ma amie Claire.","right":"C'est mon amie Claire.","why":"پیش از اسم مؤنثی که با مصوت شروع می‌شود، به‌جای ma از mon استفاده می‌شود."},{"type":"more","markdown":[{"kind":"li","text":"پیش از اسم مؤنثی که با مصوت یا h بی‌صدا شروع می‌شود: `mon, ton, son`. مثل `mon école`، `ton idée`، `son histoire`."},{"kind":"li","text":"`leur` برای یک چیز و `leurs` برای چند چیز است: `leur maison` (خانه‌شان)، `leurs enfants` (بچه‌هایشان)."},{"kind":"li","text":"`votre` و `vos` هم برای چند نفر و هم برای خطاب مؤدبانه به یک نفر به کار می‌روند."},{"kind":"li","text":"برای اعضای بدن معمولاً حرف تعریف معین می‌آید، نه صفت ملکی: `Je me lave les mains.`، `J'ai mal à la tête.`"},{"kind":"li","text":"برای روشن‌کردن اینکه مالک مرد است یا زن، می‌توان `à lui` یا `à elle` اضافه کرد: `C'est son livre à elle.`"}]},{"type":"infographic","kind":"comparison","header":[{"text":"مالک","highlights":[]},{"text":"مذکر","highlights":[]},{"text":"مؤنث","highlights":[]},{"text":"جمع","highlights":[]}],"rows":[[{"text":"je","highlights":[]},{"text":"mon","highlights":[]},{"text":"ma","highlights":[]},{"text":"mes","highlights":[]}],[{"text":"tu","highlights":[]},{"text":"ton","highlights":[]},{"text":"ta","highlights":[]},{"text":"tes","highlights":[]}],[{"text":"il / elle","highlights":[]},{"text":"son","highlights":[]},{"text":"sa","highlights":[]},{"text":"ses","highlights":[]}],[{"text":"nous","highlights":[]},{"text":"notre","highlights":[]},{"text":"notre","highlights":[]},{"text":"nos","highlights":[]}],[{"text":"vous","highlights":[]},{"text":"votre","highlights":[]},{"text":"votre","highlights":[]},{"text":"vos","highlights":[]}],[{"text":"ils / elles","highlights":[]},{"text":"leur","highlights":[]},{"text":"leur","highlights":[]},{"text":"leurs","highlights":[]}]]}]}]}"""
