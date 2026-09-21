package com.example.act1sem3appsmov

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.act1sem3appsmov.databinding.ActivityMysqlConfigBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MySQLConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMysqlConfigBinding
    private lateinit var repository: PayrollRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMysqlConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PayrollRepository(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadConfig()
        setupListeners()
    }

    private fun loadConfig() {
        val config = MySQLConfig.load(this)
        binding.etHost.setText(config.host)
        binding.etPort.setText(config.port.toString())
        binding.etDatabase.setText(config.database)
        binding.etUser.setText(config.user)
        binding.etPassword.setText(config.password)
    }

    private fun setupListeners() {
        binding.btnSaveConfig.setOnClickListener {
            saveConfig()
            Toast.makeText(this, "✅ Configuración guardada", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestConnection.setOnClickListener {
            saveConfig()
            binding.btnTestConnection.isEnabled = false
            lifecycleScope.launch {
                val dbManager = MySQLDatabaseManager(this@MySQLConfigActivity)
                val result = dbManager.testConnection()
                binding.btnTestConnection.isEnabled = true
                if (result.isSuccess && result.getOrDefault(false)) {
                    Snackbar.make(binding.root, "🟢 Conexión a MySQL Exitosa", Snackbar.LENGTH_LONG).show()
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Error de conexión"
                    Snackbar.make(binding.root, "🔴 Falló conexión a MySQL: $err", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Ok") {}
                        .show()
                }
            }
        }

        binding.btnSyncNow.setOnClickListener {
            saveConfig()
            binding.btnSyncNow.isEnabled = false
            lifecycleScope.launch {
                val syncResult = repository.syncPendingRecords()
                binding.btnSyncNow.isEnabled = true
                if (syncResult.totalPending == 0) {
                    Toast.makeText(this@MySQLConfigActivity, "✓ No hay registros pendientes de sincronizar", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = "Sincronizados: ${syncResult.syncedCount}/${syncResult.totalPending} | Errores: ${syncResult.failedCount}"
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveConfig() {
        val host = binding.etHost.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_HOST }
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: MySQLConfig.DEFAULT_PORT
        val database = binding.etDatabase.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_DATABASE }
        val user = binding.etUser.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_USER }
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        val config = MySQLConfig(
            host = host,
            port = port,
            database = database,
            user = user,
            password = password
        )
        MySQLConfig.save(this, config)
    }
}
