package com.nikolam.filedownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
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
            if (writeResponseBodyToDisk(body, context = applicationContext)) {
                Result.success()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun download() = suspendCoroutine<ResponseBody?> { cont ->
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
                    cont.resume(response.body())
                    //val writtenToDisk: Boolean = writeResponseBodyToDisk(response.body()!!)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Timber.d("Failure $t")
                cont.resume(null)
            }

        })
    }

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
}

private fun writeResponseBodyToDisk(body: ResponseBody?, context: Context): Boolean {
    if (body == null) {
        return false
    }

//    val CREATE_FILE = 1
//
//    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
//        addCategory(Intent.CATEGORY_OPENABLE)
//        type = "application/pdf"
//        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
//        putExtra(Intent.EXTRA_TITLE, "invoice.pdf")
//    }
//
//    context.startActivity(intent)

    val path = context.getExternalFilesDir(null)?.absolutePath

    val dir = File(path)
    if (!dir.exists()) dir.mkdirs()

    val newFile = File(dir, "newFile.pdf")

    Timber.d(newFile.toString())

    return try {

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val fileReader = ByteArray(4096)
            val fileSize = body.contentLength()
            var fileSizeDownloaded: Long = 0
            inputStream = body.byteStream()
            outputStream = FileOutputStream(newFile)
            while (true) {
                val read: Int = inputStream.read(fileReader)
                if (read == -1) {
                    break
                }
                outputStream.write(fileReader, 0, read)
                fileSizeDownloaded += read.toLong()
                Timber.d("file download: $fileSizeDownloaded of $fileSize");
            }
            outputStream.flush()
            true
        } catch (e: IOException) {
            Timber.e(e);
            false
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    } catch (e: IOException) {
        Timber.e(e);
        false
    }
}


// try {
//        contentResolver.openFileDescriptor(uri, "w")?.use {
//            FileOutputStream(it.fileDescriptor).use {
//                it.write(
//                    ("Overwritten at ${System.currentTimeMillis()}\n")
//                        .toByteArray()
//                )
//            }
//        }
//    } catch (e: FileNotFoundException) {
//        e.printStackTrace()
//    } catch (e: IOException) {
//        e.printStackTrace()
//    }

fun test(context: Context) {
    val contentResolver = context.contentResolver
    val name = "myfile"
    val relativeLocation = Environment.DIRECTORY_PICTURES + File.pathSeparator + "AppName"

    val contentValues = ContentValues().apply {
//        put(MediaStore.Images.ImageColumns.DISPLAY_NAME, name)
//        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.DownloadColumns.MIME_TYPE, "application/pdf")

        // without this part causes "Failed to create new MediaStore record" exception to be invoked (uri is null below)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Downloads.RELATIVE_PATH, relativeLocation)
        }
    }

    val contentUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        TODO("VERSION.SDK_INT < Q")
    }
    var stream: OutputStream? = null
    var uri: Uri? = null

    try {
        uri = contentResolver.insert(contentUri, contentValues)
        if (uri == null) {
            throw IOException("Failed to create new MediaStore record.")
        }

        stream = contentResolver.openOutputStream(uri)

        if (stream == null) {
            throw IOException("Failed to get output stream.")
        }

//        Snackbar.make(mCoordinator, R.string.image_saved_success, Snackbar.LENGTH_INDEFINITE).setAction("Open") {
//            val intent = Intent()
//            intent.type = "image/*"
//            intent.action = Intent.ACTION_VIEW
//            intent.data = contentUri
//            startActivity(Intent.createChooser(intent, "Select Gallery App"))
//        }.show()

    } catch (e: IOException) {
        if (uri != null) {
            contentResolver.delete(uri, null, null)
        }

        throw IOException(e)

    } finally {
        stream?.close()
    }
}