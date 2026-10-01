package com.apache.tools.smarthome

import com.apache.tools.RiskLevel
import com.apache.tools.Tool
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.text.Normalizer
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.springframework.stereotype.Component

/** Un dispositivo controlable por MQTT (luz, enchufe, ESP32...). */
data class SmartDevice(
    val id: String,
    val name: String,
    /** Topic al que se publican las órdenes, ej: "casa/salon/luz/set". */
    val topic: String,
    val onPayload: String = "ON",
    val offPayload: String = "OFF",
    val togglePayload: String = "TOGGLE",
    val room: String? = null
)

/** Contenido de ~/.apache/smarthome.json. */
data class SmartHomeConfig(
    val broker: String,
    val username: String? = null,
    val password: String? = null,
    val devices: List<SmartDevice> = emptyList()
)

/**
 * Publica órdenes MQTT a los dispositivos de casa.
 *
 * Es opcional: solo funciona si existe ~/.apache/smarthome.json (ver
 * docs/smarthome.example.json). El archivo se vuelve a leer en cada orden,
 * así que se pueden añadir dispositivos sin reiniciar Apache.
 */
@Component
class SmartHomeService {

    private val configFile = File(File(System.getProperty("user.home"), ".apache"), "smarthome.json")
    private val mapper = ObjectMapper().findAndRegisterModules()

    private var client: MqttClient? = null
    private var clientBroker: String? = null

    val configPath: String
        get() = configFile.absolutePath

    fun loadConfig(): SmartHomeConfig? =
        if (!configFile.exists()) null else mapper.readValue<SmartHomeConfig>(configFile)

    /** Busca un dispositivo por id o por nombre (sin tildes ni mayúsculas, coincidencia parcial). */
    fun findDevice(config: SmartHomeConfig, query: String): SmartDevice? {
        val q = normalize(query)
        return config.devices.firstOrNull { normalize(it.id) == q || normalize(it.name) == q }
            ?: config.devices.firstOrNull { normalize(it.name).contains(q) || q.contains(normalize(it.name)) }
    }

    @Synchronized
    fun publish(config: SmartHomeConfig, topic: String, payload: String) {
        val mqtt = connectedClient(config)
        mqtt.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 1 })
    }

    private fun connectedClient(config: SmartHomeConfig): MqttClient {
        val existing = client
        if (existing != null && existing.isConnected && clientBroker == config.broker) return existing

        try {
            existing?.disconnect()
        } catch (_: Exception) {
        }

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 5
            config.username?.takeIf { it.isNotBlank() }?.let { userName = it }
            config.password?.takeIf { it.isNotBlank() }?.let { password = it.toCharArray() }
        }

        val created = MqttClient(config.broker, "apache-core-" + System.currentTimeMillis(), MemoryPersistence())
        created.connect(options)

        client = created
        clientBroker = config.broker
        return created
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/** Tool para encender, apagar y controlar dispositivos de casa por MQTT. */
@Component
class SmartHomeTool(private val smartHome: SmartHomeService) : Tool {

    override val name = "smartHome"

    override val description =
        "Controla los dispositivos de casa del usuario (luces, enchufes, ESP32...) por MQTT: " +
            "listar los dispositivos, encender, apagar, alternar o enviar un valor (ej. brillo). " +
            "Si no sabes qué dispositivos hay, usa primero action 'list'."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "enum" to listOf("list", "on", "off", "toggle", "set"),
                "description" to "Qué hacer."
            ),
            "device" to mapOf(
                "type" to "string",
                "description" to "Id o nombre del dispositivo (no hace falta para 'list')."
            ),
            "value" to mapOf(
                "type" to "string",
                "description" to "Valor a enviar con action 'set', ej: '50' para el brillo."
            )
        ),
        "required" to listOf("action")
    )

    override fun execute(args: Map<String, Any?>): String {
        val config = try {
            smartHome.loadConfig()
        } catch (e: Exception) {
            return "El archivo ${smartHome.configPath} tiene un error: ${e.message}"
        } ?: return "Todavía no hay dispositivos de casa configurados. Hay que crear ${smartHome.configPath} " +
            "(hay un ejemplo en docs/smarthome.example.json del proyecto)."

        val action = (args["action"] as? String)?.trim()?.lowercase() ?: "list"

        if (action == "list") {
            if (config.devices.isEmpty()) return "No hay dispositivos en ${smartHome.configPath}."
            return "Dispositivos de casa:\n" + config.devices.joinToString("\n") { device ->
                "- ${device.name} (id: ${device.id}${device.room?.let { ", $it" } ?: ""})"
            }
        }

        val query = (args["device"] as? String)?.trim()
        if (query.isNullOrBlank()) return "¿Qué dispositivo?"

        val device = smartHome.findDevice(config, query)
            ?: return "No conozco ningún dispositivo llamado «$query». Dispositivos: " +
                config.devices.joinToString { it.name }

        val payload = when (action) {
            "on" -> device.onPayload
            "off" -> device.offPayload
            "toggle" -> device.togglePayload
            "set" -> (args["value"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                ?: (args["value"] as? Number)?.toString()
                ?: return "Falta el valor a enviar."
            else -> return "Acción '$action' no reconocida."
        }

        return try {
            smartHome.publish(config, device.topic, payload)
            when (action) {
                "on" -> "${device.name} encendido."
                "off" -> "${device.name} apagado."
                "toggle" -> "${device.name} cambiado."
                else -> "${device.name}: enviado $payload."
            }
        } catch (e: Exception) {
            "No he podido hablar con el broker MQTT (${config.broker}): ${e.message}"
        }
    }
}
