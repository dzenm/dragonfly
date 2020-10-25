package com.dzenm.permission

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.util.*


/**
 * @author dinzhenyan
 * @date 2019-04-30 20:03
 *
 *
 * 权限设置页（兼容大部分国产手机）
 */
object PermissionSetting {

    private val TAG = PermissionSetting::class.java.simpleName

    /*
     * 华为手机的一些包名常量
     */
    private const val HUAWEI_PACKAGE = "com.huawei.systemmanager"
    private const val HUAWEI_UI_PERMISSION = "com.huawei.permissionmanager.ui.MainActivity"
    private const val HUAWEI_UI_SYSTEM =
        "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"
    private const val HUAWEI_UI_NOTIFICATION =
        "com.huawei.notificationmanager.ui.NotificationManagmentActivity"

    /*
     * 小米手机的一些包名常量
     */
    private const val MIUI_INTENT = "miui.intent.action.APP_PERM_EDITOR"
    private const val MIUI_PACKAGE = "com.miui.securitycenter"
    private const val MIUI_UI_APP_PERMISSION =
        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
    private const val MIUI_UI_PERMISSION =
        "com.miui.permcenter.permissions.PermissionsEditorActivity"

    /*
     * OPPO手机的一些包名常量
     */
    private const val OPPO_PACKAGE_COLOR = "com.color.safecenter"
    private const val OPPO_PACKAGE_COLOR_OS = "com.coloros.safecenter"
    private const val OPPO_PACKAGE_OPPO = "com.oppo.safe"
    private const val OPPO_UI_PERMISSION =
        "com.color.safecenter.permission.floatwindow.FloatWindowListActivity"
    private const val OPPO_UI_SYS =
        "com.coloros.safecenter.sysfloatwindow.FloatWindowListActivity"
    private const val OPPO_UI_SAFE = "com.oppo.safe.permission.PermissionAppListActivity"

    /*
     * VIVO手机的一些包名常量
     */
    private const val VIVO_PACKAGE = "com.iqoo.secure"
    private const val VIVO_UI_SECURE_PHONE =
        "com.iqoo.secure.ui.phoneoptimize.FloatWindowManager"
    private const val VIVO_UI_SAFE_PERMISSION =
        "com.iqoo.secure.safeguard.SoftPermissionDetailActivity"

    /*
     * 魅族手机的一些包名常量
     */
    private const val MEIZU_INTENT = "com.meizu.safe.security.SHOW_APPSEC"
    private const val MEIZU_PACKAGE = "com.meizu.safe"
    private const val MEIZU_UI_PERMISSION = "com.meizu.safe.security.AppSecActivity"
    private const val DEFAULT_PACKAGE = "packageName"

    /**
     * @return 获取手机厂商名称
     */
    private fun mark(): String = Build.MANUFACTURER.toLowerCase(Locale.getDefault())

    /**
     * 一般手机通过该方法打开设置界面
     *
     * @param activity 当前Activity
     * @return intent
     */
    private fun normal(activity: Activity): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }

    /**
     * 跳转华为设置页面的Intent
     *
     * @param activity 当前Activity
     * @return intent
     */
    private fun huawei(activity: Activity): Intent = Intent().apply {
        component = ComponentName(HUAWEI_PACKAGE, HUAWEI_UI_PERMISSION)
        if (isEmptyIntent(activity, this)) {
            component = ComponentName(HUAWEI_PACKAGE, HUAWEI_UI_SYSTEM)
            if (isEmptyIntent(activity, this)) {
                component = ComponentName(HUAWEI_PACKAGE, HUAWEI_UI_NOTIFICATION)
            }
        }
    }

    /**
     * 跳转小米设置页面的Intent
     *
     * @param activity 当前Activity
     * @return intent
     */
    private fun xiaomi(activity: Activity): Intent = Intent(MIUI_INTENT).apply {
        putExtra("extra_pkgname", activity.packageName)
        if (isEmptyIntent(activity, this)) {
            setPackage(MIUI_PACKAGE)
            if (isEmptyIntent(activity, this)) {
                setClassName(MIUI_PACKAGE, MIUI_UI_APP_PERMISSION)
                if (isEmptyIntent(activity, this)) {
                    setClassName(MIUI_PACKAGE, MIUI_UI_PERMISSION)
                }
            }
        }
    }

    /**
     * 跳转OPPO设置页面的Intent
     *
     * @param activity 当前Activity
     * @return
     */
    private fun oppo(activity: Activity): Intent = Intent().apply {
        putExtra(DEFAULT_PACKAGE, activity.packageName)
        if (isEmptyIntent(activity, this)) {
            setClassName(OPPO_PACKAGE_COLOR, OPPO_UI_PERMISSION)
            if (isEmptyIntent(activity, this)) {
                setClassName(OPPO_PACKAGE_COLOR_OS, OPPO_UI_SYS)
                if (isEmptyIntent(activity, this)) {
                    setClassName(OPPO_PACKAGE_OPPO, OPPO_UI_SAFE)
                }
            }
        }
    }

    /**
     * 跳转VIVO设置页面的Intent
     *
     * @param activity 当前Activity
     * @return intent
     */
    private fun vivo(activity: Activity): Intent = Intent().apply {
        setClassName(VIVO_PACKAGE, VIVO_UI_SECURE_PHONE)
        putExtra(DEFAULT_PACKAGE, activity.packageName)
        if (isEmptyIntent(activity, this)) {
            component = ComponentName(VIVO_PACKAGE, VIVO_UI_SAFE_PERMISSION)
        }
    }

    /**
     * 跳转魅族设置页面的Intent
     *
     * @param activity 当前Activity
     * @return intent
     */
    private fun meizu(activity: Activity): Intent = Intent(MEIZU_INTENT).apply {
        putExtra(DEFAULT_PACKAGE, activity.packageName)
        component = ComponentName(MEIZU_PACKAGE, MEIZU_UI_PERMISSION)
    }

    /**
     * 跳转到应用权限设置页面
     *
     * @param fragment  通过Fragment打开设置页面
     * @param isNewTask 是否使用新的任务栈启动
     */
    fun openSetting(fragment: Fragment, isNewTask: Boolean) {
        val activity: FragmentActivity = fragment.activity!!
        val intent = when {
            mark() == "huawei" -> huawei(activity)
            mark() == "xiaomi" -> xiaomi(activity)
            mark() == "oppo" -> oppo(activity)
            mark() == "vivo" -> vivo(activity)
            mark() == "meizu" -> meizu(activity)
            else -> normal(activity)
        }
        if (isNewTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.d(TAG, "当前手机厂商: " + mark())
        fragment.startActivityForResult(intent, PermissionFragment.REQUEST_SETTING)
    }

    /**
     * @param activity 当前Activity
     * @param intent   判断的Intent
     * @return 是否存在该Intent
     */
    private fun isEmptyIntent(activity: Activity, intent: Intent): Boolean {
        return activity.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isEmpty()
    }
}