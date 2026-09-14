package com.apache.model

// DTO que recibimos del endpoint de transcripción.
data class VoiceTranscriptionResponse(val text: String)

// DTO que enviamos al endpoint de síntesis de voz.
data class VoiceSpeakRequest(val text: String)

// DTO que recibimos del endpoint de síntesis de voz.
data class VoiceSpeechResponse(val audio: String, val sampleRate: Int)
