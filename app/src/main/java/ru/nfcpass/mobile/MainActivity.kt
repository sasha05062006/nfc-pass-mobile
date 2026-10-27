package ru.nfcpass.mobile

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScanRecord(
    val title: String,
    val uid: String,
    val technologies: String,
    val ndef: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private var nfcAdapter: NfcAdapter? = null
    private val records = mutableStateListOf<ScanRecord>()
    private var scanMessage by mutableStateOf("Нажми «Сканировать» и приложи метку к задней стороне телефона.")
    private var scanning by mutableStateOf(false)
    private var lastScan by mutableStateOf<ScanRecord?>(null)
    private var selectedRecord by mutableStateOf<ScanRecord?>(null)
    private var currentPage by mutableStateOf("cards")
    private var renameTarget by mutableStateOf<ScanRecord?>(null)
    private var renameText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        loadRecords()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.width(116.dp).fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("NFC PASS", style = MaterialTheme.typography.titleMedium)
                            Divider()
                            if (currentPage == "cards") {
                                Button(onClick = { currentPage = "cards" }, modifier = Modifier.fillMaxWidth()) { Text("Мои карты") }
                            } else {
                                OutlinedButton(onClick = { currentPage = "cards" }, modifier = Modifier.fillMaxWidth()) { Text("Мои карты") }
                            }
                            if (currentPage == "scan") {
                                Button(onClick = { currentPage = "scan" }, modifier = Modifier.fillMaxWidth()) { Text("Сканировать") }
                            } else {
                                OutlinedButton(onClick = { currentPage = "scan" }, modifier = Modifier.fillMaxWidth()) { Text("Сканировать") }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Сохранено: ${records.size}", style = MaterialTheme.typography.bodySmall)
                        }

                        Column(
                            modifier = Modifier.weight(1f).fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("NFC Pass Mobile", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (nfcAdapter?.isEnabled == true) "NFC включён" else if (nfcAdapter == null) "NFC не поддерживается" else "Включи NFC в настройках телефона",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (currentPage == "scan") {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Добавить карту", style = MaterialTheme.typography.titleLarge)
                                        Text("1. Нажми сканирование. 2. Приложи метку. 3. Проверь данные и задай своё название.")
                                        Text(scanMessage)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { startScan() }, enabled = nfcAdapter?.isEnabled == true && !scanning) { Text("Сканировать") }
                                            OutlinedButton(onClick = { stopScan() }, enabled = scanning) { Text("Стоп") }
                                        }
                                    }
                                }
                                lastScan?.let { record ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("Найденная метка", style = MaterialTheme.typography.titleMedium)
                                            Text("UID: ${record.uid.ifBlank { "не предоставлен" }}")
                                            Text("Технологии: ${record.technologies}")
                                            Text("NDEF: ${record.ndef}")
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = {
                                                    renameTarget = records.firstOrNull { it.uid == record.uid && it.technologies == record.technologies } ?: record
                                                    renameText = renameTarget?.title ?: "NFC-карта"
                                                }) { Text("Задать имя") }
                                                OutlinedButton(onClick = { selectedRecord = record; currentPage = "cards" }) { Text("Выбрать") }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("Мои карты", style = MaterialTheme.typography.titleLarge)
                                Text("Выбери сохранённую метку, чтобы открыть её сведения.")
                                if (records.isEmpty()) {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Пока пусто")
                                            Text("Отсканируй NFC-метку, проверь её данные и сохрани под удобным именем.")
                                            Button(onClick = { currentPage = "scan" }) { Text("Добавить карту") }
                                        }
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(records.reversed()) { record ->
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(record.title, style = MaterialTheme.typography.titleMedium)
                                                    Text("UID: ${record.uid.ifBlank { "не предоставлен" }}", style = MaterialTheme.typography.bodySmall)
                                                    Text(record.technologies, style = MaterialTheme.typography.bodySmall)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Button(onClick = { selectedRecord = record }) { Text(if (selectedRecord == record) "Выбрана ✓" else "Выбрать") }
                                                        OutlinedButton(onClick = { renameTarget = record; renameText = record.title }) { Text("Имя") }
                                                        TextButton(onClick = {
                                                            records.remove(record)
                                                            if (selectedRecord == record) selectedRecord = null
                                                            saveRecords()
                                                        }) { Text("Удалить") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                selectedRecord?.let { record ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Выбрана: ${record.title}", style = MaterialTheme.typography.titleMedium)
                                            Text("UID: ${record.uid}")
                                            Text("NDEF: ${record.ndef}")
                                            Text("Это сохранённая запись, а не эмуляция физической карты.")
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Важно: приложение сохраняет сведения о метках, но не копирует защищённые пропуска. Телефон не читает RFID 125 кГц и не может универсально эмулировать любую карту.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                renameTarget?.let { target ->
                    AlertDialog(
                        onDismissRequest = { renameTarget = null },
                        title = { Text("Название карты") },
                        text = {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                label = { Text("Например, «Офис»") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val newTitle = renameText.trim().ifBlank { "NFC-карта" }
                                val index = records.indexOfFirst { it.uid == target.uid && it.technologies == target.technologies }
                                if (index >= 0) {
                                    records[index] = records[index].copy(title = newTitle)
                                    if (selectedRecord?.uid == target.uid) selectedRecord = records[index]
                                    if (lastScan?.uid == target.uid) lastScan = records[index]
                                    saveRecords()
                                }
                                renameTarget = null
                            }) { Text("Сохранить") }
                        },
                        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } }
                    )
                }
            }
        }
    }

    private fun startScan() {
        val adapter = nfcAdapter
        if (adapter == null) {
            scanMessage = "NFC не поддерживается этим устройством."
            return
        }
        if (!adapter.isEnabled) {
            scanMessage = "Сначала включи NFC в настройках телефона."
            return
        }
        try {
            adapter.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE,
                null
            )
            scanning = true
            scanMessage = "Ожидаю метку… приложи её к задней части телефона."
        } catch (e: Exception) {
            scanMessage = "Не удалось начать сканирование: ${e.localizedMessage ?: "неизвестная ошибка"}"
        }
    }

    private fun stopScan() {
        try { nfcAdapter?.disableReaderMode(this) } catch (_: Exception) {}
        scanning = false
        scanMessage = "Сканирование остановлено."
    }

    override fun onTagDiscovered(tag: Tag) {
        val uid = tag.id?.joinToString("") { "%02X".format(it) } ?: ""
        val technologies = tag.techList?.joinToString(", ") { it.substringAfterLast('.') }.orEmpty()
        val ndefText = readNdef(tag)
        runOnUiThread {
            val old = records.firstOrNull { it.uid == uid && it.technologies == technologies }
            val record = ScanRecord(
                title = old?.title ?: "NFC-карта",
                uid = uid,
                technologies = technologies.ifBlank { "не определены" },
                ndef = ndefText
            )
            lastScan = record
            records.removeAll { it.uid == record.uid && it.technologies == record.technologies }
            records.add(record)
            saveRecords()
            scanMessage = "Метка обнаружена. Проверь сведения и задай ей имя."
            scanning = false
            try { nfcAdapter?.disableReaderMode(this) } catch (_: Exception) {}
        }
    }

    private fun readNdef(tag: Tag): String {
        return try {
            val ndef = Ndef.get(tag)
            if (ndef == null) {
                "NDEF не поддерживается или недоступен"
            } else {
                ndef.connect()
                val message = ndef.ndefMessage
                ndef.close()
                if (message == null || message.records.isEmpty()) {
                    "NDEF-сообщение отсутствует"
                } else {
                    message.records.joinToString(" | ") { record ->
                        record.payload.toString(Charsets.UTF_8).take(160).ifBlank { "пустая запись" }
                    }
                }
            }
        } catch (e: Exception) {
            "Недоступен: ${e.localizedMessage ?: "ошибка чтения"}"
        }
    }

    private fun loadRecords() {
        try {
            val raw = getSharedPreferences("nfc_pass", Context.MODE_PRIVATE).getString("records", "[]") ?: "[]"
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                records.add(ScanRecord(
                    title = o.optString("title", "NFC-карта"),
                    uid = o.optString("uid", ""),
                    technologies = o.optString("technologies", ""),
                    ndef = o.optString("ndef", ""),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis())
                ))
            }
            lastScan = records.lastOrNull()
            selectedRecord = records.lastOrNull()
        } catch (_: Exception) {}
    }

    private fun saveRecords() {
        val array = JSONArray()
        records.forEach { r ->
            array.put(JSONObject().apply {
                put("title", r.title)
                put("uid", r.uid)
                put("technologies", r.technologies)
                put("ndef", r.ndef)
                put("timestamp", r.timestamp)
            })
        }
        getSharedPreferences("nfc_pass", Context.MODE_PRIVATE)
            .edit().putString("records", array.toString()).apply()
    }

    override fun onPause() {
        stopScan()
        super.onPause()
    }
}
