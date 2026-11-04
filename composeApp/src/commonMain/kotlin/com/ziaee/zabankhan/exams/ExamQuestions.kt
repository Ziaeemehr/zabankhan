package com.ziaee.zabankhan.exams

import kotlinx.serialization.json.*
import com.ziaee.zabankhan.lang.Languages

data class ExamQuestionText(val fr: String, val fa: String)
data class ExamQuestionItem(val id: String, val count: Int, val months: List<String>, val texts: List<ExamQuestionText>) {
    val lastSeen: String get() = months.last()
}
data class ExamQuestionTask(val id: String, val skill: String, val task: Int, val titleFr: String, val exportedOn: String, val items: List<ExamQuestionItem>)
data class ExamQuestionSet(val variant: String, val source: ExamSource, val tasks: List<ExamQuestionTask>)

fun parseExamQuestions(json: String): ExamQuestionSet {
    val root = Json.parseToJsonElement(json).jsonObject
    require(root.int("version") == 1)
    val source = root.obj("source")
    val tasks = root.array("tasks")
    return ExamQuestionSet(root.string("variant"), ExamSource("questions", source.string("title"), source.string("url")),
        (0 until tasks.size).mapNotNull { index ->
            runCatching {
                val task = tasks.obj(index)
                val entries = task.array("items")
                val items = (0 until entries.size).mapNotNull { itemIndex ->
                    runCatching {
                        val item = entries.obj(itemIndex)
                        val months = item.array("months").let { array -> (0 until array.size).map { array.string(it) } }
                        val texts = item.array("texts").let { array -> (0 until array.size).map {
                            val text = array.obj(it)
                            ExamQuestionText(text.string(Languages.FRENCH.code), text.optionalString("fa"))
                        } }
                        require(months.isNotEmpty() && months.all { it.matches(Regex("\\d{4}-\\d{2}")) })
                        require(texts.isNotEmpty() && texts.all { it.fr.isNotBlank() })
                        ExamQuestionItem(item.string("id"), item.int("count"), months, texts)
                    }.getOrNull()
                }
                require(items.isNotEmpty())
                ExamQuestionTask(task.string("id"), task.string("skill"), task.int("task"), task.string("titleFr"), task.string("exportedOn"), items)
            }.getOrNull()
        })
}


data class ExamSource(val id: String, val title: String, val url: String)
private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.optionalString(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int
private fun JsonObject.array(key: String) = getValue(key).jsonArray
private fun JsonObject.obj(key: String) = getValue(key).jsonObject
private fun JsonArray.obj(index: Int) = get(index).jsonObject
private fun JsonArray.string(index: Int) = get(index).jsonPrimitive.content
