package com.ai.assistance.operit.ui.features.memory.screens.graph

import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.repository.CompanionMemoryGraphSnapshot
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node

internal const val COMPANION_GRAPH_SOURCE = "companion_memory"
private const val COMPANION_NODE_PREFIX = "companion-memory:"
private const val HUB_NODE_PREFIX = "companion-hub:"

data class CompanionMemoryGraphLabels(
    val root: String,
    val scope: (CompanionMemoryRecordEntity) -> String,
    val type: (String) -> String,
    val recordToType: String,
    val typeToScope: String,
    val scopeToRoot: String,
)

object CompanionMemoryGraphProjector {
    fun merge(
        legacyGraph: Graph,
        snapshot: CompanionMemoryGraphSnapshot,
        query: String,
        labels: CompanionMemoryGraphLabels,
    ): Graph {
        val queryKey = query.trim().lowercase()
        val visibleRecords =
            snapshot.records.filter { record ->
                queryKey.isBlank() ||
                    listOf(record.predicate, record.decodedLabel().orEmpty(), record.decodedValue())
                        .joinToString(" ")
                        .lowercase()
                        .contains(queryKey)
            }
        if (visibleRecords.isEmpty()) return legacyGraph

        val visibleIds = visibleRecords.mapTo(hashSetOf()) { it.id }
        val nodes = legacyGraph.nodes.toMutableList()
        val edges = legacyGraph.edges.toMutableList()
        val hubs = linkedMapOf<String, Node>()

        fun addHub(id: String, label: String, color: Color): Node =
            hubs.getOrPut(id) {
                Node(
                    id = id,
                    label = label,
                    color = color,
                    metadata = mapOf("source" to COMPANION_GRAPH_SOURCE, "kind" to "hub"),
                )
            }

        val root =
            addHub(
                id = "$HUB_NODE_PREFIX root",
                label = labels.root,
                color = Color(0xFF90CAF9),
            )
        visibleRecords.forEach { record ->
            val recordNodeId = "$COMPANION_NODE_PREFIX${record.id}"
            val value = record.decodedValue().replace(Regex("\\s+"), " ").trim().take(84)
            val label =
                record.decodedLabel()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$it: $value" }
                    ?: value
            nodes +=
                Node(
                    id = recordNodeId,
                    label = label,
                    color = colorForType(record.type),
                    metadata =
                        mapOf(
                            "source" to COMPANION_GRAPH_SOURCE,
                            "kind" to "record",
                            "recordId" to record.id,
                            "scope" to record.scope,
                            "type" to record.type,
                        ),
                )

            val scopeId = "$HUB_NODE_PREFIX scope:${record.scope}:${record.companionId}"
            val typeId = "$HUB_NODE_PREFIX type:${record.type}"
            val scopeHub = addHub(scopeId, labels.scope(record), Color(0xFFA5D6A7))
            val typeHub = addHub(typeId, labels.type(record.type), Color(0xFFFFCC80))
            edges += projectedEdge(recordNodeId, typeHub.id, labels.recordToType)
            edges += projectedEdge(typeHub.id, scopeHub.id, labels.typeToScope)
            edges += projectedEdge(scopeHub.id, root.id, labels.scopeToRoot)
        }

        snapshot.edges.forEach { edge ->
            if (edge.fromMemoryId !in visibleIds || edge.toMemoryId !in visibleIds) return@forEach
            edges +=
                Edge(
                    id = stableId("stored:${edge.id}"),
                    sourceId = "$COMPANION_NODE_PREFIX${edge.fromMemoryId}",
                    targetId = "$COMPANION_NODE_PREFIX${edge.toMemoryId}",
                    label = edge.type,
                    weight = edge.strength.toFloat().coerceIn(0.1f, 1f),
                    metadata =
                        mapOf(
                            "source" to COMPANION_GRAPH_SOURCE,
                            "kind" to "stored_edge",
                            "edgeId" to edge.id,
                        ),
                )
        }

        nodes += hubs.values
        return Graph(nodes = nodes.distinctBy { it.id }, edges = edges.distinctBy { it.id })
    }

    fun legacyOnly(graph: Graph): Graph =
        Graph(
            nodes = graph.nodes.filterNot(::isCompanionNode),
            edges = graph.edges.filterNot(::isCompanionEdge),
        )

    fun isCompanionNode(node: Node): Boolean =
        node.metadata["source"] == COMPANION_GRAPH_SOURCE

    fun isCompanionEdge(edge: Edge): Boolean =
        edge.metadata["source"] == COMPANION_GRAPH_SOURCE

    fun recordId(node: Node): String? =
        node.metadata["recordId"]?.takeIf { node.metadata["kind"] == "record" }

    private fun projectedEdge(sourceId: String, targetId: String, label: String): Edge =
        Edge(
            id = stableId("projection:$sourceId:$targetId"),
            sourceId = sourceId,
            targetId = targetId,
            label = label,
            weight = 0.45f,
            metadata = mapOf("source" to COMPANION_GRAPH_SOURCE, "kind" to "projection"),
        )

    private fun colorForType(type: String): Color =
        when (type) {
            "IDENTITY" -> Color(0xFF90CAF9)
            "PREFERENCE" -> Color(0xFFFFCC80)
            "BOUNDARY" -> Color(0xFFCE93D8)
            "COMMITMENT", "RELATIONSHIP" -> Color(0xFFF48FB1)
            "EVENT" -> Color(0xFFA5D6A7)
            "ROUTINE" -> Color(0xFF80CBC4)
            else -> Color(0xFFB0BEC5)
        }

    private fun stableId(value: String): Long {
        var hash = -0x340d631b8c46753L
        value.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
        return if (hash == Long.MIN_VALUE) Long.MIN_VALUE + 1 else -kotlin.math.abs(hash)
    }
}
