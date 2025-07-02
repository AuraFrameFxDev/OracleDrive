package com.genesis.ai.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Simple graph view for visualizing modules and their connections/blocks as halos and lines.
 */
class HaloGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val nodePaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val blockedPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 36f
        isAntiAlias = true
    }
    private var nodes: List<ModuleNode> = emptyList()
    private var edges: List<ModuleEdge> = emptyList()

    /**
     * Updates the graph data with the provided nodes and edges, and triggers a redraw of the view.
     *
     * @param nodes The list of nodes to display in the graph.
     * @param edges The list of edges representing connections between nodes.
     */
    fun setGraphData(nodes: List<ModuleNode>, edges: List<ModuleEdge>) {
        this.nodes = nodes
        this.edges = edges
        invalidate()
    }

    /**
     * Renders the graph by drawing nodes as circles and edges as lines on the canvas.
     *
     * Blocked edges are drawn with a distinct style. Node labels are displayed near each node.
     *
     * @param canvas The canvas on which the graph is drawn.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw edges
        for (edge in edges) {
            val from = nodes.find { it.id == edge.fromId } ?: continue
            val to = nodes.find { it.id == edge.toId } ?: continue
            val paint = if (edge.blocked) blockedPaint else nodePaint
            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
        // Draw nodes
        for (node in nodes) {
            canvas.drawCircle(node.x, node.y, 60f, nodePaint)
            canvas.drawText(node.label, node.x - 40f, node.y + 10f, textPaint)
        }
    }
}

data class ModuleNode(val id: String, val label: String, val x: Float, val y: Float)
data class ModuleEdge(val fromId: String, val toId: String, val blocked: Boolean)
