// GeminiLiveModels.kt
//
//  – Adds GenerationComplete & GoAway events
//  – Parameter type for lookup_session_history.limit is "integer"
//  – Optional `behavior` on FunctionDeclaration for NON_BLOCKING calls
//
package com.di.feature_trainer.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/* ─────────────  GENERATION CONFIG  ───────────── */
@Serializable
data class GenerationConfig(
    val candidateCount: Int = 1,
    val maxOutputTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val responseModalities: List<String> = listOf("AUDIO"),
    val speechConfig: SpeechConfig? = null
)

/* ─────────────  LIVE-SESSION CONFIG  ───────────── */
@Serializable
data class LiveSessionConfig(
    val model: String = "models/gemini-live-2.5-flash-preview",
    val generationConfig: GenerationConfig = GenerationConfig(),
    val tools: List<Tool> = emptyList(),
    val systemInstruction: SystemInstruction,
    val realtimeInputConfig: RealtimeInputConfig = RealtimeInputConfig(),
    val contextWindowCompression: ContextWindowCompression = ContextWindowCompression(),
    val inputAudioTranscription: EmptyObject = EmptyObject(),
    val outputAudioTranscription: EmptyObject = EmptyObject(),
    val sessionResumption: SessionResumptionConfig? = null
)

/* ─────────────  VAD / CONTEXT CONFIG  ───────────── */
@Serializable
data class RealtimeInputConfig(
    val automaticActivityDetection: AutomaticActivityDetection = AutomaticActivityDetection()
)

@Serializable
data class AutomaticActivityDetection(
    val disabled: Boolean = false,
    val endOfSpeechSensitivity: String = "END_SENSITIVITY_HIGH",
    val silenceDurationMs: Int = 1000,
    val prefixPaddingMs: Int = 100
)

@Serializable
data class ContextWindowCompression(
    val slidingWindow: SlidingWindow = SlidingWindow(),
    val triggerTokens: Int = 25_600
)

@Serializable
data class SlidingWindow(val targetTokens: Int = 16_000)

@Serializable
data class SessionResumptionConfig(val handle: String)

/* ─────────────  TOOL DECLARATIONS  ───────────── */
@Serializable
data class Tool(val functionDeclarations: List<FunctionDeclaration>)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Parameters? = null,
    /**  OPTIONAL: "NON_BLOCKING" for async calls */
    val behavior: String? = null
)

@Serializable
data class Parameters(
    val type: String = "object",
    val properties: Map<String, Property> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class Property(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

/* ─────────────  SYSTEM PROMPT  ───────────── */
@Serializable
data class SystemInstruction(val parts: List<Part>)

/* ─────────────  CONTENT / INLINE DATA  ───────────── */
@Serializable
data class InlineData(val data: String, val mimeType: String)

@Serializable
data class Part(val text: String? = null, val inlineData: InlineData? = null)

/* ─────────────  SPEECH CONFIG  ───────────── */
@Serializable
data class SpeechConfig(
    val voiceConfig: VoiceConfig? = null,
    val languageCode: String? = null
)

@Serializable
data class VoiceConfig(val prebuiltVoiceConfig: PrebuiltVoiceConfig? = null)

@Serializable
data class PrebuiltVoiceConfig(val voiceName: String)

/* ─────────────  CLIENT → SERVER  ───────────── */
@Serializable
data class ClientMessage(
    val realtimeInput: RealtimeInput? = null,
    val clientContent: ClientContent? = null,
    val toolResponse: ToolResponse? = null
)

@Serializable
data class RealtimeInput(
    val audio: AudioInput? = null,
    val audioStreamEnd: Boolean? = null,
    val activityStart: Boolean? = null,
    val activityEnd: Boolean? = null
)

@Serializable
data class AudioInput(
    val data: String,
    val mimeType: String = "audio/pcm;rate=16000"
)

@Serializable
data class ClientContent(
    val turns: List<Turn>,
    val turnComplete: Boolean = true
)

@Serializable
data class Turn(val role: String, val parts: List<Part>)

/* ─────────────  TOOL RESPONSE  ───────────── */
@Serializable
data class ToolResponse(val functionResponses: List<FunctionResponse>)

@Serializable
data class FunctionResponse(
    val id: String,
    val name: String,
    val response: Map<String, JsonElement>
)

/* ─────────────  SERVER → CLIENT  ───────────── */
@Serializable
data class ServerMessage(
    val setupComplete: EmptyObject? = null,
    val serverContent: ServerContent? = null,
    val toolCall: ToolCall? = null,
    val data: String? = null,
    val usageMetadata: UsageMetadata? = null,
    val sessionResumptionUpdate: SessionResumptionUpdate? = null,
    val goAway: GoAway? = null
)

@Serializable
data class ServerContent(
    val modelTurn: ModelTurn? = null,
    val turnComplete: Boolean? = null,
    val interrupted: Boolean? = null,
    val inputTranscription: Transcription? = null,
    val outputTranscription: Transcription? = null,
    val generationComplete: Boolean? = null
)

@Serializable
data class ModelTurn(val parts: List<Part>)

@Serializable
data class Transcription(val text: String)

/* ─────────────  TOOL CALL  ───────────── */
@Serializable
data class ToolCall(
    @SerialName("functionCalls") val functionCalls: List<FunctionCall>
)

@Serializable
data class FunctionCall(
    val id: String,
    val name: String,
    val args: Map<String, JsonElement>? = null
)

/* ─────────────  USAGE / SESSION META  ───────────── */
@Serializable
data class UsageMetadata(
    val totalTokenCount: Int,
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null
)

@Serializable
data class SessionResumptionUpdate(
    val resumable: Boolean,
    val newHandle: String? = null
)

@Serializable
data class GoAway(val timeLeft: String)

/* ─────────────  EVENTS surfaced to ViewModel  ───────────── */
sealed class LiveApiEvent {
    object ConnectionOpened : LiveApiEvent()
    data class AudioReceived(val base64Data: String) : LiveApiEvent()
    data class UserTranscriptUpdated(val text: String, val isFinal: Boolean) : LiveApiEvent()
    data class AiTranscriptUpdated(val text: String, val isFinal: Boolean) : LiveApiEvent()
    data class ToolCallReceived(val toolCall: ToolCall) : LiveApiEvent()
    data class SessionHandleUpdated(val handle: String) : LiveApiEvent()
    data class Error(val message: String) : LiveApiEvent()
    object ConnectionClosed : LiveApiEvent()
    data class Interrupted(val reason: String) : LiveApiEvent()

    /* new */
    class GenerationComplete : LiveApiEvent()
    data class GoAway(val timeLeft: String) : LiveApiEvent()
}

/* ─────────────  CLIENT SETUP WRAPPER  ───────────── */
@Serializable
data class ClientSetupMessage(val setup: LiveSessionConfig)

/* ─────────────  EMPTY OBJECT PLACE-HOLDER  ───────────── */
@Serializable
class EmptyObject