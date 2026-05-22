package com.example.upbeat.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.util.UUID

object UserIdentityManager {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_USER_ID = "user_id"

    /**
     * Returns the persistent anonymous user ID, creating and storing one on first call.
     * The UUID is generated once and stored in SharedPreferences so it survives app restarts.
     */
    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_USER_ID, userId) }
            Log.d("UserIdentityManager", "Created new user ID: $userId")
        } else {
            Log.d("UserIdentityManager", "Existing user ID: $userId")
        }
        return userId
    }
}



