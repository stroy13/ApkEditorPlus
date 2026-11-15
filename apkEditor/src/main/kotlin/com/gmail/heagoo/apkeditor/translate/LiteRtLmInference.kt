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
        trace.engineInfo("LiteRT-LM backend=$selectedBackend max_output_tokens=40")
        trace.stage(TranslationTrace.Stage.MODEL_READY)
    }

    override fun translate(value: String, language: String, sourceQualifier: String, resourceName: String): String {
        if (cancelled.get()) throw IOException("Translation cancelled")
        trace.stringStart(value.length, 40)
        val sourceCode = ResourceLanguageTag.languageOnlyFromQualifier(sourceQualifier)
        val sourceLanguage = sourceCode?.let {
            java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH)
        } ?: "the source language inferred from the text"
        val prompt = """Translate from $sourceLanguage into natural, concise $language UI text. Prefer wording a native app would use; do not translate literally. Preserve placeholders, tags, escapes and product names exactly. The key is context only. Return only the translated text.
Key: $resourceName
$value""".trimIndent()
        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 20, topP = 0.92, temperature = 0.32),
            maxOutputToken = 40
        )
        val first = generate(prompt, conversationConfig)
        if (cancelled.get()) throw IOException("Translation cancelled")
        trace.stage(TranslationTrace.Stage.VALIDATE)
        try {
            val cleaned = TranslationOutput.clean(value, first)
            validateLanguage(language, value, resourceName, cleaned)
            return cleaned
        } catch (rejected: TranslationRejectedException) {
            if (rejected.reason() != TranslationRejectedException.Reason.EMPTY
                    && rejected.reason() != TranslationRejectedException.Reason.PLACEHOLDER
                    && rejected.reason() != TranslationRejectedException.Reason.LANGUAGE
                    && rejected.reason() != TranslationRejectedException.Reason.REASONING) throw rejected
            // The tiny model's same-prompt retry is useful for malformed tokens but not for
            // language failures; repeating it doubles latency without improving its capability.
            if (rejected.reason() == TranslationRejectedException.Reason.LANGUAGE
                    || rejected.reason() == TranslationRejectedException.Reason.REASONING) throw rejected
            if (cancelled.get()) throw IOException("Translation cancelled")
            // A fresh conversation with the exact same short prompt avoids exposing repair instructions
            // in the returned resource string (small models often echo appended retry directions).
            val second = generate(prompt, conversationConfig)
            if (cancelled.get()) throw IOException("Translation cancelled")
            val cleaned = TranslationOutput.clean(value, second)
            validateLanguage(language, value, resourceName, cleaned)
            return cleaned
        }
    }

    private fun generate(prompt: String, config: ConversationConfig): String = kotlinx.coroutines.runBlocking {
        engine.createConversation(config).use { conversation -> conversation.sendMessage(prompt).toString() }
    }

    private fun validateLanguage(language: String, source: String, resourceName: String, output: String) {
        if (!requiresCyrillic(language, source)) return
        val lower = output.lowercase(java.util.Locale.ROOT)
        if (!TranslationOutput.containsCyrillic(output)
                || (!resourceName.isNullOrBlank() && lower.contains(resourceName.lowercase(java.util.Locale.ROOT)))
                || mostlyLatin(output))
            throw TranslationRejectedException(TranslationRejectedException.Reason.LANGUAGE, "Model output is not a clean Russian translation")
    }

    private fun mostlyLatin(text: String): Boolean {
        var latin = 0
        var cyrillic = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (!Character.isLetter(cp)) continue
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.LATIN -> latin++
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                else -> Unit
            }
        }
        return latin > cyrillic
    }

    private fun requiresCyrillic(language: String, source: String): Boolean {
        if (!language.equals("Russian", ignoreCase = true)) return false
        val latinLetters = source.codePoints().filter { Character.isLetter(it)
                && Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN }.count()
        if (latinLetters < 3) return false
        if (source.matches(Regex("(?i)^(google|android|wi-fi|apk|usb|url|gps)$"))) return false
        if (source.matches(Regex("^[A-Z0-9 _-]{2,5}$"))) return false
        return true
    }

    override fun translateBatch(items: List<common.types.StringItem>, language: String, sourceQualifier: String): List<String> =
        throw UnsupportedOperationException("LiteRT-LM uses one isolated prompt per resource")

    override fun requestCancel() { cancelled.set(true) }
    override fun close() { kotlinx.coroutines.runBlocking { engine.close() } }
}
