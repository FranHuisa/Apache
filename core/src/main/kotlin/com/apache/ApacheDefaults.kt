package com.apache

/**
 * Valores por defecto compartidos por toda la aplicación mientras Apache no
 * tiene gestión real de usuarios y sesiones.
 *
 * Apache 0.1 utiliza inicialmente el usuario `default`, creado durante la
 * configuración inicial de MySQL, que corresponde al id 1.
 *
 * Centralizar esta constante evita tener el número "1L" repetido y sin
 * explicar en Agent, las tools de calendario/tareas/recordatorios y los
 * controladores REST. Cuando Apache tenga sesiones de usuario reales, este
 * es el único sitio que habrá que tocar para dejar de asumir un usuario fijo.
 */
object ApacheDefaults {
    const val DEFAULT_USER_ID: Long = 1L
}
