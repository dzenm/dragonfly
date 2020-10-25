package com.dzenm.dragonfly

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dzenm.permission.PermissionManager
import com.dzenm.permission.PermissionManager.OnPermissionListener
import kotlinx.android.synthetic.main.activity_permission.*

class PermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        btn_once.setOnClickListener {
            PermissionManager.getInstance()
                .with(this)
                .load(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.CAMERA
                    )
                )
                .into(object : OnPermissionListener {
                    override fun onPermit(isGrant: Boolean) {
                        Toast.makeText(
                            this@PermissionActivity,
                            if (isGrant) "授权成功" else "授权失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }).request()
        }
        btn_repeat.setOnClickListener {
            PermissionManager.getInstance()
                .with(this)
                .load(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.CAMERA
                    )
                )
                .repeat()
                .into(object : OnPermissionListener {
                    override fun onPermit(isGrant: Boolean) {
                        Toast.makeText(
                            this@PermissionActivity,
                            if (isGrant) "授权成功" else "授权失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }).request()
        }
    }
}