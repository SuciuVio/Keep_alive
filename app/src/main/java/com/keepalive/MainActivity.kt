package com.keepalive

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : FragmentActivity() {
    private lateinit var mainContent: View
    private lateinit var editTextUrl: EditText
    private lateinit var btnAddUrl: Button
    private lateinit var btnToggleService: Button
    private lateinit var textViewLog: TextView
    private lateinit var adapter: UrlAdapter

    private val logLines = mutableListOf<String>()
    private val maxLogLines = 100
    private var isAuthenticated = false
    private var authPromptVisible = false
    private var backgroundAt = 0L

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
        if (!SignatureVerifier.isTrusted(this)) {
            showTamperWarningAndExit()
            return
        }

        setContentView(R.layout.activity_main)

        NotificationHelper.createChannels(this)
        requestNotificationPermissionIfNeeded()

        mainContent = findViewById(R.id.mainContent)
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
        lockContent()
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
        relockIfNeeded()
    }

    override fun onPause() {
        unregisterReceiver(logReceiver)
        if (!authPromptVisible) {
            backgroundAt = SystemClock.elapsedRealtime()
        }
        super.onPause()
    }

    private fun relockIfNeeded() {
        val wasAwayLongEnough = backgroundAt > 0L &&
            SystemClock.elapsedRealtime() - backgroundAt >= RELOCK_DELAY_MS
        if (!isAuthenticated || wasAwayLongEnough) {
            lockContent()
            requireAuthentication()
        }
    }

    private fun requireAuthentication() {
        if (authPromptVisible) return
        if (!AuthRepository.hasPin(this)) {
            showSetPinDialog()
            return
        }
        showBiometricOrPin()
    }

    private fun showBiometricOrPin() {
        val biometricManager = BiometricManager.from(this)
        val canUseBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canUseBiometric) {
            showPinUnlockDialog()
            return
        }

        authPromptVisible = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authPromptVisible = false
                    unlockContent()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authPromptVisible = false
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        showPinUnlockDialog()
                    } else {
                        Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                        showPinUnlockDialog()
                    }
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@MainActivity, "Autentificare biometrica esuata", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Deblocare Keep_alive")
            .setSubtitle("Confirma identitatea pentru a accesa aplicatia")
            .setNegativeButtonText("Foloseste PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun showSetPinDialog() {
        authPromptVisible = true
        val pinInput = securePinInput("PIN nou")
        val confirmInput = securePinInput("Confirma PIN")
        val layout = dialogInputLayout(pinInput, confirmInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Seteaza PIN local")
            .setMessage("PIN-ul protejeaza accesul la Keep_alive pe acest telefon.")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Salveaza", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = pinInput.text.toString()
                val confirmPin = confirmInput.text.toString()
                when {
                    !AuthRepository.isValidPin(pin) -> toast("PIN-ul trebuie sa aiba minimum 4 cifre")
                    pin != confirmPin -> toast("PIN-urile nu coincid")
                    else -> {
                        AuthRepository.savePin(this, pin)
                        authPromptVisible = false
                        dialog.dismiss()
                        unlockContent()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showPinUnlockDialog() {
        authPromptVisible = true
        val pinInput = securePinInput("PIN")
        val layout = dialogInputLayout(pinInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Deblocare cu PIN")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Deblocheaza", null)
            .setNegativeButton("Inchide") { _, _ -> finish() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = pinInput.text.toString()
                if (AuthRepository.verifyPin(this, pin)) {
                    authPromptVisible = false
                    dialog.dismiss()
                    unlockContent()
                } else {
                    toast("PIN incorect")
                    pinInput.setText("")
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                authPromptVisible = false
                dialog.dismiss()
                finish()
            }
        }
        dialog.show()
    }

    private fun securePinInput(hint: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            minHeight = 48
        }
    }

    private fun dialogInputLayout(vararg inputs: EditText): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 3
            setPadding(padding, 0, padding, 0)
            inputs.forEach { input -> addView(input) }
        }
    }

    private fun lockContent() {
        isAuthenticated = false
        if (::mainContent.isInitialized) {
            mainContent.visibility = View.INVISIBLE
        }
    }

    private fun unlockContent() {
        isAuthenticated = true
        backgroundAt = 0L
        authPromptVisible = false
        mainContent.visibility = View.VISIBLE
    }

    private fun showTamperWarningAndExit() {
        AlertDialog.Builder(this)
            .setTitle("Aplicatie neverificata")
            .setMessage("Semnatura aplicatiei nu este cea asteptata. Aplicatia se va inchide.")
            .setCancelable(false)
            .setPositiveButton("Inchide") { _, _ -> finish() }
            .show()
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val RELOCK_DELAY_MS = 15_000L
    }
}