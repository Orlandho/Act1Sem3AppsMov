package com.example.act1sem3appsmov

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de configuración dinámica para conexión MySQL / MariaDB.
 * Permite cambiar Host (10.0.2.2 para emulador, IP LAN para físico), Puerto,
 * Base de Datos, Usuario y Contraseña con persistencia en SharedPreferences.
 */
data class MySQLConfig(
    var host: String = DEFAULT_HOST,
    var port: Int = DEFAULT_PORT,
    var database: String = DEFAULT_DATABASE,
    var user: String = DEFAULT_USER,
    var password: String = DEFAULT_PASSWORD
) {
    val jdbcUrl: String
        get() = "jdbc:mariadb://$host:$port/$database?connectTimeout=4000&socketTimeout=4000"

    companion object {
        const val PREFS_NAME = "mysql_config_prefs"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_DATABASE = "database"
        const val KEY_USER = "user"
        const val KEY_PASSWORD = "password"

        const val DEFAULT_HOST = "10.0.2.2"
        const val DEFAULT_PORT = 3306
        const val DEFAULT_DATABASE = "smart_payroll"
        const val DEFAULT_USER = "root"
        const val DEFAULT_PASSWORD = ""

        fun load(context: Context): MySQLConfig {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return MySQLConfig(
                host = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST,
                port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
                database = prefs.getString(KEY_DATABASE, DEFAULT_DATABASE) ?: DEFAULT_DATABASE,
                user = prefs.getString(KEY_USER, DEFAULT_USER) ?: DEFAULT_USER,
                password = prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
            )
        }

        fun save(context: Context, config: MySQLConfig) {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_HOST, config.host)
                putInt(KEY_PORT, config.port)
                putString(KEY_DATABASE, config.database)
                putString(KEY_USER, config.user)
                putString(KEY_PASSWORD, config.password)
                apply()
            }
        }
    }
}
