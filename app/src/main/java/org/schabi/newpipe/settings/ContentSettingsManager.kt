package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.util.Log
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.util.zip.ZipOutputStream

class ContentSettingsManager(private val fileLocator: NewPipeFileLocator) {
    companion object {
        const val TAG = "ContentSetManager"
    }

    private var legacySettings = false

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, file: StoredFileHelper) {
        file.create()
        ZipOutputStream(BufferedOutputStream(SharpOutputStream(file.stream)))
            .use { outZip ->
                ZipHelper.addFileToZip(outZip, fileLocator.db.path, "newpipe.db")

                try {
                    FileOutputStream(fileLocator.settings).use { output ->
                        JsonWriter.indent("").on(output).`object`(preferences.all).done()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Unable to exportDatabase", e)
                }

                ZipHelper.addFileToZip(outZip, fileLocator.settings.path, "preferences.json")
            }
    }

    fun deleteSettingsFile() {
        fileLocator.settings.delete()
    }

    /**
     * Tries to create database directory if it does not exist.
     *
     * @return Whether the directory exists afterwards.
     */
    fun ensureDbDirectoryExists(): Boolean {
        return fileLocator.dbDir.exists() || fileLocator.dbDir.mkdir()
    }

    fun extractDb(file: StoredFileHelper): Boolean {
        val success = ZipHelper.extractFileFromZip(file, fileLocator.db.path, "newpipe.db")
        if (success) {
            fileLocator.dbJournal.delete()
            fileLocator.dbWal.delete()
            fileLocator.dbShm.delete()
        }

        return success
    }

    fun extractSettings(file: StoredFileHelper): Boolean {
        legacySettings = false
        if (ZipHelper.extractFileFromZip(file, fileLocator.settings.path, "preferences.json")) {
            return true
        }

        legacySettings = ZipHelper.extractFileFromZip(
            file,
            fileLocator.settings.path,
            "newpipe.settings"
        )
        return legacySettings
    }

    fun loadSharedPreferences(preferences: SharedPreferences) {
        try {
            val preferenceEditor = preferences.edit()

            FileInputStream(fileLocator.settings).use { input ->
                preferenceEditor.clear()
                @Suppress("UNCHECKED_CAST")
                val entries = if (legacySettings) {
                    PreferencesObjectInputStream(input).use { it.readObject() as Map<String, *> }
                } else {
                    JsonParser.`object`().from(input)
                }
                for ((key, value) in entries) {
                    when (value) {
                        is Boolean -> {
                            preferenceEditor.putBoolean(key, value)
                        }
                        is Float -> {
                            preferenceEditor.putFloat(key, value)
                        }
                        is Int -> {
                            preferenceEditor.putInt(key, value)
                        }
                        is Long -> {
                            preferenceEditor.putLong(key, value)
                        }
                        is String -> {
                            preferenceEditor.putString(key, value)
                        }
                        is JsonArray -> {
                            preferenceEditor.putStringSet(
                                key,
                                value.mapNotNull { entry -> entry as? String }.toSet()
                            )
                        }
                        is Set<*> -> {
                            preferenceEditor.putStringSet(
                                key,
                                value.mapNotNull { entry -> entry as? String }.toSet()
                            )
                        }
                    }
                }
                preferenceEditor.commit()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to loadSharedPreferences", e)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Unable to loadSharedPreferences", e)
        }
    }

    private class PreferencesObjectInputStream(stream: InputStream) : ObjectInputStream(stream) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (desc.name !in classWhitelist) {
                throw ClassNotFoundException("Class not allowed: ${desc.name}")
            }
            return super.resolveClass(desc)
        }

        companion object {
            private val classWhitelist = setOf(
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.Character",
                "java.lang.Short",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Float",
                "java.lang.Double",
                "java.lang.Void",
                "java.util.HashMap",
                "java.util.HashSet"
            )
        }
    }
}
