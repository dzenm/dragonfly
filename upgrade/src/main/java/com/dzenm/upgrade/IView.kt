package com.dzenm.upgrade

import android.view.View

interface IView {

    /**
     * @param newVersionName 新版本名称
     * @param desc           更新描述内容
     * @param upgradeManager 更新管理器
     */
    fun createView(newVersionName: String?, desc: String?, upgradeManager: UpgradeManager): View

    /**
     * 升级按钮
     */
    fun getPositionButton(): View?

    /**
     * 取消按钮
     */
    fun getNegativeButton(): View?

    /**
     * @param value 下载进度（百分比）
     */
    fun flush(value: Int)

    /**
     * @param isRunning 是否正在下载
     */
    fun isRunning(isRunning: Boolean)

}