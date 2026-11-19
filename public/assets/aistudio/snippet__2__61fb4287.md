# ARAHAN PEMBINAAN APLIKASI ANDROID (APK CLIENT FOR CARBON-PRO API)

Sila bina sebuah aplikasi Android (projek Kotlin/Java lengkap berserta konfigurasi Gradle) yang bertindak sebagai klien ringan (lightweight client) untuk berinteraksi dengan API Model "CARBON-PRO". Aplikasi ini perlu dikompilasikan sebagai fail APK sedia pasang.

## 1. STRUKTUR UTAMA APLIKASI
*   **Antaramuka Pengguna (UI/UX):**
    *   Satu ruang sembang (Chat Interface) yang kemas, responsif, dan menyokong pemformatan Markdown (terutamanya sintaks kod blok).
    *   Halaman "Tetapan API" (API Settings) di mana pengguna boleh memasukkan:
        *   `API_URL` (Endpoint pelayan awan peribadi anda yang mengandungi model AI).
        *   `API_KEY` (Kunci keselamatan untuk pengesahan).
*   **Sistem Logik & Sambungan API:**
    *   Aplikasi mesti menggunakan pustaka rangkaian bertaraf industri (seperti OkHttpClient atau Retrofit2) untuk menghantar permintaan POST tak-segerak (asynchronous) ke pelayan awan.
    *   Sistem perlu menghantar sejarah sembang (chat history) dan menerima maklum balas berstruktur JSON daripada pelayan.
    *   Sokong penstriman respons (Server-Sent Events / SSE) jika disokong oleh API, supaya teks jawapan AI dipaparkan secara masa-nyata (real-time typing effect) pada skrin telefon.

## 2. SPESIFIKASI KOD SUMBER (KOTLIN)
Pastikan kod rangkaian anda mengikut templat pengurusan sambungan seperti berikut:

```kotlin
// Contoh konfigurasi penghantaran data ke API Awan
class ApiClient(private val baseUrl: String, private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun sendPrompt(prompt: String, chatHistory: List<Message>, callback: Callback) {
        val jsonPayload = JSONObject().apply {
            put("prompt", prompt)
            put("history", JSONArray(chatHistory.map { it.toJson() }))
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(callback)
    }
}
