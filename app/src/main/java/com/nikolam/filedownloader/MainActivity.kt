package com.nikolam.filedownloader

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.nikolam.filedownloader.databinding.ActivityMainBinding
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.startDownloadButton.setOnClickListener {
            downloadFile()
        }


        // This will initialise Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    super.log(priority, "dwnl_$tag", message, t)
                }
            })
        }
    }

    private fun downloadFile(){
        val downloadWorkRequest: WorkRequest =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                        .build()

        WorkManager
                .getInstance(applicationContext)
                .enqueue(downloadWorkRequest)
    }

}