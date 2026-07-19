package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.MarketV2PublishRequest
import com.ai.assistance.operit.data.api.MarketV2PublishAsset
import com.ai.assistance.operit.data.api.MarketV2PublishSource
import com.ai.assistance.operit.data.api.MarketV2PublishVersion
import com.ai.assistance.operit.data.api.MiraMarketNetworkPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubMarketRepositoryTest {
    @Test
    fun `Mira ids map only positive GitHub issue numbers`() {
        assertTrue(GitHubMarketRepository.isMiraEntryId("mira-42"))
        assertEquals(42L, GitHubMarketRepository.requireIssueNumber("mira-42"))
        assertFalse(GitHubMarketRepository.isMiraEntryId("legacy-42"))
        assertFalse(GitHubMarketRepository.isMiraEntryId("mira-0"))
        assertFailsWith<IllegalArgumentException> {
            GitHubMarketRepository.requireIssueNumber("mira-0")
        }
    }

    @Test
    fun `entry body uses Pages index protocol and round trips request`() {
        val request = sampleRequest()

        val body = GitHubMarketRepository.buildEntryBody(request)
        val envelope = GitHubMarketRepository.parseEntryEnvelope(body)

        assertEquals(GitHubMarketRepository.BODY_MARKER, body.lineSequence().first())
        assertNotNull(envelope)
        assertEquals("mira_market_entry", envelope.kind)
        assertEquals(1, envelope.schemaVersion)
        assertEquals(request, envelope.request)
    }

    @Test
    fun `unmarked issue is never interpreted as a market entry`() {
        val body = "```json\n{\"kind\":\"mira_market_entry\"}\n```"

        assertEquals(null, GitHubMarketRepository.parseEntryEnvelope(body))
    }

    @Test
    fun `version update comment uses the same marker and update kind`() {
        val body =
            GitHubMarketRepository.buildUpdateBody(
                entryId = "mira-7",
                request = sampleRequest(),
                includeEntryPatch = true
            )

        assertEquals(GitHubMarketRepository.BODY_MARKER, body.lineSequence().first())
        assertTrue(body.contains("\"kind\":\"mira_market_update\""))
        assertTrue(body.contains("\"entryId\":\"mira-7\""))
        assertTrue(body.contains("\"includeEntryPatch\":true"))
        assertNotNull(GitHubMarketRepository.parseUpdateEnvelope(body))
        assertTrue(GitHubMarketRepository.isProtocolBody(body))
    }

    @Test
    fun `artifact publication accepts only the current users MiraForge release`() {
        val valid =
            sampleRequest().copy(
                type = "package",
                asset =
                    MarketV2PublishAsset(
                        kind = "github_release_asset",
                        url = "https://github.com/alice/MiraForge/releases/download/v1.0.0/plugin.toolpkg",
                        ghOwner = "alice",
                        ghRepo = "MiraForge",
                        ghReleaseTag = "v1.0.0",
                        assetName = "plugin.toolpkg",
                        sha256 = "a".repeat(64)
                    )
            )

        GitHubMarketRepository.validatePublishRequest(valid, "alice")
        assertFailsWith<IllegalArgumentException> {
            GitHubMarketRepository.validatePublishRequest(
                valid.copy(asset = valid.asset?.copy(ghOwner = "bob")),
                "alice"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GitHubMarketRepository.validatePublishRequest(
                valid.copy(asset = valid.asset?.copy(sha256 = "bad")),
                "alice"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GitHubMarketRepository.validatePublishRequest(
                valid.copy(asset = valid.asset?.copy(url = "https://example.com/plugin.toolpkg")),
                "alice"
            )
        }
    }

    @Test
    fun `editing a reply preserves its machine parent marker`() {
        val result =
            GitHubMarketRepository.preserveReplyMarker(
                existingBody = "<!-- mira-reply-to:123 -->\nold",
                editedBody = "new"
            )

        assertEquals("<!-- mira-reply-to:123 -->\nnew", result)
    }

    @Test
    fun `notification mapper keeps only Mira issue notifications`() {
        val record =
            GitHubMarketNotificationRecord(
                id = "987",
                reason = "comment",
                unread = true,
                updatedAt = "2026-07-19T08:00:00Z",
                subject =
                    GitHubMarketNotificationSubject(
                        title = "Plugin feedback",
                        url = "https://api.github.com/repos/kernelx30/Mira/issues/42",
                        latestCommentUrl = "https://api.github.com/repos/kernelx30/Mira/issues/comments/123",
                        type = "Issue"
                    ),
                repository = GitHubMarketNotificationRepository(fullName = "kernelx30/Mira")
            )

        val mapped = GitHubMarketRepository.mapNotification(record)

        assertNotNull(mapped)
        assertEquals("github-987", mapped.id)
        assertEquals("comment_new", mapped.kind)
        assertEquals("mira-42", mapped.entryId)
        assertEquals("mira-comment-123", mapped.commentId)
        assertEquals(
            null,
            GitHubMarketRepository.mapNotification(
                record.copy(repository = GitHubMarketNotificationRepository("someone/else"))
            )
        )
        assertEquals(
            null,
            GitHubMarketRepository.mapNotification(
                record.copy(subject = record.subject.copy(type = "PullRequest"))
            )
        )
    }

    @Test
    fun `artifact download policy uses direct HTTPS and rejects legacy redirect host`() {
        val direct =
            "https://github.com/example/MiraForge/releases/download/v1/plugin.toolpkg"

        assertEquals(direct, MiraMarketNetworkPolicy.directArtifactUrl(direct))
        assertFailsWith<IllegalArgumentException> {
            MiraMarketNetworkPolicy.directArtifactUrl("http://github.com/example/plugin.toolpkg")
        }
        assertFailsWith<IllegalArgumentException> {
            MiraMarketNetworkPolicy.directArtifactUrl("https://api.operit.app/market/v2/assets/a/download")
        }
    }

    private fun sampleRequest(): MarketV2PublishRequest =
        MarketV2PublishRequest(
            type = "skill",
            title = "Test skill",
            description = "Description",
            detail = "Details",
            categoryId = "productivity",
            version =
                MarketV2PublishVersion(
                    version = "1.0.0",
                    formatVer = "1",
                    minAppVer = "0.1.0"
                ),
            source =
                MarketV2PublishSource(
                    kind = "github_repo",
                    url = "https://github.com/example/test-skill"
                )
        )
}
