package com.apache.security

import com.apache.tools.RiskLevel
import org.springframework.stereotype.Component

/**
 * Lo que el PermissionManager decide hacer con la ejecución de una tool.
 */
enum class PermissionDecision {
    /** Ejecutar inmediatamente sin preguntar nada al usuario. */
    EXECUTE_DIRECT,

    /** No ejecutar todavía: hay que preguntarle al usuario y esperar su confirmación explícita. */
    REQUIRES_CONFIRMATION,

    /** No se debe ejecutar bajo ningún concepto (reservado para el futuro: perfiles/roles con permisos limitados). */
    DENIED
}

/**
 * Traduce el [RiskLevel] de una tool en una [PermissionDecision] concreta.
 *
 * Este es el único sitio del proyecto donde vive la "política de seguridad".
 * Si mañana queréis, por ejemplo, que RECOVERABLE solo pida confirmación la
 * primera vez de cada sesión, o que un "modo invitado" deniegue todo lo que
 * no sea READ_ONLY, este es el sitio a tocar — el resto del agente (Agent.kt)
 * no necesita cambiar nada.
 */
@Component
class PermissionManager {

    fun decide(riskLevel: RiskLevel): PermissionDecision = when (riskLevel) {
        RiskLevel.READ_ONLY -> PermissionDecision.EXECUTE_DIRECT
        RiskLevel.REVERSIBLE -> PermissionDecision.EXECUTE_DIRECT
        RiskLevel.RECOVERABLE -> PermissionDecision.REQUIRES_CONFIRMATION
        RiskLevel.DESTRUCTIVE -> PermissionDecision.REQUIRES_CONFIRMATION
    }
}