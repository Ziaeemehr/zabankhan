package com.ziaee.zabankhan.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class FoundComputer(val id: String, val name: String, val host: String, val port: Int)
data class PairedComputer(val id: String, val name: String, val host: String, val port: Int)

private fun baseUrl(host: String, port: Int, service: String): String {
    val address = if (':' in host && !host.startsWith('[')) "[${host.replace("%", "%25")}]" else host
    return "http://$address:$port/$service"
}

class LocalServerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("zabankhan_local_server", Context.MODE_PRIVATE)
    val paired: PairedComputer? get() {
        val id = prefs.getString("id", null) ?: return null
        val host = prefs.getString("host", null) ?: return null
        return PairedComputer(id, prefs.getString("name", "Local computer").orEmpty(), host, prefs.getInt("port", 8765))
    }
    fun pair(value: FoundComputer) { prefs.edit().putString("id", value.id).putString("name", value.name).putString("host", value.host).putInt("port", value.port).apply() }
    fun update(host: String, port: Int) { prefs.edit().putString("host", host).putInt("port", port).apply() }
    fun forget() { prefs.edit().clear().apply() }
    fun ttsUrl() = paired?.let { baseUrl(it.host, it.port, "tts") }
}

object LocalServerFinder {
    @Suppress("DEPRECATION")
    suspend fun discover(context: Context, timeoutMs: Long = 4000): List<FoundComputer> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val found = linkedMapOf<String, FoundComputer>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) { queue.close() }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) { queue.trySend(service) }
        }
        try {
            manager.discoverServices("_frenchreader._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(timeoutMs) {
                for (service in queue) {
                    val resolved = resolve(manager, service) ?: continue
                    val host = resolved.host?.hostAddress ?: continue
                    val id = resolved.attributes["id"]?.toString(Charsets.UTF_8)?.takeIf(String::isNotBlank)
                        ?: readInfo(host, resolved.port)?.first ?: continue
                    val name = readInfo(host, resolved.port)?.second ?: resolved.serviceName.removeSuffix(" (French Reader)")
                    found[id] = FoundComputer(id, name, host, resolved.port)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        finally { runCatching { manager.stopServiceDiscovery(listener) }; queue.close() }
        found.values.toList()
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(manager: NsdManager, service: NsdServiceInfo): NsdServiceInfo? = withTimeoutOrNull(3000) {
        suspendCancellableCoroutine { c ->
            try {
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) { if (c.isActive) c.resume(null) }
                    override fun onServiceResolved(info: NsdServiceInfo) { if (c.isActive) c.resume(info) }
                })
            } catch (_: Exception) { if (c.isActive) c.resume(null) }
        }
    }
    private fun readInfo(host: String, port: Int): Pair<String, String>? = runCatching {
        val connection = URL("${baseUrl(host, port, "llm").removeSuffix("/llm")}/frenchreader/info").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 1500; connection.readTimeout = 1500
            val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (!json.getJSONObject("services").optBoolean("tts")) throw IllegalStateException("Computer has no TTS service")
            json.getString("id") to json.getString("name").removeSuffix(" (French Reader)")
        } finally { connection.disconnect() }
    }.getOrNull()
}
