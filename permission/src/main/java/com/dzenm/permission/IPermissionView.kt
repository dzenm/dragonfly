package com.dzenm.permission

import android.app.Activity
import android.view.View

interface IPermissionView {

    fun createView(title: String, message: String, activity: Activity): View

    fun getPositionButton(): View

    fun getNegativeButton(): View

}