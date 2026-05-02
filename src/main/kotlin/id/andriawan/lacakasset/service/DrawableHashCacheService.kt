package id.andriawan.lacakasset.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import id.andriawan.lacakasset.model.HashedDrawable
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class DrawableHashCacheService : Disposable {

    private val cache = ConcurrentHashMap<String, HashedDrawable>()

    @Volatile
    var hasChangedSinceLastScan = false
        private set

    fun getCached(filePath: String): HashedDrawable? {
        return cache[filePath]
    }

    fun put(filePath: String, hashed: HashedDrawable) {
        cache[filePath] = hashed
    }

    fun invalidate(filePath: String) {
        cache.remove(filePath)
        hasChangedSinceLastScan = true
    }

    fun getAllCached(): List<HashedDrawable> = cache.values.toList()

    fun clearChangedFlag() {
        hasChangedSinceLastScan = false
    }

    override fun dispose() {
        cache.clear()
    }

    companion object {
        fun getInstance(project: Project): DrawableHashCacheService {
            return project.getService(DrawableHashCacheService::class.java)
        }
    }
}
