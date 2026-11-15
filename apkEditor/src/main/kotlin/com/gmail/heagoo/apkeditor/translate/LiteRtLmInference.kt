package com.gmail.heagoo.apkeditor.translate

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** On-device generative translation. Text is passed only to the local LiteRT-LM runtime. */
class LiteRtLmInference(
    modelFile: File,
    private val trace: TranslationTrace,
    private val cancelled: AtomicBoolean
) : TranslationSession {
    private lateinit var engine: Engine
    private var selectedBackend = "GPU"

    init {
        if (!modelFile.isFile || modelFile.length() == 0L) throw IOException("Local AI model is missing")
        trace.stage(TranslationTrace.Stage.LOAD_MODEL)
        if (cancelled.get()) throw IOException("Translation cancelled")
        val gpu = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.GPU()))
        try {
            kotlinx.coroutines.runBlocking { gpu.initialize() }
            if (cancelled.get()) throw IOException("Translation cancelled")
            engine = gpu
        } catch (gpuError: Throwable) {
            try { kotlinx.coroutines.runBlocking { gpu.close() } } catch (_: Throwable) { }
            if (cancelled.get()) throw IOException("Translation cancelled", gpuError)
            val cpu = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU(threadCount = 4)))
            try {
                kotlinx.coroutines.runBlocking { cpu.initialize() }
                if (cancelled.get()) throw IOException("Translation cancelled")
                engine = cpu
                selectedBackend = "CPU-fallback"
            } catch (cpuError: Throwable) {
                try { kotlinx.coroutines.runBlocking { cpu.close() } } catch (_: Throwable) { }
                cpuError.addSuppressed(gpuError)
                throw cpuError
            }
        }
        trace.runtimeBackend(selectedBackend)
        trace.engineInfo("LiteRT-LM backend=$selectedBackend max_output_tokens=80")
        trace.stage(TranslationTrace.Stage.MODEL_READY)
    }

    override fun translate(value: String, language: String, sourceQualifier: String, resourceName: String): String {
        if (cancelled.get()) throw IOException("Translation cancelled")
        trace.stringStart(value.length, 80)
        val sourceTag = ResourceLanguageTag.fromQualifier(sourceQualifier)
        val sourceLanguage = sourceTag?.let { java.util.Locale.forLanguageTag(it).getDisplayName() } ?: "the source language inferred from the text"
        val prompt = """Translate Android UI text from $sourceLanguage to $language. Return only a concise, natural translation. Preserve placeholders, tags, escapes and formatting exactly. Keep names unchanged.
Key: $resourceName
Text: $value
Translation:""".trimIndent()
        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
            maxOutputToken = 80
        )
        val result = kotlinx.coroutines.runBlocking<String> {
            engine.createConversation(conversationConfig).use { conversation -> conversation.sendMessage(prompt).toString() }
        }
        if (cancelled.get()) throw IOException("Translation cancelled")
        trace.stage(TranslationTrace.Stage.VALIDATE)
        return TranslationOutput.clean(value, result)
    }

    override fun translateBatch(items: List<common.types.StringItem>, language: String, sourceQualifier: String): List<String> =
        throw UnsupportedOperationException("LiteRT-LM uses one isolated prompt per resource")

    override fun requestCancel() { cancelled.set(true) }
    override fun close() { kotlinx.coroutines.runBlocking { engine.close() } }
}
