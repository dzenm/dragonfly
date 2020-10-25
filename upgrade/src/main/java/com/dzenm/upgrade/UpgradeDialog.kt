package com.dzenm.upgrade

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialogFragment

class UpgradeDialog(
    private val activity: AppCompatActivity,
    private val dialogView: View
) : AppCompatDialogFragment() {

    fun show() = show(activity.supportFragmentManager, UpgradeDialog::class.java.simpleName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.DialogFragmentTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = dialogView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.let {
            // 解决Dialog内存泄漏
            try {
                it.setOnShowListener(null)
                it.setOnDismissListener(null)
                it.setOnCancelListener(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setWindowProperty(window())
    }

    /**
     * 设置Windows的属性
     */
    private fun setWindowProperty(window: Window) {
        val decorView = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        val layoutParams = decorView.layoutParams as MarginLayoutParams
        layoutParams.width = (getDisplayWidth(activity) * 0.7).toInt()
        decorView.layoutParams = layoutParams

        // dialog背景
        dialogView.setBackgroundColor(context!!.resources.getColor(android.R.color.transparent))

        // 设置是否可以通过点击dialog之外的区域取消显示dialog
        dialog!!.setCanceledOnTouchOutside(false)

        // 设置dialog显示的位置
        val attributes = window.attributes
        attributes.gravity = Gravity.CENTER
        window.attributes = attributes

        // 将背景设为透明
        window.setBackgroundDrawable(ColorDrawable(context!!.resources.getColor(android.R.color.transparent)))

        // dialog动画
        window.setWindowAnimations(R.style.DialogFragmentTheme_Overshoot_Animator)

        // 解决AlertDialog无法弹出软键盘,且必须放在AlertDialog的show方法之后
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        // 收起键盘
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
    }

    private fun window(): Window = dialog?.window ?: activity.window

    /**
     * @return 屏幕宽度, 包含NavigatorBar
     */
    private fun getDisplayWidth(activity: Activity): Int = getDisplayMetrics(activity).widthPixels

    /**
     * 获取屏幕指标工具
     *
     * @param activity 获取WindowManager
     * @return 屏幕指标
     */
    private fun getDisplayMetrics(activity: Activity): DisplayMetrics = DisplayMetrics().apply {
        activity.windowManager.defaultDisplay.getRealMetrics(this)
    }
}