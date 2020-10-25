package com.dzenm.upgrade

import android.app.DownloadManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import com.dzenm.download_manager.DownloadHelper
import com.dzenm.download_manager.DownloadHelper.OnDownloadListener
import com.dzenm.progressview.ProgressView

/**
 * 更新版本
 */
class UpgradeManager private constructor(val activity: AppCompatActivity) : OnDownloadListener {

    private var mUpgradeDialog: UpgradeDialog? = null
    private var iView: IView = DialogView()

    /**
     * 更新下载的新版本名称 [setVersionName]
     */
    private var mVersionName: String? = null

    /**
     * 更新的内容 [setDesc]
     */
    private var mDesc: String? = null

    /**
     * 判断是否需要更新, 根据当前versionCode与newVersionCode进行对比
     */
    private var isNeedUpdate = false

    /**
     * 判断是否可以通过点击取消按钮取消下载
     */
    private var isCanCancel = true

    /**
     * 判断是否需要显示Dialog提示
     */
    private var isShowDialog = true

    /**
     * 下载管理器 [DownloadHelper]
     */
    private val mDownloadHelper: DownloadHelper = DownloadHelper(activity)

    /**
     * 设置下载的属性
     */
    private var mOnRequestListener: OnRequestListener? = null

    companion object {
        @JvmStatic
        fun newInstance(activity: AppCompatActivity) = UpgradeManager(activity)
    }

    /**
     * @param url 下载的url
     * @return this
     */
    fun setUrl(url: String): UpgradeManager = apply { mDownloadHelper.setUrl(url) }

    /**
     * @param filePath 存储的文件路径
     * @return this
     */
    fun setFilePath(filePath: String): UpgradeManager =
        apply { mDownloadHelper.setFilePath(filePath) }

    /**
     * @param newVersionCode 服务器上的最新版本号, 当服务器上最新版本高于当前安装的版本号时,会提示更新
     * @return this
     */
    fun setNewVersionCode(newVersionCode: Long): UpgradeManager =
        apply { isNeedUpdate = checkIsNeedUpdate(newVersionCode) }

    /**
     * @param versionName 下载的新版本名称
     * @return this
     */
    fun setVersionName(versionName: String): UpgradeManager = apply { mVersionName = versionName }

    /**
     * @param desc 下载新版本的更新内容
     * @return this
     */
    fun setDesc(desc: String): UpgradeManager = apply { mDesc = desc }

    /**
     * @param canCancel 是否可以取消(是否强制更新)
     * @return this
     */
    fun setCanCancel(canCancel: Boolean): UpgradeManager = apply { isCanCancel = canCancel }

    /**
     * @param showDialog 是否显示Dialog提示
     * @return this
     */
    fun setShowDialog(showDialog: Boolean): UpgradeManager = apply { isShowDialog = showDialog }

    /**
     * @param iView 自定义View
     * @return this
     */
    fun setView(iView: IView): UpgradeManager = apply { this.iView = iView }

    /**
     * @param onRequestListener 下载管理器的参数设置
     * @return this
     */
    fun setOnRequestListener(onRequestListener: OnRequestListener): UpgradeManager =
        apply { mOnRequestListener = onRequestListener }

    /**
     * 调用该方法进行检测是否需要更新, 当需要更新时，显示更新的dialog
     */
    fun update() {
        if (!isNeedUpdate) return
        mDownloadHelper.setOnDownloadListener(this)
        if (isShowDialog) {
            val dialogView = iView.createView(mVersionName, mDesc, this)
            mUpgradeDialog = UpgradeDialog(activity, dialogView)
            iView.getPositionButton()?.setOnClickListener {
                iView.isRunning(true)
                start()
            }
            iView.getNegativeButton()?.setOnClickListener { stop() }
            if (!isCanCancel) mUpgradeDialog?.isCancelable = false
            mUpgradeDialog?.show()
        } else {
            start()
        }
    }

    private fun start() {
        // 注册下载监听广播并开始下载
        mDownloadHelper.start()
    }

    private fun stop() {
        if (mDownloadHelper.isRunningDownload) {
            if (isCanCancel) {
                mDownloadHelper.stop()
                mUpgradeDialog?.dismiss()
            }
        } else {
            mUpgradeDialog?.dismiss()
        }
    }

