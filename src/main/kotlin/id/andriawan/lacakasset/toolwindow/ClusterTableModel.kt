package id.andriawan.lacakasset.toolwindow

import id.andriawan.lacakasset.model.DrawableCluster
import java.awt.image.BufferedImage
import javax.swing.table.AbstractTableModel

/**
 * One row per cluster, so a drawable duplicated four times occupies a single row instead of
 * the six pair rows the previous table produced.
 */
class ClusterTableModel : AbstractTableModel() {

    companion object {
        const val COLUMN_PREVIEW = 0
        const val COLUMN_NAME = 1
        const val COLUMN_COUNT = 2
        const val COLUMN_SIMILARITY = 3
        const val COLUMN_SAVING = 4
    }

    private val columns = arrayOf("", "Asset", "Copies", "Similarity", "Recoverable")
    private val clusters = mutableListOf<DrawableCluster>()

    override fun getRowCount(): Int = clusters.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        COLUMN_PREVIEW -> BufferedImage::class.java
        COLUMN_COUNT -> Int::class.javaObjectType
        // Sorted on the underlying numbers so ordering is numeric, not lexical.
        COLUMN_SIMILARITY -> Int::class.javaObjectType
        COLUMN_SAVING -> Long::class.javaObjectType
        else -> String::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val cluster = clusters[rowIndex]
        return when (columnIndex) {
            COLUMN_PREVIEW -> cluster.canonical.thumbnail
            COLUMN_NAME -> cluster.canonical.file.fileName
            COLUMN_COUNT -> cluster.memberCount
            COLUMN_SIMILARITY -> cluster.weakestPercent
            COLUMN_SAVING -> cluster.estimatedSaving
            else -> null
        }
    }

    fun getClusterAt(row: Int): DrawableCluster? = clusters.getOrNull(row)

    fun indexOfCluster(predicate: (DrawableCluster) -> Boolean): Int =
        clusters.indexOfFirst(predicate)

    fun setClusters(newClusters: List<DrawableCluster>) {
        clusters.clear()
        clusters.addAll(newClusters)
        fireTableDataChanged()
    }
}
