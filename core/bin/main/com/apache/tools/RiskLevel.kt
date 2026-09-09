package com.apache.tools

/**
 * Nivel de riesgo de una [Tool].
 *
 * Esta es la pieza central del sistema de permisos: en lugar de decidir
 * "¿puedo ejecutar esto?" herramienta por herramienta, cada tool declara
 * un nivel, y es el [com.apache.core.permissions.PermissionManager] quien
 * aplica UNA política común por nivel. Así, añadir una herramienta nueva
 * en el futuro (Fase 3, control del PC) no exige rediseñar el sistema de
 * permisos: solo hay que clasificarla en uno de estos niveles.
 */
enum class RiskLevel {
    /** Solo lectura. No modifica nada. Ej: getCurrentTime, getSystemInfo. Se ejecuta sin preguntar. */
    READ_ONLY,

    /** Efecto real pero benigno y fácilmente deshacible. Ej: abrir una app, subir el volumen. Se ejecuta y se registra en el log. */
    REVERSIBLE,

    /** Efecto real, recuperable pero no trivial. Ej: mover un archivo. Se pide confirmación la primera vez o según preferencias. */
    RECOVERABLE,

    /** Acción destructiva o irreversible. Ej: borrar archivos, desinstalar software. SIEMPRE requiere confirmación explícita. */
    DESTRUCTIVE
}