    override fun onPrepared(request: DownloadManager.Request) {
        // 默认在下载预备之前，隐藏通知栏的显示下载进度
        request.setNotificationVisibility(DownloadHelper.NOTIFICATION_HIDDEN)
        if (mOnRequestListener != null) mOnRequestListener?.onRequest(request)
    }

    override fun onProgress(soFar: Long, totalFileSize: Long) {
        iView.flush((100 * soFar / totalFileSize).toInt())
    }

    override fun onSuccess(uri: Uri, mimeType: String) {
        mUpgradeDialog?.dismiss()
    }

    override fun onFailed(msg: String) {
        iView.isRunning(false)
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * 检查是否需要更新
     *
     * @param newVersionCode 校验的新版本
     * @return 是否需要更新
     */
    private fun checkIsNeedUpdate(newVersionCode: Long): Boolean {
        if (newVersionCode <= 0L) {
            throw NullPointerException("版本号无效")
        }
        // 将当前安装版本和服务器版本进行比较. 判断是否需要更新
        return newVersionCode > getVersionCode(activity)
    }

    private class DialogView : IView {
        private var mUpgradeButton: Button? = null
        private var mCancelButton: AppCompatImageView? = null
        private var mProgressBar: ProgressView? = null

        override fun flush(value: Int) {
            mProgressBar?.setProgressValue(value)
        }

        override fun isRunning(isRunning: Boolean) {
            if (isRunning) {
                mUpgradeButton?.visibility = View.GONE
                mProgressBar?.visibility = View.VISIBLE
            } else {
                mUpgradeButton?.visibility = View.VISIBLE
                mProgressBar?.visibility = View.GONE
            }
        }

        override fun createView(
            newVersionName: String?,
            desc: String?,
            upgradeManager: UpgradeManager
        ): View {
            val activity = upgradeManager.activity
            val normalPadding = dp2px(16)
            val parent = LinearLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
                orientation = LinearLayout.VERTICAL
            }

            // 顶部image
            val headerView = AppCompatImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setImageResource(R.drawable.ic_upgrade_top)
                scaleType = ImageView.ScaleType.FIT_END
            }

            // 中间显示的升级提示内容
            val contentLayout = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(normalPadding, normalPadding, normalPadding, normalPadding)
                setBackgroundColor(
                    activity.resources.getColor(android.R.color.white)
                )
            }

            val titleView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = normalPadding }
                gravity = Gravity.CENTER
                textSize = 18f
                text = activity.getText(R.string.dialog_new_version_title)
            }
            val descView = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = desc
            }

            // 按钮和进度条
            val buttonLayout = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp2px(50)
                ).apply { topMargin = normalPadding }
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            mUpgradeButton = Button(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                backgroundTintList =
                    ColorStateList.valueOf(activity.resources.getColor(android.R.color.holo_red_light))
                gravity = Gravity.CENTER
                text = activity.getText(R.string.dialog_up_grade)
                setTextColor(activity.resources.getColor(android.R.color.white))
            }
            mProgressBar = ProgressView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }

            contentLayout.addView(titleView)
            contentLayout.addView(descView)
            buttonLayout.addView(mUpgradeButton)
            buttonLayout.addView(mProgressBar)
            contentLayout.addView(buttonLayout)

            // 底部的取消按钮
            mCancelButton = AppCompatImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setImageResource(R.drawable.ic_upgrade_cancel)
            }
            parent.addView(headerView)
            parent.addView(contentLayout)
            parent.addView(mCancelButton)
            return parent
        }

        override fun getPositionButton(): View? = mUpgradeButton

        override fun getNegativeButton(): View? = mCancelButton

        /**
         * @param value 需要转换的dp值
         * @return dp值
         */
        private fun dp2px(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()
    }

    interface OnRequestListener {
        fun onRequest(request: DownloadManager.Request?)
    }

    /**
     * 获取应用程序版本号
     *
     * @param context 上下文
     * @return 当前应用程序的版本号
     */
    private fun getVersionCode(context: Context): Long {
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0)
                    .longVersionCode
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
                    .versionCode.toLong()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }
}