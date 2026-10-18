package com.ouri.localai

import android.content.Context
import android.os.Handler
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File

/**
 * Локальный запуск модели через MediaPipe GenAI (LiteRT-LM).
 * Всё считается на телефоне: сеть после загрузки модели не нужна.
 */
object ChatEngine {

    private var inference: LlmInference? = null
    var loadedPath: String? = null
        private set
    private lateinit var app: Context
    private var main: Handler? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        main = Handler(ctx.applicationContext.mainLooper)
    }

    private fun post(block: () -> Unit) = main?.post(block)

    fun load(file: File, onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                close()
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(1024)
                    .build()
                inference = LlmInference.createFromOptions(app, options)
                loadedPath = file.absolutePath
                post { onDone() }
            } catch (e: Exception) {
                post { onError(e.message ?: "Не удалось загрузить модель") }
            }
        }.start()
    }

    /** Шаблон чата зависит от семейства модели. */
    fun buildPrompt(family: ModelCatalog.Family, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        if (family == ModelCatalog.Family.QWEN) {
            for ((u, a) in history) sb.append("user\n").append(u).append("\nassistant\n").append(a).append("\n")
            sb.append("user\n").append(user).append("\nassistant\n")
        } else {
            for ((u, a) in history) {
                sb.append("<start_of_turn>user\n").append(u).append("<end_of_turn>\n")
                sb.append("<start_of_turn>model\n").append(a).append("<end_of_turn>\n")
            }
            sb.append("<start_of_turn>user\n").append(user).append("<end_of_turn>\n")
            sb.append("<start_of_turn>model\n")
        }
        return sb.toString()
    }

    fun generate(prompt: String, onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val llm = inference
        if (llm == null) { onError("Модель не загружена"); return }
        try {
            llm.generateResponseAsync(prompt, ProgressListener<String> { partial, done ->
                post {
                    if (!partial.isNullOrEmpty()) onToken(partial)
                    if (done) onDone()
                }
            })
        } catch (e: Exception) {
            onError(e.message ?: "Ошибка генерации")
        }
    }

    fun close() {
        runCatching { inference?.close() }
        inference = null
        loadedPath = null
    }
}
