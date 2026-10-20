package com.ic3101.consultaambiental

/** Coordenadas aproximadas del centro de cada zona; no requieren permiso de GPS. */
internal data class ZonaClimatica(
    val id: String,
    val nombre: String,
    val region: String,
    val latitud: Double,
    val longitud: Double
) {
    val origen: OrigenDatos get() = OrigenDatos(
        "Open-Meteo · datos modelados", "$nombre, $region, Costa Rica",
        latitud, longitud, urlProveedorClima, licenciaClima
    )
}

// Los identificadores se usan en preferencias, archivos privados y nombres de CSV.
// Añade zonas aquí sin cambiar los identificadores de las que ya existen.
internal val zonasClimaticas = listOf(
    ZonaClimatica("santa_clara", "Santa Clara", "San Carlos", 10.36, -84.51),
    ZonaClimatica("san_jose", "San José", "San José", 9.93, -84.08),
    ZonaClimatica("alajuela", "Alajuela", "Alajuela", 10.02, -84.21),
    ZonaClimatica("cartago", "Cartago", "Cartago", 9.86, -83.92),
    ZonaClimatica("heredia", "Heredia", "Heredia", 10.00, -84.12),
    ZonaClimatica("liberia", "Liberia", "Guanacaste", 10.63, -85.44),
    ZonaClimatica("puntarenas", "Puntarenas", "Puntarenas", 9.98, -84.84),
    ZonaClimatica("limon", "Limón", "Limón", 9.99, -83.04),
    ZonaClimatica("la_fortuna", "La Fortuna", "San Carlos", 10.47, -84.65),
    ZonaClimatica("perez_zeledon", "San Isidro", "Pérez Zeledón", 9.37, -83.70)
)
internal val santaClara = zonasClimaticas.first()
