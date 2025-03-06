package com.example.kotlinquizzer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Question(val text: String, val options: List<String>)
data class Quiz(val id: Int, val name: String, val questions: List<Question>, val responses: List<String> = emptyList())

class QuizDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "quiz.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_QUIZZES = "quizzes"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_QUESTIONS = "questions"
        private const val COLUMN_RESPONSES = "responses"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_QUIZZES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_QUESTIONS TEXT,
                $COLUMN_RESPONSES TEXT
            )
        """.trimIndent()
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_QUIZZES")
        onCreate(db)
    }

    private fun questionsToJson(question: List<Question>): String {
        val jsonArray = JSONArray()
        for (q in question) {
            val obj = JSONObject()
            obj.put("text", q.text)
            val optionsArray = JSONArray(q.options)
            obj.put("options", optionsArray)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun responsesToJson(responses: List<String>): String {
        val jsonArray = JSONArray()
        for (r in responses) {
            jsonArray.put(r)
        }
        return jsonArray.toString()
    }

    private fun jsonToQuestions(json: String): List<Question> {
        val list = mutableListOf<Question>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val text = obj.getString("text")
            val optionsArray = obj.getJSONArray("options")
            val options = mutableListOf<String>()
            for (j in 0 until optionsArray.length()) {
                options.add(optionsArray.getString(j))
            }
            list.add(Question(text, options))
        }
        return list
    }

    private fun jsonToResponses(json: String): List<String> {
        val list = mutableListOf<String>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    fun insertQuiz(quiz: Quiz): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, quiz.name)
            put(COLUMN_QUESTIONS, questionsToJson(quiz.questions))
            put(COLUMN_RESPONSES, responsesToJson(quiz.responses))
        }
        val id = db.insert(TABLE_QUIZZES, null, values)
        db.close()
        return id
    }

    fun updateQuiz(quiz: Quiz): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, quiz.name)
            put(COLUMN_QUESTIONS, questionsToJson(quiz.questions))
            put(COLUMN_RESPONSES, responsesToJson(quiz.responses))
        }
        val rows = db.update(TABLE_QUIZZES, values, "$COLUMN_ID = ?", arrayOf(quiz.id.toString()))
        db.close()
        return rows
    }

    fun deleteQuiz(quizId: Int): Int {
        val db = writableDatabase
        val rows = db.delete(TABLE_QUIZZES, "$COLUMN_ID = ?", arrayOf(quizId.toString()))
        db.close()
        return rows
    }

    fun getAllQuizzes(): List<Quiz> {
        val db = readableDatabase
        val quizzes = mutableListOf<Quiz>()
        val cursor = db.query(TABLE_QUIZZES, null, null, null, null, null, null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
                val questionsJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_QUESTIONS))
                val responsesJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RESPONSES))
                val questions = jsonToQuestions(questionsJson)
                val responses =
                    if (responsesJson.isNotEmpty()) jsonToResponses(responsesJson) else emptyList()
                quizzes.add(Quiz(id, name, questions, responses))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return quizzes
    }

}
