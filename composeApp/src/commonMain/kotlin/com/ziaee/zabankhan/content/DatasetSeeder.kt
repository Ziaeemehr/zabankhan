package com.ziaee.zabankhan.content

import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.translate.KeyValueCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** French Reader's crash-resumable starter seeding, scoped to the active language pack. */
object DatasetSeeder {
    private val mutex = Mutex()

    suspend fun seedIfNeeded(repository: DatasetRepository, db: AppDatabase, cache: KeyValueCache) = mutex.withLock {
        val language = repository.language.code
        val versionKey = "dataset:$language:seeded_version"
        val pendingKey = "dataset:$language:seed_pending"
        val pending = cache.get(pendingKey) == "true"
        val manifest = repository.loadManifest()
        if (cache.get(versionKey)?.toIntOrNull() == manifest.version && !pending) return@withLock
        if (manifest.version == 0) return@withLock
        val fresh = db.texts().observeAll(language).first().isEmpty() && db.vocab().observeLists(language).first().isEmpty()
        if (pending || fresh) {
            cache.put(pendingKey, "true")
            repository.installStarter(manifest)
        }
        cache.put(versionKey, manifest.version.toString())
        cache.put(pendingKey, "false")
    }
}
