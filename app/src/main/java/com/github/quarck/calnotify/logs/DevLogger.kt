//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//


package com.github.quarck.calnotify.logs

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.format.DateUtils
import com.github.quarck.calnotify.utils.PersistentStorageBase
import java.io.Closeable


class DevLoggerSettings(ctx: Context): PersistentStorageBase(ctx, NAME) {

    var enabled by BooleanProperty(false)

    companion object {
        const val NAME="devlog"
    }
}

class DevLoggerDB(val context: Context):
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable {

    override fun onCreate(db: SQLiteDatabase) {

        val logger = Logger("DevLoggerDB")

        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_TIME INTEGER, " +
                        "$KEY_SEVERITY INTEGER, " +
                        "$KEY_EVENT_ID INTEGER, " +
                        "$KEY_MESSAGE TEXT, " +
                        " )"

        logger.debug("Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        if (oldVersion != newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db)
        }
    }

    fun addMessage(severity: Int, eventId: Long, message: String) {

        writableDatabase.use {
            db ->

            val values = ContentValues();

            values.put(KEY_TIME, System.currentTimeMillis())
            values.put(KEY_SEVERITY, severity)
            values.put(KEY_EVENT_ID, eventId)
            values.put(KEY_MESSAGE, message);

            db.insert(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
        }
    }

    fun getMessages(): List<String> {

        val ret = mutableListOf<String>()

        readableDatabase.use {
            db->

            val cursor = db.query(TABLE_NAME, // a. table
                    SELECTION_COLUMNS, // b. column names
                    null, // c. selections
                    null,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor.moveToFirst()) {

                do {
                    ret.add(cursorToLogLine(cursor))

                } while (cursor.moveToNext())
            }
            cursor.close()
        }

        return ret
    }

    fun clear() {
        writableDatabase.use { db ->
            db.delete(TABLE_NAME, null, null)
        }
    }

    private fun cursorToLogLine(cursor: Cursor): String {

        val time = cursor.getLong(PROJECTION_KEY_TIME)
        val sev = cursor.getInt(PROJECTION_KEY_SEVERITY)
        val eventId = cursor.getLong(PROJECTION_KEY_EVENT_ID)
        val msg = cursor.getString(PROJECTION_KEY_MESSAGE)

        val builder = StringBuffer(msg.length + 64)

        builder.append(
                DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)
        )

        when (sev) {
            SEVERITY_ERROR ->
                builder.append(": ERROR: ")
            SEVERITY_WARNING ->
                builder.append(": WARNING: ")
            SEVERITY_INFO ->
                builder.append(": INFO: ")
            SEVERITY_DEBUG ->
                builder.append(": DEBUG: ")
            else ->
                builder.append(": ")
        }

        if (eventId != 0L) {
            builder.append("Event ID: ")
            builder.append(eventId)
            builder.append(", ")
        }

        return builder.toString()
    }


    companion object {
        const val DATABASE_NAME = "devlogV1"
        const val TABLE_NAME = "messages"
        const val DATABASE_CURRENT_VERSION = 1

        const val KEY_TIME = "time"
        const val KEY_SEVERITY = "sev"
        const val KEY_EVENT_ID = "evId"
        const val KEY_MESSAGE = "msg"

        val SELECTION_COLUMNS = arrayOf<String>(
                KEY_TIME,
                KEY_SEVERITY,
                KEY_EVENT_ID,
                KEY_MESSAGE
        )

        const val PROJECTION_KEY_TIME = 0
        const val PROJECTION_KEY_SEVERITY = 1
        const val PROJECTION_KEY_EVENT_ID = 2
        const val PROJECTION_KEY_MESSAGE = 3

        const val SEVERITY_ERROR = 0
        const val SEVERITY_WARNING = 1
        const val SEVERITY_INFO = 2
        const val SEVERITY_DEBUG = 3
    }
}

class DevLogger(val ctx: Context) {

    val enabled: Boolean

    init {
        enabled = DevLoggerSettings(ctx).enabled
    }

    private fun log(severity: Int, eventId: Long, message: String) {
        if (enabled) {
            DevLoggerDB(ctx).use {
                it.addMessage(severity, eventId, message)
            }
        }
    }

    fun error(eventId: Long, message: String) {
        if (enabled)
            log(DevLoggerDB.SEVERITY_ERROR, eventId, message)
    }

    fun warn(eventId: Long, message: String) {
        if (enabled)
            log(DevLoggerDB.SEVERITY_WARNING, eventId, message)
    }

    fun info(eventId: Long, message: String) {
        if (enabled)
            log(DevLoggerDB.SEVERITY_INFO, eventId, message)
    }

    fun debug(eventId: Long, message: String) {
        if (enabled)
            log(DevLoggerDB.SEVERITY_DEBUG, eventId, message)
    }

    fun error(message: String) = error(0, message)

    fun warn(message: String) = warn(0, message)

    fun info(message: String) = info(0, message)

    fun debug(message: String) = debug(0, message)


    fun clear() {
        DevLoggerDB(ctx).use {
            it.clear()
        }
    }

    val messages: List<String>
        get() {

            val lines = DevLoggerDB(ctx).use {
                it.getMessages()
            }

            return lines
        }
}
