package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import com.ai.assistance.operit.data.api.MarketV2Artifact
import com.ai.assistance.operit.data.api.MarketV2Asset
import com.ai.assistance.operit.data.api.MarketV2Author
import com.ai.assistance.operit.data.api.MarketV2Comment
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2EntryUpdateRequest
import com.ai.assistance.operit.data.api.MarketV2NewVersionResponse
import com.ai.assistance.operit.data.api.MarketV2Notification
import com.ai.assistance.operit.data.api.MarketV2PublishRequest
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.data.api.MarketV2Reaction
import com.ai.assistance.operit.data.api.MarketV2Source
import com.ai.assistance.operit.data.api.MarketV2Version
import com.ai.assistance.operit.data.api.MiraMarketNetworkPolicy
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
internal data class GitHubMarketEntryEnvelope(
    val kind: String = "mira_market_entry",
    val schemaVersion: Int = 1,
    val request: MarketV2PublishRequest
)

@Serializable
internal data class GitHubMarketUpdateEnvelope(
    val kind: String = "mira_market_update",
    val schemaVersion: Int = 1,
    val entryId: String,
    val includeEntryPatch: Boolean,
    val request: MarketV2PublishRequest
)

@Serializable
private data class GitHubIssueCreateRequest(
    val title: String,
    val body: String
)

@Serializable
private data class GitHubIssueUpdateRequest(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null
)

@Serializable
private data class GitHubCommentCreateRequest(val body: String)

@Serializable
private data class GitHubReactionCreateRequest(val content: String)

