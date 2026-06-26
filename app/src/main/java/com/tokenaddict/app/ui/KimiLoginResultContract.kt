package com.tokenaddict.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class KimiLoginResultContract : ActivityResultContract<Unit, Boolean>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(context, KimiLoginActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}
