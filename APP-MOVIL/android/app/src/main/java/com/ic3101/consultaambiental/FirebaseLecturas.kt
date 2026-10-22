package com.ic3101.consultaambiental

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

internal object FirebaseLecturas {
    private const val RUTA = "lecturas/equipo01"

    // FirebaseInitProvider crea la app solo cuando existe la configuración local.
    private fun aplicacion(contexto: Context): FirebaseApp? =
        FirebaseApp.getApps(contexto).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }

    fun configurada(contexto: Context): Boolean =
        !aplicacion(contexto)?.options?.databaseUrl.isNullOrBlank()

    fun referencia(contexto: Context): DatabaseReference? {
        val app = aplicacion(contexto) ?: return null
        val url = app.options.databaseUrl?.takeIf { it.isNotBlank() } ?: return null
        return FirebaseDatabase.getInstance(app, url).getReference(RUTA)
    }
}
