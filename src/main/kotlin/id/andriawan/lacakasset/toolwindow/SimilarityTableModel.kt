package id.andriawan.lacakasset.toolwindow

import id.andriawan.lacakasset.model.SimilarityResult
import javax.swing.table.AbstractTableModel

class SimilarityTableModel : AbstractTableModel() {

    private val columns = arrayOf("Image A", "Image B", "Similarity", "Source A", "Source B", "Savings")
    private val results = mutableListOf<SimilarityResult>()

    override fun getRowCount(): Int = results.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val result = results[rowIndex]
        return when (columnIndex) {
            0 -> "${result.fileA.resourceName}.${result.fileA.format.extensions.first()}"
            1 -> "${result.fileB.resourceName}.${result.fileB.format.extensions.first()}"
            2 -> result.similarityPercent
            3 -> formatSource(result.fileA.modulePath, result.fileA.sourceSet, result.fileA.resourceOrigin.label)
            4 -> formatSource(result.fileB.modulePath, result.fileB.sourceSet, result.fileB.resourceOrigin.label)
            5 -> minOf(result.fileA.virtualFile.length, result.fileB.virtualFile.length)
            else -> ""
        }
    }

    private fun formatSource(modulePath: String, sourceSet: String, origin: String): String {
        return "$modulePath ($sourceSet, $origin)"
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            2 -> Int::class.javaObjectType
            5 -> Long::class.javaObjectType
            else -> String::class.java
        }
    }

    fun formatSavings(row: Int): String {
        val result = results[row]
        return formatFileSize(minOf(result.fileA.virtualFile.length, result.fileB.virtualFile.length))
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
