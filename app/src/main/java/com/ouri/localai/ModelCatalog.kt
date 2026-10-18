package com.ouri.localai

/**
 * Каталог моделей для локального запуска (LiteRT-LM / MediaPipe GenAI).
 * Все модели открытые и бесплатные; источники — Hugging Face.
 *
 * Честная заметка про Gemma 2: Google изъял Gemma 2 из всех публичных
 * on-device сборок (.litertlm/.task), поэтому в каталоге — Gemma 3n,
 * прямое продолжение линейки Gemma 2 для телефонов.
 */
object ModelCatalog {

    data class Model(
        val id: String,
        val title: String,
        val family: Family,          // влияет на шаблон чата
        val url: String,
        val bytes: Long,
        val note: String
    )

    enum class Family { QWEN, GEMMA }

    private const val HF = "https://huggingface.co"

    val MODELS: List<Model> = listOf(
        Model(
            id = "qwen25-15b-q8",
            title = "Qwen2.5 1.5B Instruct · INT8",
            family = Family.QWEN,
            url = "$HF/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
                  "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            bytes = 1_600_000_000L,
            note = "Хороший баланс качества и размера. Универсальный ассистент."
        ),
        Model(
            id = "qwen3-06b-int4",
            title = "Qwen3 0.6B · INT4",
            family = Family.QWEN,
            url = "$HF/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
            bytes = 500_000_000L,
            note = "Самая лёгкая: быстрая даже на слабых телефонах."
        ),
        Model(
            id = "gemma3n-e2b-int4",
            title = "Gemma 3n E2B IT · INT4",
            family = Family.GEMMA,
            url = "$HF/bhrum108/gemma-3n-E2B-it-litertlm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
            bytes = 3_660_000_000L,
            note = "Линейка Gemma (преемница Gemma 2 для устройств). " +
                   "Большой файл: скачивание займёт заметное время."
        )
    )

    fun byId(id: String?): Model? = MODELS.firstOrNull { it.id == id }

    fun fileName(m: Model): String = m.id + ".litertlm"
}
