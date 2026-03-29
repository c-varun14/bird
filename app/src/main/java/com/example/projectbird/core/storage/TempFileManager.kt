package com.example.projectbird.core.storage

import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TempFileManager(
    context: Context
) {
    private val appContext = context.applicationContext

    private val tempDirectory: File by lazy {
        File(appContext.cacheDir, TEMP_AUDIO_DIRECTORY_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun createTempAudioFile(extension: String = DEFAULT_EXTENSION): File {
        ensureTempDirectory()

        val safeExtension = extension.removePrefix(".")
        val timestamp = FILE_TIMESTAMP_FORMAT.format(Date())
        val fileName = "chunk_${timestamp}_${UUID.randomUUID()}.$safeExtension"
        val file = File(tempDirectory, fileName)

        if (!file.createNewFile()) {
            throw IOException("Unable to create temp audio file: ${file.absolutePath}")
        }

        return file
    }

    fun getTempDirectoryFile(): File {
        ensureTempDirectory()
        return tempDirectory
    }

    fun deleteTempFile(file: File?): Boolean {
        if (file == null) return false
        if (!file.exists()) return true
        return file.delete()
    }

    fun clearAllTempFiles(): Int {
        ensureTempDirectory()

        val files = tempDirectory.listFiles().orEmpty()
        var deletedCount = 0

        files.forEach { file ->
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }

        return deletedCount
    }

    fun moveToSessionDirectory(
        tempFile: File,
        sessionDirectory: File,
        targetFileName: String? = null
    ): File {
        require(tempFile.exists()) {
            "Temp file does not exist: ${tempFile.absolutePath}"
        }

        if (!sessionDirectory.exists() && !sessionDirectory.mkdirs()) {
            throw IOException("Unable to create session directory: ${sessionDirectory.absolutePath}")
        }

        val destinationFile = File(
            sessionDirectory,
            targetFileName ?: tempFile.name
        )

        val renamed = tempFile.renameTo(destinationFile)
        if (!renamed) {
            tempFile.copyTo(destinationFile, overwrite = true)
            if (!tempFile.delete()) {
                throw IOException("Failed to delete temp file after copy: ${tempFile.absolutePath}")
            }
        }

        return destinationFile
    }

    private fun ensureTempDirectory() {
        if (!tempDirectory.exists() && !tempDirectory.mkdirs()) {
            throw IOException("Unable to create temp directory: ${tempDirectory.absolutePath}")
        }
    }

    companion object {
        private const val TEMP_AUDIO_DIRECTORY_NAME = "temp_audio_chunks"
        private const val DEFAULT_EXTENSION = "m4a"

        private val FILE_TIMESTAMP_FORMAT = SimpleDateFormat(
            "yyyyMMdd_HHmmss_SSS",
            Locale.US
        )
    }
}
