package com.ai.assistance.operit.ui.features.memory.screens.graph

import com.ai.assistance.operit.data.model.CompanionMemoryEdgeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.repository.CompanionMemoryGraphSnapshot
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryGraphProjectorTest {
    private val labels =
        CompanionMemoryGraphLabels(
            root = "Mira memory",
            scope = { it.scope },
            type = { it },
            recordToType = "category",
            typeToScope = "scope",
            scopeToRoot = "domain",
        )

    @Test
    fun `merges companion records and stored relations with the legacy graph`() {
        val first = record("first", "PREFERENCE", "coffee")
        val second = record("second", "BOUNDARY", "no late messages")
        val snapshot =
            CompanionMemoryGraphSnapshot(
                records = listOf(first, second),
                edges =
                    listOf(
                        CompanionMemoryEdgeEntity(
                            id = "edge-1",
                            fromMemoryId = first.id,
                            toMemoryId = second.id,
                            type = "RELATES_TO",
                        ),
                    ),
            )

        val graph =
            CompanionMemoryGraphProjector.merge(
                legacyGraph = Graph(nodes = listOf(Node("legacy", "Legacy")), edges = emptyList()),
                snapshot = snapshot,
                query = "",
                labels = labels,
            )

        assertTrue(graph.nodes.any { it.id == "legacy" })
        assertEquals(2, graph.nodes.count { it.metadata["kind"] == "record" })
        assertTrue(graph.nodes.any { it.metadata["kind"] == "hub" })
        assertTrue(graph.edges.any { it.metadata["kind"] == "stored_edge" })
    }

    @Test
    fun `search only projects matching companion records`() {
        val graph =
            CompanionMemoryGraphProjector.merge(
                legacyGraph = Graph(emptyList(), emptyList()),
                snapshot =
                    CompanionMemoryGraphSnapshot(
                        records =
                            listOf(
                                record("first", "PREFERENCE", "coffee"),
                                record("second", "BOUNDARY", "no late messages"),
                            ),
                        edges = emptyList(),
                    ),
                query = "coffee",
                labels = labels,
            )

        assertEquals(1, graph.nodes.count { it.metadata["kind"] == "record" })
    }

    private fun record(id: String, type: String, value: String) =
        CompanionMemoryRecordEntity(
            id = id,
            profileId = "default",
            scope = "USER",
            type = type,
            subjectKey = "user",
            predicate = type.lowercase(),
            valueJson = "\"$value\"",
            normalizedValue = value,
            sourceKind = "USER_EXPLICIT",
        )
}
