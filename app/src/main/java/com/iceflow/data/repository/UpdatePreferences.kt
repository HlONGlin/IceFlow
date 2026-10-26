package com.iceflow.data.repository

import android.content.Context

/** 自动更新提醒的本地偏好，不写入备份。 */
class UpdatePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    var autoRemind: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REMIND, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REMIND, value).apply()

    /** 私有仓库需要只读令牌才能读取 Release 信息。 */
    var githubToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    private companion object {
        const val KEY_AUTO_REMIND = "auto_remind"
        const val KEY_TOKEN = "github_token"
    }
}
