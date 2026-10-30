package com.yasmin.ai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedMemory(
    val id: Long,
    val content: String,
    val createdAt: Long
)

class MemoryDb(context: Context) :
    SQLiteOpenHelper(context, "yasmin_memories.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Future database migrations go here.
    }

    @Synchronized
    fun add(content: String): Long {
        val value = content.trim()
        require(value.isNotEmpty()) { "Memory khaali nahi ho sakti." }
        require(value.length <= 500) {
            "Memory 500 characters se chhoti honi chahiye."
        }

        val values = ContentValues().apply {
            put("content", value)
            put("created_at", System.currentTimeMillis())
        }

        return writableDatabase.insertOrThrow("memories", null, values)
    }

    @Synchronized
    fun getAll(): List<SavedMemory> {
        val result = mutableListOf<SavedMemory>()

        readableDatabase.rawQuery(
            "SELECT id, content, created_at FROM memories ORDER BY id DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    SavedMemory(
                        id = cursor.getLong(0),
                        content = cursor.getString(1),
                        createdAt = cursor.getLong(2)
                    )
                )
            }
        }

        return result
    }

    @Synchronized
    fun delete(id: Long): Int {
        return writableDatabase.delete(
            "memories",
            "id = ?",
            arrayOf(id.toString())
        )
    }

    @Synchronized
    fun clearAll(): Int {
        return writableDatabase.delete("memories", null, null)
    }
}
