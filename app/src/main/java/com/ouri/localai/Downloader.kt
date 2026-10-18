package com.ouri.localai

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Загрузчик моделей: честный прогресс, докачка не поддерживается (файлы
 * монолитные), отмена — кнопкой. Пишем в .part, переименовываем по факту.
 */
class Downloader(private val context: Context) {

    interface Callback {
        fun onProgress(percent: Int, downloadedMb: Long, totalMb: Long)
        fun onDone(file: File)
        fun onError(message: String)
    }

    private val tasks = ConcurrentHashMap<String, Task>()

    private class Task(val thread: Thread, @Volatile var cancelled: Boolean = false)

    fun modelsDir(): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(model: ModelCatalog.Model): File = File(modelsDir(), ModelCatalog.fileName(model))

    fun isDownloaded(model: ModelCatalog.Model): Boolean = fileFor(model).let { it.exists() && it.length() > 1_000_000 }

    fun isRunning(id: String): Boolean = tasks[id]?.thread?.isAlive == true

    fun cancel(id: String) { tasks[id]?.cancelled = true }

    fun download(model: ModelCatalog.Model, cb: Callback) {
        if (isRunning(model.id)) return
        val dest = fileFor(model)
        val part = File(dest.parentFile, dest.name + ".part")
        val t = Thread({
            var conn: HttpURLConnection? = null
            var input: InputStream? = null
            var out: FileOutputStream? = null
            try {
                conn = URL(model.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    post { cb.onError("Сервер отдал код ${conn.responseCode}") }
                    return@Thread
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: model.bytes
                input = conn.inputStream
                out = FileOutputStream(part)
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var lastReport = 0L
                while (true) {
                    val task = tasks[model.id]
                    if (task?.cancelled == true) {
                        post { cb.onError("Отменено") }
                        return@Thread
                    }
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (read - lastReport > 512 * 1024 || read == total) {
                        lastReport = read
                        val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                        post { cb.onProgress(pct, read / 1_048_576, total / 1_048_576) }
                    }
                }
                out.flush()
                out.close(); out = null
                input.close(); input = null
                if (!part.renameTo(dest)) {
                    post { cb.onError("Не удалось сохранить файл") }
                    return@Thread
                }
                post { cb.onDone(dest) }
            } catch (e: Exception) {
                post { cb.onError(e.message ?: "Ошибка сети") }
            } finally {
                runCatching { input?.close() }
                runCatching { out?.close() }
                runCatching { conn?.disconnect() }
                if (tasks[model.id]?.cancelled == true) part.delete()
                tasks.remove(model.id)
            }
        }, "dl-${model.id}")
        tasks[model.id] = Task(t)
        t.start()
    }

    fun delete(model: ModelCatalog.Model) {
        fileFor(model).delete()
        File(fileFor(model).path + ".part").delete()
    }

    private val main = android.os.Handler(context.mainLooper)
    private fun post(block: () -> Unit) = main.post(block)
}
