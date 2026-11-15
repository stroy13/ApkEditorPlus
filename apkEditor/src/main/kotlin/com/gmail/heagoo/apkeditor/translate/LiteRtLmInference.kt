package com.gmail.heagoo.apkeditor.translate

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** On-device generative translation. Text is passed only to the local LiteRT-LM runtime. */
class LiteRtLmInference(
    modelFile: File,
    private val trace: TranslationTrace,
    private val cancelled: AtomicBoolean
) : TranslationSession {
    private val engine: Engine

    init {
        if (!modelFile.isFile || modelFile.length() == 0L) throw IOException("Local AI model is missing")
        trace.stage(TranslationTrace.Stage.LOAD_MODEL)
        engine = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU()))
        try {
            if (cancelled.get()) throw IOException("Translation cancelled")
            kotlinx.coroutines.runBlocking { engine.initialize() }
            if (cancelled.get()) throw IOException("Translation cancelled")
            trace.stage(TranslationTrace.Stage.MODEL_READY)
        } catch (e: Throwable) {
            kotlinx.coroutines.runBlocking { engine.close() }
            throw e
        }
    }

    override fun translate(value: String, language: String, sourceQualifier: String, resourceName: String): String {
        if (cancelled.get()) throw IOException("Translation cancelled")
        trace.stringStart(value.length, 0)
        val sourceTag = ResourceLanguageTag.fromQualifier(sourceQualifier)
        val sourceLanguage = sourceTag?.let { java.util.Locale.forLanguageTag(it).getDisplayName() } ?: "the source language inferred from the text"
        val prompt = """Translate this Android app UI string from $sourceLanguage into $language.
Write only the natural, concise translation, with no explanation or quotation marks.
Preserve every placeholder, escape, tag, and formatting token exactly. Keep product names unchanged.
Resource key: $resourceName
Source: $value
Translation:""".trimIndent()
        val result = kotlinx.coroutines.runBlocking {
            engine.createConversation().use { conversation -> conversation.sendMessage(prompt).text }
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
