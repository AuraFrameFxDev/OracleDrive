package com.genesis.ai.app.utils

import android.content.Context
import android.net.Uri
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as GDriveFile
import java.io.File

object CloudSyncHelper {
    /**
     * Creates and configures a Google Drive service client using the provided context and Google account credentials.
     *
     * @return An instance of the Google Drive service client for API operations.
     */
    fun getDriveService(context: Context, credential: GoogleAccountCredential): Drive {
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("OracleDrive").build()
    }

    /**
     * Uploads a JSON backup file to Google Drive, optionally placing it in a specified folder.
     *
     * Reports upload progress via the provided callback. Returns the ID of the uploaded Drive file, or null if the upload fails.
     *
     * @param file The JSON file to upload.
     * @param folderId Optional Drive folder ID to upload the file into.
     * @param onProgress Callback invoked with upload progress as a percentage (0.0 to 100.0).
     * @return The ID of the uploaded Drive file, or null if unsuccessful.
     */
    fun uploadBackupToDrive(service: Drive, file: File, folderId: String? = null, onProgress: (Double) -> Unit = {}): String? {
        val gFile = GDriveFile().apply {
            name = file.name
            mimeType = "application/json"
            if (folderId != null) parents = listOf(folderId)
        }
        val mediaContent = FileContent("application/json", file)
        val insert = service.files().create(gFile, mediaContent)
        val uploader = insert.mediaHttpUploader
        uploader.chunkSize = MediaHttpUploader.MINIMUM_CHUNK_SIZE
        uploader.progressListener = MediaHttpUploaderProgressListener { onProgress(it.progress * 100) }
        val result = insert.execute()
        return result.id
    }

    /**
     * Retrieves a list of JSON backup files from Google Drive, optionally filtered by a specific folder.
     *
     * Only files with MIME type "application/json" and names containing "lsposed_backup" are included.
     *
     * @param folderId If provided, restricts the search to files within the specified Drive folder.
     * @return A list of matching Drive file metadata objects.
     */
    fun listBackups(service: Drive, folderId: String? = null): List<GDriveFile> {
        val query = "mimeType='application/json' and name contains 'lsposed_backup'" +
            (if (folderId != null) " and '$folderId' in parents" else "")
        return service.files().list().setQ(query).setSpaces("drive").execute().files
    }

    /**
     * Downloads a file from Google Drive by its file ID and saves it to the specified local file.
     *
     * @param fileId The ID of the file to download from Google Drive.
     * @param dest The local file where the downloaded content will be saved.
     */
    fun downloadBackup(service: Drive, fileId: String, dest: File) {
        service.files().get(fileId).executeMediaAndDownloadTo(dest.outputStream())
    }

    // Automation: Schedule daily cloud backup using WorkManager
    class DailyBackupWorker(appContext: android.content.Context, workerParams: androidx.work.WorkerParameters) : androidx.work.Worker(appContext, workerParams) {
        /**
         * Executes the scheduled daily backup task.
         *
         * Currently logs a message indicating where the cloud backup logic should be implemented and reports success.
         *
         * @return The result of the work, always indicating success.
         */
        override fun doWork(): Result {
            // TODO: Integrate with CloudSyncHelper and Google Drive
            // For now, just log
            android.util.Log.i("DailyBackupWorker", "Scheduled cloud backup would run here.")
            return Result.success()
        }
    }

    /**
     * Schedules a daily background task to perform cloud backups using WorkManager.
     *
     * Ensures that only one instance of the daily backup worker is active by using a unique work name.
     */
    fun scheduleDailyBackup(context: android.content.Context) {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<DailyBackupWorker>(1, java.util.concurrent.TimeUnit.DAYS).build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_cloud_backup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
