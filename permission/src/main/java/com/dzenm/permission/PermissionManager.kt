package com.dzenm.permission

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * <pre>
 * PermissionManager.getInstance()
 *      .with(this)
 *      .load(permissions)
 *      .into(this)
 *      .requestPermission();
 * </pre>
 *
 * @author dinzhenyan
 * @date 2019-04-30 20:03
 *
 * 权限请求管理工具类
 */
class PermissionManager private constructor() {

    private var fragment = PermissionFragment()

    companion object {

        private val TAG = PermissionManager::class.java.simpleName

        @Volatile
        private var sPermissionManager: PermissionManager? = null

        @JvmStatic
        fun getInstance(): PermissionManager = sPermissionManager ?: synchronized(this) {
            sPermissionManager ?: PermissionManager().also { sPermissionManager = it }
        }
    }

    fun with(activity: AppCompatActivity): PermissionManager =
        transaction(activity.supportFragmentManager).apply {
            Log.d(TAG, activity.javaClass.simpleName + "正在请求权限...")
        }

    fun with(fragment: Fragment): PermissionManager =
        transaction(fragment.childFragmentManager).apply {
            Log.d(TAG, fragment::class.java.simpleName + "正在请求权限...")
        }

    private fun transaction(manager: FragmentManager): PermissionManager = apply {
        if (!fragment.isAdded) {
            manager.beginTransaction()
                .add(fragment, TAG)
                .commitNow()
        }
    }

    /**
     * @param permissions
     * @return this
     */
    fun load(permissions: String): PermissionManager =
        apply { fragment.permissions = arrayOf(permissions) }

    /**
     * @param permissions
     * @return this
     */
    fun load(permissions: List<String>): PermissionManager =
        apply { fragment.permissions = permissions.toTypedArray() }

    /**
     * @param permissions
     * @return this
     */
    fun load(permissions: Array<String>): PermissionManager =
        apply { fragment.permissions = permissions }

    /**
     * @param onPermissionListener [OnPermissionListener]
     * @return this
     */
    fun into(onPermissionListener: OnPermissionListener): PermissionManager = apply {
        fragment.onPermissionListener = onPermissionListener
    }

    /**
     * 自定义提示View
     * @param iPermissionView
     * @return this
     */
    fun setIPermissionView(iPermissionView: IPermissionView): PermissionManager =
        apply { fragment.iPermissionView = iPermissionView }


    /**
     * @param requestRepeat
     * @return this
     */
    fun repeat(): PermissionManager = apply { fragment.requestRepeat = true }

    fun request() = fragment.prepareRequestPermissions()

    interface OnPermissionListener {
        fun onPermit(isGrant: Boolean)
    }
}