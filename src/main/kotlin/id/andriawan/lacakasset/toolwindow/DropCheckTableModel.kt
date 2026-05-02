package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.project.Project
import id.andriawan.lacakasset.model.SimilarityResult
import id.andriawan.lacakasset.service.DrawableHashCacheService
import java.awt.image.BufferedImage
import javax.swing.table.AbstractTableModel

class DropCheckTableModel(private val project: Project) : AbstractTableModel() {

    private val columns = arrayOf("Match", "Similarity", "Source")
    private val results = mutableListOf<SimilarityResult>()

    override fun getRowCount(): Int = results.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return if (columnIndex == 0) BufferedImage::class.java else String::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val result = results[rowIndex]
        return when (columnIndex) {
            0 -> DrawableHashCacheService.getInstance(project).getCached(result.fileB.virtualFile.path)?.thumbnail
            1 -> "${result.similarityPercent}%"
            2 -> "${result.fileB.resourceName}.${result.fileB.format.extensions.first()} (${result.fileB.modulePath})"
            else -> null
        }
    }

    fun setResults(newResults: List<SimilarityResult>) {
        results.clear()
        results.addAll(newResults)
        fireTableDataChanged()
    }

    fun getResultAt(row: Int): SimilarityResult? = results.getOrNull(row)
}
