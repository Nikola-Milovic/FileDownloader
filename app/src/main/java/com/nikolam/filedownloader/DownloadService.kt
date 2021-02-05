package com.nikolam.filedownloader

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming


interface DownloadService {

    @GET("/api/download/")
    @Streaming
    fun download() : Call<ResponseBody>
}