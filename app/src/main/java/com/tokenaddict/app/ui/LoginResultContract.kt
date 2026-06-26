package com.tokenaddict.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class LoginResultContract : ActivityResultContract<Unit, Boolean>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(context, LoginActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}
