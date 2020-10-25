package com.dzenm.dragonfly

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.dzenm.download.DownloadListener
import com.dzenm.download.DownloadManager
import com.dzenm.upgrade.UpgradeManager
import kotlinx.android.synthetic.main.activity_download.*
import java.io.File

class DownloadActivity : AppCompatActivity() {

    var url = "http://hzdown.muzhiwan.com/2017/05/08/nl.noio.kingdom_59104935e56f0.apk"
    var url1 = "http://s1.music.126.net/download/android/CloudMusic_3.4.1.133604_official.apk"
    var url2 = "http://study.163.com/pub/study-android-official.apk"

    var isDownload = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)
        val downloadHelper = newInstance(1)
        tv_start.setOnClickListener { downloadHelper.start() }
        tv_stop.setOnClickListener { //                downloadHelper.stop();
            append("\n停止下载")
        }
        val downloadHelper1 = newInstance(2)
        tv_delete.setOnClickListener {
            isDownload = if (isDownload) {
                downloadHelper1.stop()
                false
            } else {
                downloadHelper1.start()
                true
            }
        }
        tv_upgeade.setOnClickListener {
            UpgradeManager.newInstance(this)
                .setUrl(url1)
                .setDesc("我也不知道更新了什么")
                .setFilePath(externalCacheDir!!.absolutePath)
                .setNewVersionCode(2)
                .update()
        }
    }

    private fun newInstance(position: Int): DownloadManager {
        val downloadHelper = DownloadManager(this)
        downloadHelper.setUrl(url1)
        downloadHelper.setDownloadListener(object : DownloadListener {
            override fun onProgress(totalValue: Long, currentValue: Long) {
                val percent = (currentValue * 100 / totalValue).toInt()
                //                append(percent + "%   ");
            }

            override fun onError(errorMsg: String?) {
                append("\n下载失败 $position : $errorMsg")
            }

            override fun onSuccess(filePath: String) {
                append("\n下载成功 $position : $filePath")
                installApk(this@DownloadActivity, filePath)
            }
        })
        return downloadHelper
    }

    private fun append(log: String) {
        runOnUiThread { tv_log.append(log) }
    }

    companion object {
        fun installApk(context: Context, downloadApk: String?) {
            val file = File(downloadApk)
            val intent = Intent(Intent.ACTION_VIEW)
            val apkUri: Uri
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val authority = context.packageName + ".fileprovider"
                apkUri = FileProvider.getUriForFile(context, authority, file)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                apkUri = Uri.fromFile(file)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            context.startActivity(intent)
        }
    }
}