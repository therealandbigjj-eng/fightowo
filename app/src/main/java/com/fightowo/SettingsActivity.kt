package com.fightowo

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Switch
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class SettingsActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("fightowo_prefs", Context.MODE_PRIVATE)

        val difficulty = findViewById<Spinner>(R.id.spinner_difficulty)
        difficulty.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Easy", "Normal", "Hard"))
        val savedDiff = prefs.getString("difficulty", "Normal")
        val idx = when (savedDiff) { "Easy" -> 0; "Hard" -> 2; else -> 1 }
        difficulty.setSelection(idx)

        val vib = findViewById<Switch>(R.id.switch_vibrate)
        vib.isChecked = prefs.getBoolean("vibration", true)

        val uiLocation = findViewById<Spinner>(R.id.spinner_ui)
        uiLocation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Top-Right", "Top-Left", "Bottom-Right", "Bottom-Left"))
        val loc = prefs.getInt("ui_gravity", (android.view.Gravity.TOP or android.view.Gravity.END))
        uiLocation.setSelection( when (loc) {
            (android.view.Gravity.TOP or android.view.Gravity.END) -> 0
            (android.view.Gravity.TOP or android.view.Gravity.START) -> 1
            (android.view.Gravity.BOTTOM or android.view.Gravity.END) -> 2
            else -> 3
        })

        val tagsField = findViewById<AutoCompleteTextView>(R.id.tags_field)
        tagsField.setText(prefs.getString("tags", ""))
        // simple autocomplete using e621 tag search (best-effort)
        tagsField.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString() ?: return
                if (q.length < 2) return
                scope.launch(Dispatchers.IO) {
                    try {
                        val url = "https://e621.net/tags.json?search=${java.net.URLEncoder.encode(q, "utf-8") }&limit=10"
                        val req = Request.Builder().url(url).header("User-Agent", "Fightowo/0.1 (by therealandbigjj-eng)").build()
                        val resp = client.newCall(req).execute()
                        val body = resp.body?.string() ?: return@launch
                        val arr = JSONArray(body)
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(o.getString("name"))
                        }
                        launch(Dispatchers.Main) {
                            val adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_dropdown_item_1line, list)
                            tagsField.setAdapter(adapter)
                            adapter.notifyDataSetChanged()
                        }
                    } catch (e: Exception) {}
                }
            }
        })

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val editor = prefs.edit()
            editor.putString("difficulty", difficulty.selectedItem as String)
            editor.putBoolean("vibration", vib.isChecked)
            val gi = when (uiLocation.selectedItemPosition) {
                0 -> android.view.Gravity.TOP or android.view.Gravity.END
                1 -> android.view.Gravity.TOP or android.view.Gravity.START
                2 -> android.view.Gravity.BOTTOM or android.view.Gravity.END
                else -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            }
            editor.putInt("ui_gravity", gi)
            editor.putString("tags", tagsField.text.toString())
            editor.apply()
            finish()
        }
    }
}
