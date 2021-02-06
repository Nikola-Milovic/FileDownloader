package com.nikolam.filedownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class DownloadWorker(context: Context, parameters: WorkerParameters) :
        CoroutineWorker(context, parameters) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as
            NotificationManager

    override suspend fun doWork(): Result {
//        val inputUrl = inputData.getString(KEY_INPUT_URL)
//            ?: return Result.failure()
//        val outputFile = inputData.getString(KEY_OUTPUT_FILE_NAME)
//            ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            // Mark the Worker as important
            val progress = "Starting Download"

            Timber.d("WithContext")

            setForeground(createForegroundInfo(progress))

            val body = download()
            Timber.d(body.toString())
            if (body != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (writeResponseBodyToDisk(body)) {
                    return@withContext Result.success()
                } else {
                    return@withContext Result.failure()
                }
            } else {
                return@withContext Result.failure()
            }
        }
    }

    private suspend fun download() = suspendCoroutine<FileDownloadResponse?> { cont ->
        Timber.d("Download")
        val retrofit = Retrofit.Builder()
                .baseUrl("http://10.0.2.2:3000/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val service: DownloadService = retrofit.create(DownloadService::class.java)

        service.download().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Timber.d(response.toString())
                    Timber.d(response.headers().toString())
                    cont.resume(FileDownloadResponse(response.body(), response.headers()["File-Name"]!!, response.headers().get("Size")!!.toLong()))
                    //val writtenToDisk: Boolean = writeResponseBodyToDisk(response.body()!!)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Timber.d("Failure $t")
                cont.resume(null)
            }
        })
    }

    inner class FileDownloadResponse(val body : ResponseBody?, val fileName : String, val fileSize : Long)

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val id = CHANNEL_ID
        val title = "File Download"
        //  val cancel = applicationContext.getString(R.string.cancel_download)
        // This PendingIntent can be used to cancel the worker
//        val intent = WorkManager.getInstance(applicationContext)
//            .createCancelPendingIntent(getId())

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                //   .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build()

        return ForegroundInfo(123, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        val name = "Download Progress"
        val descriptionText = "Download progress description"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        notificationManager.createNotificationChannel(mChannel)
    }

    companion object {
        const val CHANNEL_ID = "dprogress"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeResponseBodyToDisk(body: FileDownloadResponse?): Boolean {
        if (body?.body == null) {
            return false
        }

        return savePDFFile(body.body.byteStream(), body.fileSize, body.fileName) == null

    }

    //https://stackoverflow.com/questions/63480192/how-to-save-pdf-file-in-a-media-store-in-android-10-and-above-using-java
    @RequiresApi(Build.VERSION_CODES.Q)
    fun savePDFFile(inputStream: InputStream, fileSize: Long, fileName: String): Uri? {
        val contentResolver = applicationContext.contentResolver
        val relativeLocation = Environment.DIRECTORY_DOCUMENTS
        val subfolder = "/dir"

        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation);
        contentValues.put(MediaStore.Video.Media.TITLE, "SomeName");
        contentValues.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        contentValues.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        var stream: OutputStream? = null
        var uri: Uri? = null

        try {
            val contentUri = MediaStore.Files.getContentUri("external");
            uri = contentResolver.insert(contentUri, contentValues)
            if (uri == null) {
                return null
            }
            val pfd: ParcelFileDescriptor?
            try {
                pfd = applicationContext.contentResolver.openFileDescriptor(uri, "w")

                if (pfd == null) {
                    return null
                }

                val out = FileOutputStream(pfd.fileDescriptor);
                //Progress
                var fileSizeDownloaded = 0L

                val buf = ByteArray(4096)
                var read = inputStream.read(buf)
                while (read > 0) {
                    out.write(buf, 0, read);
                    read = inputStream.read(buf)
                    fileSizeDownloaded += read.toLong()
                    if (fileSizeDownloaded % 4096 * 3 == 0L) {
                        //setForegroundAsync(createForegroundInfo("Downloaded $fileSizeDownloaded out of $fileSize"))
                        Timber.d("Downloaded $fileSizeDownloaded out of $fileSize")
                    }
                }
                out.close();
                inputStream.close();
                pfd.close();
            } catch (e: Exception) {
                Timber.e(e)
            }

            contentValues.clear();
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
            applicationContext.contentResolver.update(uri, contentValues, null, null);
            stream = contentResolver.openOutputStream(uri)
            if (stream == null) {
                throw IOException("Failed to get output stream.");
            }
            return uri
        } catch (e: IOException) {
            // Don't leave an orphan entry in the MediaStore
            if (uri != null) contentResolver.delete(uri, null, null);
            throw e;
        } finally {
            stream?.close()
        }
    }
}


// val path = context.getExternalFilesDir(null)?.absolutePath + "/dir"
//
//    val dir = File(path)
//    if (!dir.exists()) dir.mkdirs()
//
//    val newFile = File(dir, "newFile.pdf")
//
//
//    try {
//        newFile.createNewFile()
//    } catch (e: Exception) {
//        Timber.e(e)
//    }
//return try {
//
//        var inputStream: InputStream? = null
//        var outputStream: OutputStream? = null
//        try {
//            val fileReader = ByteArray(4096)
//            val fileSize = body.contentLength()
//            var fileSizeDownloaded: Long = 0
//            inputStream = body.byteStream()
//            outputStream = FileOutputStream(newFile)
//            while (true) {
//                val read: Int = inputStream.read(fileReader)
//                if (read == -1) {
//                    break
//                }
//                outputStream.write(fileReader, 0, read)
//                fileSizeDownloaded += read.toLong()
//                Timber.d("file download: $fileSizeDownloaded of $fileSize");
//            }
//            outputStream.flush()
//            true
//        } catch (e: IOException) {
//            Timber.e(e);
//            false
//        } finally {
//            inputStream?.close()
//            outputStream?.close()
//        }
//    } catch (e: IOException) {
//        Timber.e(e);
//        false
//    }
