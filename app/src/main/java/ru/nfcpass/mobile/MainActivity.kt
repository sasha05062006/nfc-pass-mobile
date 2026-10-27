package ru.nfcpass.mobile

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    private var scanMessage by mutableStateOf("Нажми «Начать сканирование» и приложи карту к задней стороне телефона.")
    private var scanning by mutableStateOf(false)
    private var lastScan by mutableStateOf<ScanRecord?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        loadRecords()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("NFC Pass Mobile", style = MaterialTheme.typography.headlineMedium)
                        Text("Диагностика NFC-карт • Samsung Galaxy")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Состояние NFC", style = MaterialTheme.typography.titleMedium)
                                val status = when {
                                    nfcAdapter == null -> "На устройстве нет NFC"
                                    nfcAdapter?.isEnabled == true -> "NFC включён"
                                    else -> "NFC выключен — включи его в настройках телефона"
                                }
                                Text(status)
                                Text(scanMessage)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { startScan() },
                                        enabled = nfcAdapter != null && nfcAdapter?.isEnabled == true && !scanning
                                    ) { Text("Начать сканирование") }
                                    OutlinedButton(
                                        onClick = { stopScan() },
                                        enabled = scanning
                                    ) { Text("Стоп") }
                                }
                            }
                        }

                        lastScan?.let { record ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Последняя карта", style = MaterialTheme.typography.titleLarge)
                                    Text("UID: ${record.uid.ifBlank { "не предоставлен" }}")
                                    Text("Технологии: ${record.technologies}")
                                    Text("NDEF: ${record.ndef}")
                                    Text("Совместимость: чтение обнаружено; возможность прохода не подтверждена.")
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("История сканирований", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = { records.clear(); saveRecords(); lastScan = null }) {
                                Text("Очистить")
                            }
                        }
                        if (records.isEmpty()) {
                            Text("Пока нет сохранённых сканирований.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(records.reversed()) { record ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(record.title, style = MaterialTheme.typography.titleSmall)
                                            Text("UID: ${record.uid.ifBlank { "не предоставлен" }}")
                                            Text(record.technologies)
                                            Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(record.timestamp)))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Важно: обнаружение UID не означает, что пропуск можно скопировать. NFC телефона обычно не читает RFID-карты 125 кГц; защищённые пропуска требуют официальной поддержки.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
            scanMessage = "Ожидаю карту… приложи её к задней части телефона."
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
        val record = ScanRecord(
            title = "NFC-карта",
            uid = uid,
            technologies = technologies.ifBlank { "не определены" },
            ndef = ndefText
        )
        runOnUiThread {
            lastScan = record
            records.removeAll { it.uid == record.uid && it.technologies == record.technologies }
            records.add(record)
            saveRecords()
            scanMessage = "Карта обнаружена. Данные сохранены локально."
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
                        val payload = record.payload
                        payload.toString(Charsets.UTF_8).take(160).ifBlank { "пустая запись" }
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

    override fun onTagDiscovered(tag: Tag?) {
        if (tag != null) onTagDiscovered(tag)
    }
}