@Serializable
private data class GitHubMarketApiUser(
    val id: Long,
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class GitHubIssueReactions(
    @SerialName("+1") val plusOne: Int = 0
)

@Serializable
private data class GitHubMarketIssue(
    val number: Long,
    val title: String,
    val body: String? = null,
    val state: String,
    val user: GitHubMarketApiUser,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
    val reactions: GitHubIssueReactions = GitHubIssueReactions(),
    @SerialName("pull_request") val pullRequest: JsonElement? = null
)

@Serializable
private data class GitHubMarketComment(
    val id: Long,
    val body: String,
    val user: GitHubMarketApiUser,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class GitHubMarketReaction(
    val id: Long,
    val content: String
)

@Serializable
internal data class GitHubMarketNotificationSubject(
    val title: String,
    val url: String? = null,
    @SerialName("latest_comment_url") val latestCommentUrl: String? = null,
    val type: String
)

@Serializable
internal data class GitHubMarketNotificationRepository(
    @SerialName("full_name") val fullName: String
)

@Serializable
internal data class GitHubMarketNotificationRecord(
    val id: String,
    val reason: String,
    val unread: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    val subject: GitHubMarketNotificationSubject,
    val repository: GitHubMarketNotificationRepository
)

private data class GitHubMarketUpdateRecord(
    val comment: GitHubMarketComment,
    val envelope: GitHubMarketUpdateEnvelope
)

private data class GitHubMarketPublishedRequest(
    val request: MarketV2PublishRequest,
    val publishedAt: String,
    val versionId: String
)

/**
 * GitHub-native Mira market writes. The OAuth token is read for each request and is
 * attached only to requests whose host is api.github.com, so logout/account changes
 * cannot inherit an in-process market identity.
 */
class GitHubMarketRepository(context: Context) {
    private val auth = GitHubAuthPreferences.getInstance(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun createEntry(request: MarketV2PublishRequest): Result<MarketV2Entry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val user = auth.getCurrentUserInfo() ?: error("GitHub login required")
                validatePublishRequest(request, user.login)
                val response =
                    executeJson(
                        method = "POST",
                        pathSegments = listOf("repos", OWNER, REPOSITORY, "issues"),
                        requestBody =
                            json.encodeToString(
                                GitHubIssueCreateRequest(
                                    title = issueTitle(request),
                                    body = buildEntryBody(request)
                                )
                            ),
                        serializer = GitHubMarketIssue.serializer()
                    )
                response.toMarketEntry()
            }
        }

    suspend fun getEntry(entryId: String): Result<MarketV2Entry?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val issueNumber = requireIssueNumber(entryId)
                val issue = getIssue(issueNumber)
                parseEntryEnvelope(issue.body)?.let { envelope ->
                    issue.toMarketEntry(envelope, getAuthorizedUpdates(issue))
                }
            }
        }

    suspend fun getCurrentUserEntries(type: String?): Result<List<MarketV2PublisherEntrySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val user = auth.getCurrentUserInfo() ?: error("GitHub login required")
                val issues = executeJson(
                    method = "GET",
                    pathSegments = listOf("repos", OWNER, REPOSITORY, "issues"),
                    queryParameters =
                        mapOf(
                            "creator" to user.login,
                            "state" to "all",
                            "per_page" to "100"
                        ),
                    serializer = kotlinx.serialization.builtins.ListSerializer(GitHubMarketIssue.serializer())
                )
                val entries = mutableListOf<MarketV2PublisherEntrySummary>()
                for (issue in issues) {
                    if (issue.pullRequest != null) continue
                    val envelope = parseEntryEnvelope(issue.body) ?: continue
                    val entry = issue.toMarketEntry(envelope, getAuthorizedUpdates(issue))
                    entries += entry.toPublisherSummary(issue)
                }
                entries.asSequence()
                    .filter { type.isNullOrBlank() || it.type.equals(type, ignoreCase = true) }
                    .sortedByDescending { it.updatedAt }
                    .toList()
            }
        }

    suspend fun getComments(entryId: String, page: Int): Result<List<MarketV2Comment>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val issueNumber = requireIssueNumber(entryId)
                getIssueComments(issueNumber, page = page.coerceAtLeast(1), perPage = 50)
                    .filterNot { isProtocolBody(it.body) }
                    .map { it.toMarketComment(entryId) }
            }
        }

    suspend fun getNotifications(limit: Int, offset: Int): Result<List<MarketV2Notification>> =
        Result.failure(MiraMarketNetworkPolicy.writeUnavailable("market notifications"))

    suspend fun postComment(entryId: String, body: String, parentId: String?): Result<MarketV2Comment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val issueNumber = requireIssueNumber(entryId)
                val normalizedBody =
                    if (parentId.isNullOrBlank()) {
                        body.trim()
                    } else {
                        "<!-- mira-reply-to:${requireCommentId(parentId)} -->\n${body.trim()}"
                    }
                require(normalizedBody.isNotBlank()) { "Comment body is empty" }
                executeJson(
                    method = "POST",
                    pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", issueNumber.toString(), "comments"),
                    requestBody = json.encodeToString(GitHubCommentCreateRequest(normalizedBody)),
                    serializer = GitHubMarketComment.serializer()
                ).toMarketComment(entryId, parentId)
            }
        }

    suspend fun editComment(commentId: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = requireCommentId(commentId)
                require(body.isNotBlank()) { "Comment body is empty" }
                val existing =
                    executeJson(
                        method = "GET",
                        pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", "comments", id.toString()),
                        serializer = GitHubMarketComment.serializer()
                    )
                executeJson(
                    method = "PATCH",
                    pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", "comments", id.toString()),
                    requestBody =
                        json.encodeToString(
                            GitHubCommentCreateRequest(
                                preserveReplyMarker(existing.body, body)
                            )
                        ),
                    serializer = GitHubMarketComment.serializer()
                )
                Unit
            }
        }

    suspend fun deleteComment(commentId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = requireCommentId(commentId)
                executeWithoutBody(
                    method = "DELETE",
                    pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", "comments", id.toString())
                )
            }
        }

    suspend fun addReaction(entryId: String): Result<MarketV2Reaction> =
        withContext(Dispatchers.IO) {
            runCatching {
                val issueNumber = requireIssueNumber(entryId)
                executeJson(
                    method = "POST",
                    pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", issueNumber.toString(), "reactions"),
                    requestBody = json.encodeToString(GitHubReactionCreateRequest("+1")),
                    serializer = GitHubMarketReaction.serializer(),
                    accept = REACTIONS_ACCEPT
                )
                MarketV2Reaction(reaction = "+1", content = "+1", total = 1)
            }
        }

    suspend fun updateEntry(entryId: String, update: MarketV2EntryUpdateRequest): Result<MarketV2Entry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val issueNumber = requireIssueNumber(entryId)
                val issue = getIssue(issueNumber)
                val envelope = parseEntryEnvelope(issue.body) ?: error("Mira market entry payload is missing")
                val request =
                    envelope.request.copy(
                        title = update.title,
                        description = update.description,
                        detail = update.detail,
                        categoryId = update.categoryId,
                        allowPublicUpdates = update.allowPublicUpdates ?: envelope.request.allowPublicUpdates
                    )
                executeJson(
                    method = "PATCH",
                    pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", issueNumber.toString()),
                    requestBody =
                        json.encodeToString(
                            GitHubIssueUpdateRequest(
                                title = issueTitle(request),
                                body = buildEntryBody(request)
                            )
                        ),
                    serializer = GitHubMarketIssue.serializer()
                ).toMarketEntry(GitHubMarketEntryEnvelope(request = request))
            }
        }

    suspend fun publishNewVersion(
        entryId: String,
        request: MarketV2PublishRequest,
        includeEntryPatch: Boolean
    ): Result<MarketV2NewVersionResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val issueNumber = requireIssueNumber(entryId)
                val user = auth.getCurrentUserInfo() ?: error("GitHub login required")
                validatePublishRequest(request, user.login)
                val comment =
                    executeJson(
                        method = "POST",
                        pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", issueNumber.toString(), "comments"),
                        requestBody =
                            json.encodeToString(
                                GitHubCommentCreateRequest(
                                    buildUpdateBody(entryId, request, includeEntryPatch)
                                )
                            ),
                        serializer = GitHubMarketComment.serializer()
                    )
                MarketV2NewVersionResponse(
                    ok = true,
                    entryId = entryId,
                    versionId = "$entryId-update-${comment.id}"
                )
            }
        }

    suspend fun setEntryOpen(entryId: String, open: Boolean): Result<MarketV2Entry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val issueNumber = requireIssueNumber(entryId)
                val updatedIssue = executeJson(
                    method = "PATCH",
                    pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", issueNumber.toString()),
                    requestBody = json.encodeToString(GitHubIssueUpdateRequest(state = if (open) "open" else "closed")),
                    serializer = GitHubMarketIssue.serializer()
                )
                updatedIssue.toMarketEntry(
                    parseEntryEnvelope(updatedIssue.body) ?: error("Mira market entry payload is missing"),
                    getAuthorizedUpdates(updatedIssue)
                )
            }
        }

    private suspend fun getIssue(issueNumber: Long): GitHubMarketIssue =
        executeJson(
            method = "GET",
            pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", issueNumber.toString()),
            serializer = GitHubMarketIssue.serializer()
        )

    private suspend fun getAuthorizedUpdates(issue: GitHubMarketIssue): List<GitHubMarketUpdateRecord> {
        val updates = mutableListOf<GitHubMarketUpdateRecord>()
        var page = 1
        while (true) {
            val comments = getIssueComments(issue.number, page = page, perPage = 100)
            comments.mapNotNullTo(updates) { comment ->
                if (comment.user.id != issue.user.id) return@mapNotNullTo null
                val envelope = parseUpdateEnvelope(comment.body) ?: return@mapNotNullTo null
                if (envelope.entryId != entryId(issue.number)) return@mapNotNullTo null
                GitHubMarketUpdateRecord(comment = comment, envelope = envelope)
            }
            if (comments.size < 100) break
            page += 1
        }
        return updates.sortedBy { it.comment.createdAt }
    }

    private suspend fun getIssueComments(
        issueNumber: Long,
        page: Int,
        perPage: Int
    ): List<GitHubMarketComment> =
        executeJson(
            method = "GET",
            pathSegments = listOf("repos", OWNER, REPOSITORY, "issues", issueNumber.toString(), "comments"),
            queryParameters = mapOf("page" to page.toString(), "per_page" to perPage.toString()),
            serializer = kotlinx.serialization.builtins.ListSerializer(GitHubMarketComment.serializer())
        )

    private suspend fun <T> executeJson(
        method: String,
        pathSegments: List<String>,
        requestBody: String? = null,
        queryParameters: Map<String, String> = emptyMap(),
        serializer: kotlinx.serialization.KSerializer<T>,
        accept: String = GITHUB_ACCEPT
    ): T {
        val responseBody = execute(method, pathSegments, requestBody, queryParameters, accept)
        return json.decodeFromString(serializer, responseBody)
    }

    private suspend fun executeWithoutBody(
        method: String,
        pathSegments: List<String>
    ) {
        execute(method, pathSegments, null, emptyMap(), GITHUB_ACCEPT)
    }

    private suspend fun execute(
        method: String,
        pathSegments: List<String>,
        requestBody: String?,
        queryParameters: Map<String, String>,
        accept: String
    ): String {
        val token = auth.getCurrentAccessToken() ?: error("GitHub login required")
        val urlBuilder =
            HttpUrl.Builder()
                .scheme("https")
                .host(GITHUB_API_HOST)
        pathSegments.forEach(urlBuilder::addPathSegment)
        queryParameters.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val url = urlBuilder.build()
        check(url.host == GITHUB_API_HOST) { "GitHub token target rejected" }

        val builder =
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", accept)
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .addHeader("User-Agent", "Mira-Market")
        val body = requestBody?.toRequestBody(JSON_MEDIA_TYPE)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requireNotNull(body))
            "PATCH" -> builder.patch(requireNotNull(body))
            "DELETE" -> builder.delete()
            else -> error("Unsupported GitHub method: $method")
        }

        AppLogger.d(TAG, "GitHub market $method ${url.encodedPath}")
        client.newCall(builder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub market request failed: HTTP ${response.code}")
            }
            return responseText
        }
    }

    private fun GitHubMarketIssue.toMarketEntry(
        envelope: GitHubMarketEntryEnvelope =
            parseEntryEnvelope(body) ?: error("Mira market entry payload is missing"),
        updates: List<GitHubMarketUpdateRecord> = emptyList()
    ): MarketV2Entry {
        val metadataRequest =
            updates.lastOrNull { it.envelope.includeEntryPatch }?.envelope?.request ?: envelope.request
        val author = user.toMarketAuthor()
        val publishedRequests =
            listOf(GitHubMarketPublishedRequest(envelope.request, createdAt, "${entryId(number)}-v1")) +
                updates.map { update ->
                    GitHubMarketPublishedRequest(
                        request = update.envelope.request,
                        publishedAt = update.comment.createdAt,
                        versionId = "${entryId(number)}-update-${update.comment.id}"
                    )
                }
        val versions = publishedRequests.map { published -> published.toMarketVersion(author, state) }
        val assets = publishedRequests.mapNotNull { published -> published.toMarketAsset() }
        val latestPublished = publishedRequests.last()
        val latestVersion = versions.last()
        return MarketV2Entry(
            type = metadataRequest.type,
            id = entryId(number),
            title = metadataRequest.title,
            description = metadataRequest.description,
            detail = metadataRequest.detail,
            authorId = "gh_${user.id}",
            publisherId = "gh_${user.id}",
            allowPublicUpdates = metadataRequest.allowPublicUpdates,
            categoryId = metadataRequest.categoryId,
            stateCode = if (state == "open") "approved" else "withdrawn",
            createdAt = createdAt,
            updatedAt = updatedAt,
            publishedAt = createdAt,
            source = metadataRequest.source?.let { MarketV2Source(kind = it.kind, url = it.url) },
            artifact =
                latestPublished.request.version.projectId?.let { projectId ->
                    MarketV2Artifact(projectId = projectId, runtimePkg = latestPublished.request.version.runtimePackageId)
                },
            assets = assets,
            versions = versions.asReversed(),
            latestVersion = latestVersion,
            reactions =
                if (reactions.plusOne > 0) {
                    listOf(MarketV2Reaction(reaction = "+1", content = "+1", total = reactions.plusOne))
                } else {
                    emptyList()
                },
            author = author,
            publisher = author
        )
    }

    private fun MarketV2Entry.toPublisherSummary(issue: GitHubMarketIssue): MarketV2PublisherEntrySummary =
        MarketV2PublisherEntrySummary(
            id = id,
            title = title,
            type = type,
            relation = "owner",
            stateCode = stateCode,
            categoryId = categoryId,
            updatedAt = issue.updatedAt
        )

    private fun GitHubMarketPublishedRequest.toMarketVersion(
        author: MarketV2Author,
        issueState: String
    ): MarketV2Version =
        MarketV2Version(
            id = versionId,
            version = request.version.version,
            formatVer = request.version.formatVer,
            publisherId = author.id,
            publisher = author,
            minAppVer = request.version.minAppVer,
            maxAppVer = request.version.maxAppVer,
            changelog = request.version.changelog.orEmpty(),
            installConfig = request.repoVersion?.installConfig.orEmpty(),
            stateCode = if (issueState == "open") "approved" else "withdrawn",
            publishedAt = publishedAt,
            projectId = request.version.projectId.orEmpty(),
            runtimePackageId = request.version.runtimePackageId.orEmpty()
        )

    private fun GitHubMarketPublishedRequest.toMarketAsset(): MarketV2Asset? =
        request.asset?.let {
            MarketV2Asset(
                id = "$versionId-asset",
                versionId = versionId,
                kind = it.kind,
                url = it.url,
                sha256 = it.sha256,
                name = it.assetName,
                assetName = it.assetName
            )
        }

    private fun GitHubMarketComment.toMarketComment(
        entryId: String,
        parentId: String? = parseReplyParent(body)
    ): MarketV2Comment =
        MarketV2Comment(
            id = commentId(id),
            entryId = entryId,
            parentId = parentId,
            author = user.toMarketAuthor(),
            body = body.replaceFirst(REPLY_MARKER, "").trimStart(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun GitHubMarketApiUser.toMarketAuthor(): MarketV2Author =
        MarketV2Author(
            id = "gh_$id",
            githubId = id,
            login = login,
            avatarUrl = avatarUrl
        )

    companion object {
        internal const val BODY_MARKER = "<!-- mira-market-v1 -->"
        private const val OWNER = "kernelx30"
        private const val REPOSITORY = "Mira"
        private const val GITHUB_API_HOST = "api.github.com"
        private const val GITHUB_ACCEPT = "application/vnd.github+json"
        private const val REACTIONS_ACCEPT = "application/vnd.github+json"
        private const val TAG = "GitHubMarketRepository"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val ENTRY_ID = Regex("^mira-([1-9][0-9]*)$")
        private val COMMENT_ID = Regex("^mira-comment-([1-9][0-9]*)$")
        private val JSON_BLOCK = Regex("```json\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        private val REPLY_MARKER = Regex("^<!-- mira-reply-to:([0-9]+) -->\\s*")
        private val PROTOCOL_JSON = Json { encodeDefaults = true }

        fun isMiraEntryId(value: String): Boolean = ENTRY_ID.matches(value.trim())

        internal fun entryId(issueNumber: Long): String {
            require(issueNumber > 0) { "Invalid GitHub issue number" }
            return "mira-$issueNumber"
        }

        internal fun requireIssueNumber(entryId: String): Long =
            ENTRY_ID.matchEntire(entryId.trim())
                ?.groupValues
                ?.get(1)
                ?.toLong()
                ?: throw IllegalArgumentException("Not a Mira market entry id: $entryId")

        internal fun commentId(id: Long): String {
            require(id > 0) { "Invalid GitHub comment id" }
            return "mira-comment-$id"
        }

        internal fun requireCommentId(commentId: String): Long =
            COMMENT_ID.matchEntire(commentId.trim())
                ?.groupValues
                ?.get(1)
                ?.toLong()
                ?: throw IllegalArgumentException("Not a Mira market comment id: $commentId")

        internal fun buildEntryBody(request: MarketV2PublishRequest): String =
            buildProtocolBody(
                PROTOCOL_JSON.encodeToString(
                    GitHubMarketEntryEnvelope.serializer(),
                    GitHubMarketEntryEnvelope(request = request)
                )
            )

        internal fun buildUpdateBody(
            entryId: String,
            request: MarketV2PublishRequest,
            includeEntryPatch: Boolean
        ): String =
            buildProtocolBody(
                PROTOCOL_JSON.encodeToString(
                    GitHubMarketUpdateEnvelope.serializer(),
                    GitHubMarketUpdateEnvelope(
                        entryId = entryId,
                        includeEntryPatch = includeEntryPatch,
                        request = request
                    )
                )
            )

        internal fun parseEntryEnvelope(body: String?): GitHubMarketEntryEnvelope? {
            if (body?.lineSequence()?.firstOrNull()?.trim() != BODY_MARKER) return null
            val jsonBody = JSON_BLOCK.find(body)?.groupValues?.get(1) ?: return null
            return runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString(GitHubMarketEntryEnvelope.serializer(), jsonBody)
            }.getOrNull()?.takeIf { it.kind == "mira_market_entry" && it.schemaVersion == 1 }
        }

        internal fun parseUpdateEnvelope(body: String?): GitHubMarketUpdateEnvelope? {
            if (body?.lineSequence()?.firstOrNull()?.trim() != BODY_MARKER) return null
            val jsonBody = JSON_BLOCK.find(body)?.groupValues?.get(1) ?: return null
            return runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString(GitHubMarketUpdateEnvelope.serializer(), jsonBody)
            }.getOrNull()?.takeIf { it.kind == "mira_market_update" && it.schemaVersion == 1 }
        }

        internal fun isProtocolBody(body: String?): Boolean =
            body?.lineSequence()?.firstOrNull()?.trim() == BODY_MARKER

        internal fun preserveReplyMarker(existingBody: String, editedBody: String): String {
            require(editedBody.isNotBlank()) { "Comment body is empty" }
            return REPLY_MARKER.find(existingBody)?.value.orEmpty() + editedBody.trim()
        }

        internal fun validatePublishRequest(
            request: MarketV2PublishRequest,
            currentLogin: String
        ) {
            val asset = request.asset
            if (request.type.equals("script", ignoreCase = true) || request.type.equals("package", ignoreCase = true)) {
                requireNotNull(asset) { "Artifact publication requires a GitHub Release asset" }
            }
            if (asset == null) return

            require(currentLogin.isNotBlank()) { "GitHub account is missing" }
            require(asset.ghOwner.equals(currentLogin, ignoreCase = true)) {
                "Artifact owner must match the current GitHub account"
            }
            require(asset.ghRepo.equals(MIRA_FORGE_REPOSITORY, ignoreCase = true)) {
                "Artifact repository must be MiraForge"
            }
            require(SHA256.matches(asset.sha256)) { "Artifact SHA-256 must contain 64 hexadecimal characters" }
            require(asset.ghReleaseTag.isNotBlank()) { "Artifact release tag is missing" }
            require(asset.assetName.isNotBlank()) { "Artifact asset name is missing" }

            val url = asset.url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid artifact URL")
            require(url.isHttps && url.host.equals("github.com", ignoreCase = true)) {
                "Artifact URL must use GitHub HTTPS"
            }
            val segments = url.pathSegments
            require(segments.size == 6) { "Artifact URL must point to a GitHub Release download" }
            require(segments[0].equals(currentLogin, ignoreCase = true)) { "Artifact URL owner mismatch" }
            require(segments[1].equals(MIRA_FORGE_REPOSITORY, ignoreCase = true)) { "Artifact URL repository mismatch" }
            require(segments[2] == "releases" && segments[3] == "download") {
                "Artifact URL must point to a GitHub Release download"
            }
            require(segments[4] == asset.ghReleaseTag) { "Artifact release tag mismatch" }
            require(segments[5] == asset.assetName) { "Artifact asset name mismatch" }
        }

        internal fun mapNotification(record: GitHubMarketNotificationRecord): MarketV2Notification? {
            if (!record.repository.fullName.equals("$OWNER/$REPOSITORY", ignoreCase = true)) return null
            if (!record.subject.type.equals("Issue", ignoreCase = true)) return null
            val issueNumber = parseGitHubApiResourceId(record.subject.url, "issues") ?: return null
            val latestCommentId = parseGitHubApiResourceId(record.subject.latestCommentUrl, "comments")
            return MarketV2Notification(
                id = "github-${record.id}",
                kind =
                    when (record.reason.lowercase()) {
                        "comment" -> "comment_new"
                        "mention", "team_mention", "author", "subscribed" -> "comment_reply"
                        else -> record.reason.lowercase()
                    },
                entryId = entryId(issueNumber),
                commentId = latestCommentId?.let(::commentId),
                title = record.subject.title,
                body = "",
                createdAt = record.updatedAt
            )
        }

        private fun buildProtocolBody(jsonBody: String): String =
            "$BODY_MARKER\n```json\n$jsonBody\n```"

        private fun issueTitle(request: MarketV2PublishRequest): String =
            "[Mira Market/${request.type}] ${request.title}".take(240)

        private fun parseReplyParent(body: String): String? =
            REPLY_MARKER.find(body)?.groupValues?.get(1)?.toLongOrNull()?.let(::commentId)

        private fun parseGitHubApiResourceId(urlValue: String?, resource: String): Long? {
            val url = urlValue?.toHttpUrlOrNull() ?: return null
            if (!url.host.equals(GITHUB_API_HOST, ignoreCase = true)) return null
            val segments = url.pathSegments
            val resourceIndex = segments.indexOfLast { it == resource }
            if (resourceIndex < 0 || resourceIndex + 1 >= segments.size) return null
            return segments[resourceIndex + 1].toLongOrNull()?.takeIf { it > 0 }
        }

        private const val MIRA_FORGE_REPOSITORY = "MiraForge"
        private val SHA256 = Regex("^[0-9a-fA-F]{64}$")
    }
}
