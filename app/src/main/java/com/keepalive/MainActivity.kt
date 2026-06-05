package com.keepalive

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {
    private lateinit var editTextUrl: EditText
    private lateinit var btnAddUrl: Button
    private lateinit var btnToggleService: Button
    private lateinit var textViewLog: TextView
    private lateinit var adapter: UrlAdapter

    private val logLines = mutableListOf<String>()
    private val maxLogLines = 100

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val msg = intent.getStringExtra(PingService.EXTRA_LOG_MESSAGE) ?: return
            runOnUiThread {
                logLines.add(0, msg)
                if (logLines.size > maxLogLines) logLines.removeAt(logLines.lastIndex)
                textViewLog.text = logLines.joinToString("\n")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannels(this)
        requestNotificationPermissionIfNeeded()

        editTextUrl = findViewById(R.id.editTextUrl)
        btnAddUrl = findViewById(R.id.btnAddUrl)
        btnToggleService = findViewById(R.id.btnToggleService)
        textViewLog = findViewById(R.id.textViewLog)

        val recyclerViewUrls = findViewById<RecyclerView>(R.id.recyclerViewUrls)
        adapter = UrlAdapter(UrlRepository.getUrls(this)) { url ->
            val updated = UrlRepository.removeUrl(this, url)
            adapter.updateList(updated)
        }
        recyclerViewUrls.layoutManager = LinearLayoutManager(this)
        recyclerViewUrls.adapter = adapter

        btnAddUrl.setOnClickListener { addUrlFromInput() }
        btnToggleService.setOnClickListener { toggleService() }
        updateToggleButton()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(PingService.BROADCAST_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(logReceiver, filter)
        }
        adapter.updateList(UrlRepository.getUrls(this))
        updateToggleButton()
    }

    override fun onPause() {
        unregisterReceiver(logReceiver)
        super.onPause()
    }

    private fun addUrlFromInput() {
        val url = editTextUrl.text.toString().trim()
        when {
            !isValidUrl(url) -> Toast.makeText(
                this,
                "URL invalid. Trebuie sa inceapa cu http:// sau https://",
                Toast.LENGTH_SHORT
            ).show()
            UrlRepository.getUrls(this).contains(url) -> Toast.makeText(
                this,
                "URL deja exista in lista",
                Toast.LENGTH_SHORT
            ).show()
            else -> {
                val updated = UrlRepository.addUrl(this, url)
                adapter.updateList(updated)
                editTextUrl.setText("")
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun toggleService() {
        if (PingService.isRunning) {
            stopMonitoringService()
            return
        }

        if (UrlRepository.getUrls(this).isEmpty()) {
            Toast.makeText(this, "Adauga cel putin un URL", Toast.LENGTH_SHORT).show()
            return
        }
        startMonitoringService()
    }

    private fun startMonitoringService() {
        val intent = Intent(this, PingService::class.java).apply {
            action = PingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        PingService.isRunning
        updateToggleButton()
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, PingService::class.java).apply {
            action = PingService.ACTION_STOP
        }
        startService(intent)
        updateToggleButton()
    }

    private fun updateToggleButton() {
        if (PingService.isRunning) {
            btnToggleService.text = "Stop Monitorizarea"
            btnToggleService.backgroundTintList = ColorStateList.valueOf(getColor(R.color.service_stop))
        } else {
            btnToggleService.text = "Porneste Monitorizarea"
            btnToggleService.backgroundTintList = ColorStateList.valueOf(getColor(R.color.service_start))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
