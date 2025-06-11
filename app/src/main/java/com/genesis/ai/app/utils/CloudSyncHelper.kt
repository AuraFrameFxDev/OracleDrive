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
    fun getDriveService(context: Context, credential: GoogleAccountCredential): Drive {
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("OracleDrive").build()
    }

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

    fun listBackups(service: Drive, folderId: String? = null): List<GDriveFile> {
        val query = "mimeType='application/json' and name contains 'lsposed_backup'" +
            (if (folderId != null) " and '$folderId' in parents" else "")
        return service.files().list().setQ(query).setSpaces("drive").execute().files
    }

    fun downloadBackup(service: Drive, fileId: String, dest: File) {
        service.files().get(fileId).executeMediaAndDownloadTo(dest.outputStream())
    }

    // Automation: Schedule daily cloud backup using WorkManager
    class DailyBackupWorker(appContext: android.content.Context, workerParams: androidx.work.WorkerParameters) : androidx.work.Worker(appContext, workerParams) {
        override fun doWork(): Result {
            // TODO: Integrate with CloudSyncHelper and Google Drive
            // For now, just log
            android.util.Log.i("DailyBackupWorker", "Scheduled cloud backup would run here.")
            return Result.success()
        }
    }

    fun scheduleDailyBackup(context: android.content.Context) {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<DailyBackupWorker>(1, java.util.concurrent.TimeUnit.DAYS).build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_cloud_backup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
