package id.andriawan.lacakasset.toolwindow

import id.andriawan.lacakasset.model.SimilarityResult
import javax.swing.table.AbstractTableModel

class SimilarityTableModel : AbstractTableModel() {

    private val columns = arrayOf("Image A", "Image B", "Similarity", "Module A", "Module B", "Savings")
    private val results = mutableListOf<SimilarityResult>()

    override fun getRowCount(): Int = results.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val result = results[rowIndex]
        return when (columnIndex) {
            0 -> "${result.fileA.resourceName}.${result.fileA.format.extensions.first()}"
            1 -> "${result.fileB.resourceName}.${result.fileB.format.extensions.first()}"
            2 -> "${result.similarityPercent}%"
            3 -> result.fileA.modulePath
            4 -> result.fileB.modulePath
            5 -> formatFileSize(minOf(result.fileA.virtualFile.length, result.fileB.virtualFile.length))
            else -> ""
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return String::class.java
    }

    fun getResultAt(row: Int): SimilarityResult? {
        return results.getOrNull(row)
    }

    fun setResults(newResults: List<SimilarityResult>) {
        results.clear()
        results.addAll(newResults)
        fireTableDataChanged()
    }
}
