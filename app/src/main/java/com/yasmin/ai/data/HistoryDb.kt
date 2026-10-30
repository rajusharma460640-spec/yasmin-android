package com.yasmin.ai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local-only conversation history (SQLite in the app's private storage). */
class HistoryDb(context: Context) : SQLiteOpenHelper(context, "yasmin.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "role TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migrations yet (schema version 1).
    }

    fun insert(role: String, content: String): ChatMessage {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("role", role)
            put("content", content)
            put("created_at", now)
        }
        val id = writableDatabase.insert("messages", null, values)
        return ChatMessage(id, role, content, now)
    }

    fun all(): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT id, role, content, created_at FROM messages ORDER BY id ASC", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(ChatMessage(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
            }
        }
        return out
    }

    fun clear() {
        writableDatabase.delete("messages", null, null)
    }
}
