package com.example.act1sem3appsmov

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Helper utilitario para aplicar Edge-to-Edge nativo y seguro con WindowInsetsCompat
 * en compatibilidad total con Android 15+ (Target SDK 37) y versiones previas (minSdk 27).
 *
 * Previene:
 * 1. Solapamiento de la barra de estado del sistema (status bar) sobre el encabezado y sus títulos.
 * 2. Solapamiento de la barra de navegación del sistema (navigation bar de 3 botones o gestos)
 *    sobre botones de acción inferiores y botones flotantes (FAB).
 * 3. Recortes por cámaras o cutouts en orientación horizontal.
 */
object EdgeToEdgeHelper {

    fun applyEdgeToEdge(
        activity: ComponentActivity,
        rootView: View,
        headerView: View? = null,
        scrollContentView: View? = null,
        bottomActionView: View? = null
    ) {
        // Habilitar modo Edge-to-Edge oficial de AndroidX
        activity.enableEdgeToEdge()

        // Ajustar contraste de íconos del sistema:
        // - Barra de estado: Íconos blancos/claros para contrastar con el fondo púrpura del encabezado
        // - Barra de navegación: Íconos oscuros para contrastar con el fondo claro de la aplicación
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = true

        // Captura defensiva de los valores iniciales de padding y margin definidos en XML
        // para prevenir incrementos acumulativos si se despachan múltiples callbacks de insets.
        val initialHeaderTopPadding = headerView?.paddingTop ?: 0
        val initialScrollBottomPadding = scrollContentView?.paddingBottom ?: 0
        val initialBottomActionMarginBottom = (bottomActionView?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        val initialBottomActionMarginRight = (bottomActionView?.layoutParams as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 1. Indentación segura contra cutouts laterales en horizontal o tablets
            rootView.updatePadding(
                left = systemBars.left,
                right = systemBars.right
            )

            // 2. Encabezado superior: compensar altura exacta de la barra de estado
            headerView?.updatePadding(
                top = initialHeaderTopPadding + systemBars.top
            )

            // 3. Contenedor de desplazamiento: asegurar que el final del contenido quede visible
            // sobre la barra de navegación del sistema
            scrollContentView?.updatePadding(
                bottom = initialScrollBottomPadding + systemBars.bottom
            )

            // 4. Botón flotante o barra de acción: elevar sobre la barra de navegación
            bottomActionView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = initialBottomActionMarginBottom + systemBars.bottom
                rightMargin = initialBottomActionMarginRight + systemBars.right
            }

            windowInsets
        }
    }
}
