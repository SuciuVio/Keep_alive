package com.keepalive

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object UrlRepository {
    private const val PREFS_NAME = "keep_alive_prefs"
    private const val KEY_URLS = "url_list"
    private val gson = Gson()

    fun getUrls(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_URLS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val urls: List<String> = gson.fromJson(json, type) ?: emptyList()
            urls.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveUrls(context: Context, urls: List<String>) {
        val normalized = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_URLS, gson.toJson(normalized)).apply()
    }

    fun addUrl(context: Context, url: String): MutableList<String> {
        val normalized = url.trim()
        val urls = getUrls(context)
        if (normalized.isNotEmpty() && !urls.contains(normalized)) {
            urls.add(normalized)
            saveUrls(context, urls)
        }
        return getUrls(context)
    }

    fun removeUrl(context: Context, url: String): MutableList<String> {
        val urls = getUrls(context)
        urls.remove(url)
        saveUrls(context, urls)
        return getUrls(context)
    }
}
