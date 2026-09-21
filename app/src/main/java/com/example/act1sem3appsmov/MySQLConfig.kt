package com.example.act1sem3appsmov

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de configuración para la conexión con el servidor MySQL.
 * Almacena los parámetros en SharedPreferences para permitir que el docente o usuario
 * configure el host (ej. 10.0.2.2 para emulador, 192.168.x.x para dispositivo físico),
 * puerto, credenciales y base de datos en tiempo de ejecución sin recompilar.
 */
class MySQLConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST).orEmpty().ifBlank { DEFAULT_HOST }
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, if (value > 0) value else DEFAULT_PORT).apply()

    var database: String
        get() = prefs.getString(KEY_DB, DEFAULT_DATABASE).orEmpty().ifBlank { DEFAULT_DATABASE }
        set(value) = prefs.edit().putString(KEY_DB, value.trim()).apply()

    var user: String
        get() = prefs.getString(KEY_USER, DEFAULT_USER).orEmpty().ifBlank { DEFAULT_USER }
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD).orEmpty()
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var timeoutSeconds: Int
        get() = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT, if (value in 1..30) value else DEFAULT_TIMEOUT_SECONDS).apply()

    /**
     * Restablece todos los valores a los parámetros por defecto de fábrica.
     */
    fun resetToDefaults() {
        prefs.edit()
            .putString(KEY_HOST, DEFAULT_HOST)
            .putInt(KEY_PORT, DEFAULT_PORT)
            .putString(KEY_DB, DEFAULT_DATABASE)
            .putString(KEY_USER, DEFAULT_USER)
            .putString(KEY_PASSWORD, DEFAULT_PASSWORD)
            .putInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)
            .apply()
    }

    /**
     * Construye la URL JDBC segura con timeouts defensivos para evitar bloqueos.
     */
    fun buildJdbcUrl(): String {
        val ms = timeoutSeconds * 1000
        return "jdbc:mariadb://$host:$port/$database?connectTimeout=$ms&socketTimeout=${ms + 2000}&useSSL=false&allowPublicKeyRetrieval=true&tcpKeepAlive=true"
    }

    companion object {
        private const val PREFS_NAME = "mysql_connection_prefs"

        private const val KEY_HOST = "mysql_host"
        private const val KEY_PORT = "mysql_port"
        private const val KEY_DB = "mysql_database"
        private const val KEY_USER = "mysql_user"
        private const val KEY_PASSWORD = "mysql_password"
        private const val KEY_TIMEOUT = "mysql_timeout"

        const val DEFAULT_HOST = "10.0.2.2" // Loopback host en Android Emulator hacia PC local
        const val DEFAULT_PORT = 3306
        const val DEFAULT_DATABASE = "smart_payroll"
        const val DEFAULT_DB = DEFAULT_DATABASE
        const val DEFAULT_USER = "root"
        const val DEFAULT_PASSWORD = ""
        const val DEFAULT_TIMEOUT_SECONDS = 3 // 3 segundos para respuesta rápida sin congelar la app
    }
}
