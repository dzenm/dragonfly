package com.dzenm.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment

/**
 * @author dzenm
 * @date 2020/3/12 下午8:09
 */
internal class PermissionFragment : Fragment() {

    /**
     * 未授予权限时是否重复请求权限
     */
    internal var requestRepeat: Boolean = false

    /**
     * 需要请求的所有的权限
     */
    internal var permissions: Array<String> = arrayOf()

    /**
     * 请求权限回调, 成功为true, 失败为false
     */
    internal var onPermissionListener: PermissionManager.OnPermissionListener? = null

    internal var iPermissionView: IPermissionView? = null

    companion object {

        private val TAG = PermissionFragment::class.java.simpleName

        /**
         * 权限请求之后, 会回调onRequestPermissionsResult()方法, 需要通过requestCode去接收权限请求的结果
         */
        private const val REQUEST_PERMISSION = 0xF1

        /**
         * 当权限请求被拒绝之后, 提醒用户进入设置权限页面手动打开所需的权限, 用于接收回调的结果
         */
        internal const val REQUEST_SETTING = 0xF2
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    /**
     * 开始请求权限
     */
    internal fun prepareRequestPermissions() {
        // 判断是否存在未请求的权限，如果存在则继续请求，不存在返回请求结果
        when {
            // 未授予的权限为空
            isNotGrantedPermissionsEmpty() ->
                requestPermissionsResult(true)
            else ->
                openPromptPermissionDialog()
        }
    }

    /**
     * 手动授予权限回掉（重写onActivityResult，并调用该方法）
     *
     * @param requestCode 请求时的标志位
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SETTING) prepareRequestPermissions()
    }

    /**
     * 权限请求回调处理结果
     * 当请求权限的模式为 MODE_ONCE_INFO 或 MODE_REPEAT 时，如果需要强制授予权限，不授予时不予进入，则需要执行该方法
     *
     * @param requestCode  请求权限的标志位
     * @param permissions  请求的所有权限 [android.permission.CAMERA, android.permission.CALL_PHONE]
     * @param grantResults 判断对应权限是否授权 [0, -1],
     * 已授权返回[PackageManager.PERMISSION_GRANTED], 未授权返回[PackageManager.PERMISSION_DENIED]
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permission = StringBuilder().apply {
            for (i in permissions.indices) append("${permissions[i]} ${grantResults[i]}\n")
        }
        Log.d(TAG, "requestCode: $requestCode\n$permission ")

        // 第一次请求的处理结果，过滤已授予的权限
        if (REQUEST_PERMISSION == requestCode) {
            // 判断是否还存在未授予的权限
            if (isNotGrantedPermissionsEmpty()) {
                Log.i(TAG, "请求成功")
                requestPermissionsResult(true)
            } else if (requestRepeat) {
                if (existShowRationalePermissions()) {
                    Log.i(TAG, "请求权限被拒绝且记住, 提示用户手动打开权限")
                    openFailedDialog(true)
                } else {
                    Log.i(TAG, "请求权限被拒绝未记住, 显示权限请求提示, 并重复请求权限")
                    openPromptPermissionDialog()
                }
            } else {
                Log.i(TAG, "请求失败")
                openFailedDialog(false)
            }
        }
    }

    /**
     * 打开未授予权限的对话框
     */
    private fun openFailedDialog(isRequestRepeat: Boolean) {
        val negativeText = if (isRequestRepeat) "进入授权" else "取消"
        AlertDialog.Builder(activity!!).apply {
            setTitle("错误提示")
            setMessage("拒绝授予程序运行需要的权限, 将出现不可预知的错误, 请授予权限后继续操作")
            setCancelable(false)
            setPositiveButton("手动设置") { dialog, _ ->
                PermissionSetting.openSetting(this@PermissionFragment, false)
                dialog.dismiss()
            }
            setNegativeButton(negativeText) { dialog, _ ->
                if (isRequestRepeat) {
                    // 拒绝权限并且未记住时, 提示用户授予权限并请求权限
                    requestPermissions(permissions, REQUEST_PERMISSION)
                } else {
                    // 回调请求结果
                    requestPermissionsResult(false)
                }
                dialog.dismiss()
            }
        }.create().show()
    }

    /**
     * 打开权限请求提示框
     */
    private fun openPromptPermissionDialog() {
        Log.i(TAG, "提示用户为什么要授予权限")
        AlertDialog.Builder(activity!!).apply {
            setTitle("温馨提示")
            setMessage(
                "程序运行所需以下权限\n${getPermissionPrompt(
                    getNotGrantedPermissions(activity!!, permissions)
                )}"
            )
            setCancelable(false)
            setPositiveButton("前往授权") { dialog, _ ->
                // 重复授权时点击取消按钮进入设置提示框
                requestPermissions(permissions, REQUEST_PERMISSION)
                dialog.dismiss()
            }
        }.create().show()
    }

    /**
     * 权限请求的结果
     */
    private fun requestPermissionsResult(result: Boolean) = onPermissionListener?.onPermit(result)

    /**
     * 是否存在未授予的解释权限
     *
     * @return 是否记住并拒绝授予所有请求的权限
     */
    private fun existShowRationalePermissions(): Boolean =
        existShowRationalePermissions(activity!!, permissions).apply {
            Log.d(TAG, "是否存在未授予的解释权限: $this")
        }

    /**
     * 是否存在未授予的解释权限
     * @param activity    当前Activity
     * @param permissions 判断的权限
     * @return 是否存在未授予的权限
     */
    private fun existShowRationalePermissions(
        activity: Activity,
        permissions: Array<String>
    ): Boolean {
        permissions.forEach { if (isShowRationalePermission(activity, it)) return true }
        return false
    }

    /**
     * 是否显示解释权限提示
     * @param activity   当前Activity
     * @param permission 判断的权限
     * @return 是否显示提示
     */
    private fun isShowRationalePermission(activity: Activity, permission: String): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    /**
     * 未授予的权限是否为空
     */
    private fun isNotGrantedPermissionsEmpty(): Boolean =
        getNotGrantedPermissions(activity!!, permissions).isEmpty()

    /**
     * 获取未授予的权限
     *
     * @param activity    当前Activity
     * @param permissions 需要过滤的权限
     * @return 过滤后的权限
     */
    private fun getNotGrantedPermissions(
        activity: Activity,
        permissions: Array<String>
    ): Array<String> =
        ArrayList<String>().apply {
            permissions.forEach { if (!isGrantPermission(activity, it)) add(it) }
        }.toTypedArray()

    /**
     * 判断单个权限是否授予
     * targetSdkVersion<23时 即便运行在android6及以上设备
     * ContextWrapper.checkSelfPermission和Context.checkSelfPermission失效
     * 返回值始终为PERMISSION_GRANTED,此时必须使用PermissionChecker.checkSelfPermission
     *
     * @param context    上下文
     * @param permission 判断的权限
     * @return 单个权限是否授予
     */
    private fun isGrantPermission(context: Context, permission: String): Boolean =
        if (getTargetSdkVersion(context)) {
            PermissionChecker.checkPermission(
                context,
                permission,
                Binder.getCallingPid(),
                Binder.getCallingUid(),
                context.packageName
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * @param context 上下文
     * @return 当前 target sdk 版本 是否大于 23
     */
    private fun getTargetSdkVersion(context: Context): Boolean =
        context.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.M

    /**
     * @return 权限提示文本
     */
    private fun getPermissionPrompt(permissions: Array<String>): String = StringBuilder().run {
        for (i in permissions.indices) {
            append(if (i == 0) "⊙\t\t" else "\n⊙\t\t")
            append(decodePermissionText(permissions[i]))
        }
        if ("".contentEquals(this)) "(空)" else toString()
    }

    /**
     * 获取权限文本名称
     *
     * @param permission 需要转化的权限
     * @return 转化后的权限名称
     */
    private fun decodePermissionText(permission: String): String = when (permission) {
        Manifest.permission.WRITE_CONTACTS -> "联系人写入权限"
        Manifest.permission.READ_CONTACTS -> "联系人读取权限"
        Manifest.permission.GET_ACCOUNTS -> "联系人账户读取权限"
        Manifest.permission.READ_CALL_LOG -> "通话记录读取权限"
        Manifest.permission.WRITE_CALL_LOG -> "通话记录写入权限"
        Manifest.permission.READ_PHONE_STATE -> "手机状态读取权限"
        Manifest.permission.CALL_PHONE -> "拨打电话权限"
        Manifest.permission.USE_SIP -> "使用SIP权限"
        Manifest.permission.PROCESS_OUTGOING_CALLS -> "处理呼入电话权限"
        Manifest.permission.ADD_VOICEMAIL -> "添加声音邮件权限"
        Manifest.permission.READ_CALENDAR -> "日历读取权限"
        Manifest.permission.WRITE_CALENDAR -> "日历写入权限"
        Manifest.permission.CAMERA -> "照相机权限"
        Manifest.permission.BODY_SENSORS -> "传感器权限"
        Manifest.permission.ACCESS_FINE_LOCATION -> "访问精确位置权限"
        Manifest.permission.ACCESS_COARSE_LOCATION -> "访问粗略位置权限"
        Manifest.permission.READ_EXTERNAL_STORAGE -> "外部存储读取权限"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "外部存储写入权限"
        Manifest.permission.RECORD_AUDIO -> "录制音频权限"
        Manifest.permission.READ_SMS -> "读取短信权限"
        Manifest.permission.SEND_SMS -> "发送短信权限"
        Manifest.permission.RECEIVE_MMS -> "接收彩信权限"
        Manifest.permission.RECEIVE_SMS -> "接收短信权限"
        Manifest.permission.RECEIVE_WAP_PUSH -> "接收WAP推送权限"
        else -> ""
    }

    class PromptView : IPermissionView {

        override fun createView(title: String, message: String, activity: Activity): View {
            TODO("Not yet implemented")
        }

        override fun getPositionButton(): View {
            TODO("Not yet implemented")
        }

        override fun getNegativeButton(): View {
            TODO("Not yet implemented")
        }
    }
}