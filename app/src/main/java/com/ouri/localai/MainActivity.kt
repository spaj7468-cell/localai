package com.ouri.localai

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : android.app.Activity() {

    private lateinit var downloader: Downloader

    private lateinit var chatPanel: View
    private lateinit var modelsPanel: View
    private lateinit var chatLog: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var modelLabel: TextView
    private lateinit var inputText: EditText
    private lateinit var sendBtn: ImageButton
    private lateinit var modelsList: LinearLayout

    private val history = mutableListOf<Pair<String, String>>()
    private var activeModel: ModelCatalog.Model? = null
    private var generating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        downloader = Downloader(this)
        ChatEngine.init(this)

        chatPanel = findViewById(R.id.chatPanel)
        modelsPanel = findViewById(R.id.modelsPanel)
        chatLog = findViewById(R.id.chatLog)
        chatScroll = findViewById(R.id.chatScroll)
        modelLabel = findViewById(R.id.modelLabel)
        inputText = findViewById(R.id.inputText)
        sendBtn = findViewById(R.id.sendBtn)
        modelsList = findViewById(R.id.modelsList)

        val prefs = getSharedPreferences("ouri", Context.MODE_PRIVATE)
        activeModel = ModelCatalog.byId(prefs.getString("active", null))

        sendBtn.setOnClickListener { send() }

        val tabChat = findViewById<TextView>(R.id.tabChat)
        val tabModels = findViewById<TextView>(R.id.tabModels)
        fun showTab(chat: Boolean) {
            chatPanel.visibility = if (chat) View.VISIBLE else View.GONE
            modelsPanel.visibility = if (chat) View.GONE else View.VISIBLE
            tabChat.setBackgroundResource(if (chat) R.color.surface2 else R.color.surface)
            tabModels.setBackgroundResource(if (chat) R.color.surface else R.color.surface2)
            tabChat.setTextColor(getColor(if (chat) R.color.text else R.color.muted))
            tabModels.setTextColor(getColor(if (chat) R.color.muted else R.color.text))
            if (!chat) refreshModels()
        }
        tabChat.setOnClickListener { showTab(true) }
        tabModels.setOnClickListener { showTab(false) }
        showTab(true)
        updateModelLabel()
        addSystem(getString(R.string.welcome))
    }

    /* ================= чат ================= */

    private fun updateModelLabel() {
        modelLabel.text = activeModel?.title ?: getString(R.string.no_model)
    }

    private fun addBubble(text: String, user: Boolean): TextView {
        val v = TextView(this)
        v.text = text
        v.setPadding(dp(14), dp(10), dp(14), dp(10))
        v.setBackgroundResource(if (user) R.drawable.bg_bubble_user else R.drawable.bg_bubble_ai)
        v.setTextColor(getColor(R.color.text))
        v.textSize = 15f
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(if (user) 48 else 8), dp(4), dp(if (user) 8 else 48), dp(4))
        chatLog.addView(v, lp)
        return v
    }

    private fun addSystem(text: String) {
        val v = TextView(this)
        v.text = text
        v.setTextColor(getColor(R.color.muted))
        v.textSize = 12.5f
        v.setPadding(dp(10), dp(6), dp(10), dp(6))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(6), 0, dp(6))
        chatLog.addView(v, lp)
        scrollDown()
    }

    private fun scrollDown() = chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }

    private fun send() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty() || generating) return
        val model = activeModel
        if (model == null || !downloader.isDownloaded(model)) {
            Toast.makeText(this, R.string.pick_model_first, Toast.LENGTH_LONG).show()
            switchToModels()
            return
        }
        val file = downloader.fileFor(model)
        inputText.setText("")
        addBubble(text, true)
        generating = true
        sendBtn.isEnabled = false

        val start = {
            val prompt = ChatEngine.buildPrompt(model.family, history.takeLast(6), text)
            val bubble = addBubble("", false)
            val acc = StringBuilder()
            ChatEngine.generate(prompt,
                onToken = { tok -> acc.append(tok); bubble.text = acc.toString(); scrollDown() },
                onDone = {
                    history.add(text to acc.toString())
                    generating = false; sendBtn.isEnabled = true; scrollDown()
                },
                onError = { msg ->
                    bubble.text = getString(R.string.gen_error, msg)
                    generating = false; sendBtn.isEnabled = true
                })
        }

        if (ChatEngine.loadedPath == file.absolutePath) start()
        else {
            addSystem(getString(R.string.loading_model))
            ChatEngine.load(file,
                onDone = { addSystem(getString(R.string.model_ready, model.title)); start() },
                onError = { msg ->
                    addSystem(getString(R.string.gen_error, msg))
                    generating = false; sendBtn.isEnabled = true
                })
        }
    }

    /* ================= модели ================= */

    private fun refreshModels() {
        modelsList.removeAllViews()
        for (m in ModelCatalog.MODELS) modelsList.addView(modelRow(m))
    }

    private fun modelRow(m: ModelCatalog.Model): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_model, modelsList, false)
        val title = row.findViewById<TextView>(R.id.mTitle)
        val note = row.findViewById<TextView>(R.id.mNote)
        val status = row.findViewById<TextView>(R.id.mStatus)
        val progress = row.findViewById<ProgressBar>(R.id.mProgress)
        val btnDl = row.findViewById<Button>(R.id.mDownload)
        val btnDel = row.findViewById<Button>(R.id.mDelete)

        title.text = m.title
        title.typeface = Typeface.DEFAULT_BOLD
        note.text = m.note

        fun paint() {
            val downloaded = downloader.isDownloaded(m)
            val running = downloader.isRunning(m.id)
            progress.visibility = if (running) View.VISIBLE else View.GONE
            btnDel.visibility = if (downloaded) View.VISIBLE else View.GONE
            btnDl.text = when {
                running -> getString(R.string.cancel)
                downloaded -> getString(R.string.downloaded)
                else -> getString(R.string.download)
            }
            btnDl.isEnabled = !downloaded || running
            val mb = m.bytes / 1_048_576
            status.text = when {
                running -> getString(R.string.downloading)
                downloaded -> getString(R.string.ready_size, mb) +
                        if (activeModel?.id == m.id) " · " + getString(R.string.active) else ""
                else -> getString(R.string.size_mb, mb)
            }
            row.alpha = if (activeModel?.id == m.id) 1f else 0.85f
        }
        paint()

        btnDl.setOnClickListener {
            if (downloader.isRunning(m.id)) {
                downloader.cancel(m.id); paint(); return@setOnClickListener
            }
            status.text = getString(R.string.starting)
            progress.visibility = View.VISIBLE
            progress.progress = 0
            btnDl.text = getString(R.string.cancel)
            downloader.download(m, object : Downloader.Callback {
                override fun onProgress(percent: Int, downloadedMb: Long, totalMb: Long) {
                    progress.progress = percent
                    status.text = getString(R.string.progress, percent, downloadedMb, totalMb)
                }
                override fun onDone(file: File) {
                    Toast.makeText(this@MainActivity, R.string.done_hint, Toast.LENGTH_LONG).show()
                    refreshModels()
                }
                override fun onError(message: String) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    refreshModels()
                }
            })
        }
        btnDel.setOnClickListener {
            downloader.delete(m)
            if (activeModel?.id == m.id) {
                activeModel = null
                getSharedPreferences("ouri", MODE_PRIVATE).edit().remove("active").apply()
                ChatEngine.close()
                updateModelLabel()
            }
            refreshModels()
        }
        row.setOnClickListener {
            if (!downloader.isDownloaded(m)) {
                Toast.makeText(this, R.string.download_first, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            activeModel = m
            getSharedPreferences("ouri", MODE_PRIVATE).edit().putString("active", m.id).apply()
            updateModelLabel()
            refreshModels()
            Toast.makeText(this, getString(R.string.model_active, m.title), Toast.LENGTH_SHORT).show()
        }
        return row
    }

    private fun switchToModels() {
        findViewById<TextView>(R.id.tabModels).performClick()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        ChatEngine.close()
        super.onDestroy()
    }
}
