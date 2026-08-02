package id.andriawan.lacakasset.listener

import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import id.andriawan.lacakasset.scanner.DrawableFileScanner
import id.andriawan.lacakasset.service.DrawableHashCacheService

class DrawableFileChangeListener : AsyncFileListener {

    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        val relevantPaths = mutableListOf<String>()

        for (event in events) {
            if (!isRelevantEvent(event)) continue
            val file = event.file ?: continue
            val extension = file.extension?.lowercase() ?: continue
            if (extension !in DrawableFileScanner.DRAWABLE_EXTENSIONS) continue
            if (!DrawableFileScanner.isInDrawableDirectory(file)) continue

            relevantPaths.add(file.path)
        }

        if (relevantPaths.isEmpty()) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                for (path in relevantPaths) {
                    val vFile = LocalFileSystem.getInstance().findFileByPath(path) ?: continue
                    val project = ProjectLocator.getInstance().guessProjectForFile(vFile) ?: continue
                    if (project.isDisposed) continue
                    DrawableHashCacheService.getInstance(project).invalidate(path)
                }
            }
        }
    }

    private fun isRelevantEvent(event: VFileEvent): Boolean {
        return event is VFileCreateEvent ||
                event is VFileDeleteEvent ||
                event is VFileContentChangeEvent ||
                event is VFileMoveEvent ||
                event is VFileCopyEvent
    }
}
