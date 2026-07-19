package com.ai.assistance.operit.data.api

import android.os.SystemClock
import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.features.packages.market.GitHubMarketRepository
import com.ai.assistance.operit.ui.features.packages.market.normalizeMarketArtifactId
import com.ai.assistance.operit.util.AppLogger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Serializable
data class MarketStatsEntryResponse(
    val downloads: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MarketTypeStatsResponse(
    val updatedAt: String? = null,
    val items: Map<String, MarketStatsEntryResponse> = emptyMap()
)

@Serializable
data class MarketRankEntryResponse(
    val id: String,
    val downloads: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null,
    val statsUpdatedAt: String? = null,
    val displayTitle: String = "",
    val summaryDescription: String = "",
    val authorLogin: String = "",
    val authorAvatarUrl: String = "",
    val metadata: JsonElement? = null,
    val entry: MarketV2Entry
)

@Serializable
data class MarketRankPageResponse(
    val updatedAt: String? = null,
    val type: String = "",
    val metric: String = "",
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val items: List<MarketRankEntryResponse> = emptyList()
)

@Serializable
data class ArtifactProjectRankDefaultVersionResponse(
    val versionId: String = "",
    val runtimePackageId: String = "",
    val sha256: String = "",
    val version: String = "",
    val downloadUrl: String = "",
    val state: String = "open",
    val publishedAt: String? = null
)

@Serializable
data class ArtifactProjectRankEntryResponse(
    val entryId: String = "",
    val projectId: String = "",
    val type: String = "",
    val projectDisplayName: String = "",
    val projectDescription: String = "",
    val rootPublisherLogin: String = "",
    val rootPublisherAvatarUrl: String = "",
    val contributorCount: Int = 0,
    val downloads: Int = 0,
    val likes: Int = 0,
    val latestVersionId: String = "",
    val defaultVersionId: String = "",
    val latestPublishedAt: String? = null,
    val defaultVersion: ArtifactProjectRankDefaultVersionResponse? = null,
    val runtimePackageVersionSha256s: List<String> = emptyList()
)

@Serializable
data class ArtifactProjectRankPageResponse(
    val updatedAt: String? = null,
    val type: String = "",
    val metric: String = "",
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val items: List<ArtifactProjectRankEntryResponse> = emptyList()
)

@Serializable
data class ArtifactProjectVersionResponse(
    val projectId: String = "",
    val type: String = "",
    val projectDisplayName: String = "",
    val projectDescription: String = "",
    val runtimePackageId: String = "",
    val versionId: String = "",
    val publisherLogin: String = "",
    val releaseTag: String = "",
    val assetName: String = "",
    val assetId: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val version: String = "",
    val displayName: String = "",
    val description: String = "",
    val sourceFileName: String = "",
    val minSupportedAppVersion: String? = null,
    val maxSupportedAppVersion: String? = null,
    val publishedAt: String? = null,
    val state: String = "open",
    val entry: MarketV2Entry
)

@Serializable
data class ArtifactProjectDetailResponse(
    val projectId: String = "",
    val type: String = "",
    val projectDisplayName: String = "",
    val projectDescription: String = "",
    val rootPublisherLogin: String = "",
    val rootPublisherAvatarUrl: String = "",
    val contributorCount: Int = 0,
    val downloads: Int = 0,
    val likes: Int = 0,
    val latestVersionId: String = "",
    val defaultVersionId: String = "",
    val latestPublishedAt: String? = null,
    val versions: List<ArtifactProjectVersionResponse> = emptyList()
)

@Serializable
data class MarketV2ListResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val sort: String = "",
    val page: Int = 1,
    val pageSize: Int = 50,
    val total: Int = 0,
    val items: List<MarketV2Entry> = emptyList()
)

@Serializable
data class MarketV2ManifestResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val types: List<MarketV2ManifestType> = emptyList(),
    val categories: List<MarketV2ManifestCategory> = emptyList(),
    val states: List<MarketV2ManifestState> = emptyList()
)

@Serializable
data class MarketV2ManifestType(
    val id: String = "",
    val name: String = "",
    val description: String = ""
)

@Serializable
data class MarketV2ManifestCategory(
    val id: String = "",
    val name: String = "",
    val description: String = ""
)

@Serializable
data class MarketV2ManifestState(
    val code: String = "",
    val publicListed: Boolean = false
)

@Serializable
data class MarketV2EntriesShardResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val shard: String = "",
    val entriesById: Map<String, MarketV2Entry> = emptyMap()
)

@Serializable
data class MarketV2PublisherEntriesResponse(
    val ok: Boolean = false,
    val generatedAt: String? = null,
    val shard: String = "",
    val entries: List<MarketV2PublisherEntrySummary> = emptyList()
)

@Serializable
data class MarketV2PublisherShardResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val shard: String = "",
    val authors: Map<String, MarketV2PublisherEntriesResponse> = emptyMap()
)

@Serializable
data class MarketV2PublisherEntrySummary(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val relation: String,
    val stateCode: String = "pending",
    val categoryId: String = "",
    val updatedAt: String = "",
    val reasonCodes: List<String> = emptyList()
)

@Serializable
data class MarketV2EntryResponse(
    val ok: Boolean = false,
    val item: MarketV2Entry? = null,
    val entry: MarketV2Entry? = null
)

@Serializable
data class MarketV2CommentsPageResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val entryId: String = "",
    val page: Int = 1,
    val pageSize: Int = 50,
    val total: Int = 0,
    val items: List<MarketV2Comment> = emptyList(),
    val generatedAt: String? = null
)

@Serializable
data class MarketV2NotificationsResponse(
    val ok: Boolean = false,
    val items: List<MarketV2Notification> = emptyList()
)

@Serializable
data class MarketV2NewVersionResponse(
    val ok: Boolean = false,
    val entryId: String = "",
    val versionId: String = ""
)

@Serializable
data class MarketV2Entry(
    val type: String = "",
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val detail: String = "",
    val authorId: String = "",
    val publisherId: String = "",
    val allowPublicUpdates: Boolean = true,
    val categoryId: String = "",
    val stateCode: String = "approved",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val publishedAt: String? = null,
    val source: MarketV2Source? = null,
    val artifact: MarketV2Artifact? = null,
    val assets: List<MarketV2Asset> = emptyList(),
    val versions: List<MarketV2Version> = emptyList(),
    val latestVersion: MarketV2Version? = null,
    val reactions: List<MarketV2Reaction> = emptyList(),
    val downloads: Int = 0,
    val downloadCount: Int = 0,
    val stats: MarketV2EntryStats? = null,
    val author: MarketV2Author? = null,
    val publisher: MarketV2Author? = null,
    val contributors: List<MarketV2Author> = emptyList(),
    val featured: Boolean = false,
)

@Serializable
data class MarketV2EntryStats(
    val downloads: Int = 0,
    val likes: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MarketV2Author(
    val id: String = "",
    val githubId: Long? = null,
    val login: String = "",
    val avatar: String? = null,
    val avatarUrl: String? = null,
    val status: String = ""
)

@Serializable
data class MarketV2Source(
    val kind: String = "",
    val url: String = ""
)

@Serializable
data class MarketV2Artifact(
    val projectId: String = "",
    val runtimePkg: String? = null
)

@Serializable
data class MarketV2Asset(
    val id: String = "",
    val versionId: String = "",
    val kind: String = "",
    val url: String = "",
    val sha256: String = "",
    val name: String = "",
    val assetName: String = ""
)

@Serializable
data class MarketV2Version(
    val id: String = "",
    val version: String = "",
    val formatVer: String = "",
    val publisherId: String = "",
    val publisher: MarketV2Author? = null,
    val minAppVer: String? = null,
    val maxAppVer: String? = null,
    val changelog: String = "",
    val installConfig: String = "",
    val stateCode: String = "approved",
    val publishedAt: String? = null,
    val projectId: String = "",
    val runtimePackageId: String = ""
)

@Serializable
data class MarketV2Reaction(
    val reaction: String = "",
    val content: String = "",
    val total: Int = 0
)

@Serializable
data class MarketV2Comment(
    val id: String = "",
    val entryId: String = "",
    val parentId: String? = null,
    val author: MarketV2Author = MarketV2Author(),
    val body: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class MarketV2Notification(
    val id: String = "",
    val kind: String = "",
    val entryId: String = "",
    val commentId: String? = null,
    val actorId: String? = null,
    val title: String = "",
    val body: String = "",
    val createdAt: String = ""
)

@Serializable
data class MarketV2PublishRequest(
    val type: String,
    val title: String,
    val description: String,
    val detail: String = "",
    val categoryId: String = "",
    val allowPublicUpdates: Boolean = true,
    val version: MarketV2PublishVersion,
    val source: MarketV2PublishSource? = null,
    val repoVersion: MarketV2PublishRepoVersion? = null,
    val asset: MarketV2PublishAsset? = null
)

@Serializable
data class MarketV2EntryUpdateRequest(
    val title: String,
    val description: String,
    val detail: String = "",
    val categoryId: String = "",
    val allowPublicUpdates: Boolean? = null
)

@Serializable
data class MarketV2PublishVersion(
    val version: String,
    val formatVer: String,
    val minAppVer: String,
    val maxAppVer: String? = null,
    val changelog: String? = null,
    val projectId: String? = null,
    val runtimePackageId: String? = null
)

@Serializable
data class MarketV2PublishSource(
    val kind: String,
    val url: String
)

@Serializable
data class MarketV2PublishRepoVersion(
    val refType: String,
    val refName: String,
    val installConfig: String = "{}"
)

@Serializable
data class MarketV2PublishAsset(
    val kind: String,
    val url: String,
    val ghOwner: String = "",
    val ghRepo: String = "",
    val ghReleaseTag: String = "",
    val assetName: String = "",
    val sha256: String = ""
)

class MiraMarketWriteUnavailableException(operation: String) :
    IllegalStateException(
        "The imported Operit market entry is read-only in Mira: $operation"
    )

/** Network boundary for the release build: public catalog reads and direct artifact URLs only. */
object MiraMarketNetworkPolicy {
    const val SUPPORTS_AUTHENTICATED_WRITES: Boolean = true

    fun directArtifactUrl(value: String): String {
        val url = value.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid market artifact URL")
        require(url.isHttps) { "Market artifact URL must use HTTPS" }
        require(!url.host.equals(LEGACY_DYNAMIC_HOST, ignoreCase = true)) {
            "Legacy market download redirects are not accepted"
        }
        return url.toString()
    }

    fun writeUnavailable(operation: String): MiraMarketWriteUnavailableException =
        MiraMarketWriteUnavailableException(operation)

    private const val LEGACY_DYNAMIC_HOST = "api.operit.app"
}

class MarketStatsApiService {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val staticClient = STATIC_CLIENT
    private val authPreferences = GitHubAuthPreferences.getInstance(OperitApplication.instance)
    private val githubMarketRepository = GitHubMarketRepository(OperitApplication.instance)

    suspend fun getManifest(): Result<MarketV2ManifestResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestStaticJson(
                    pathSegments = listOf("market", "v2", "manifest.json"),
                    label = "getManifest"
                ) { body ->
                    json.decodeFromString(MarketV2ManifestResponse.serializer(), body)
                }
            }
        }

    suspend fun getStats(type: String): Result<MarketTypeStatsResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val firstPage =
                    requestStaticJson(
                        pathSegments = typeListPathSegments(type, "updated", 1),
                        label = "getStats type=$type"
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                MarketTypeStatsResponse(
                    updatedAt = firstPage.generatedAt,
                    items =
                        firstPage.items.associate { entry ->
                            entry.id to
                                MarketStatsEntryResponse(
                                    downloads = entry.downloadCountValue(),
                                    lastDownloadAt = entry.stats?.lastDownloadAt,
                                    updatedAt = entry.stats?.updatedAt ?: entry.updatedAt
                                )
                        }
                )
            }
        }

    suspend fun getRankPage(
        type: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sort = metric.toV2Sort()
                val response =
                    requestStaticJson(
                        pathSegments = typeListPathSegments(type, sort, page),
                        label = "getRankPage type=$type metric=$metric page=$page"
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                MarketRankPageResponse(
                    updatedAt = response.generatedAt,
                    type = type,
                    metric = metric,
                    page = response.page,
                    pageSize = response.pageSize,
                    totalPages = ((response.total + response.pageSize - 1) / response.pageSize).coerceAtLeast(1),
                    totalItems = response.total,
                    items = response.items.map { it.toRankEntry() }
                )
            }
        }

    suspend fun getAllRankPage(
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = allListPathSegments(metric.toV2Sort(), page),
            label = "getAllRankPage metric=$metric page=$page",
            type = "",
            metric = metric
        )

    suspend fun getTypeRankPage(
        type: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = typeListPathSegments(type, metric.toV2Sort(), page),
            label = "getTypeRankPage type=$type metric=$metric page=$page",
            type = type,
            metric = metric
        )

    suspend fun getCategoryRankPage(
        categoryId: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = categoryListPathSegments(categoryId, metric.toV2Sort(), page),
            label = "getCategoryRankPage categoryId=$categoryId metric=$metric page=$page",
            type = "",
            metric = metric
        )

    suspend fun getTypedCategoryRankPage(
        type: String,
        categoryId: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = typedCategoryListPathSegments(type, categoryId, metric.toV2Sort(), page),
            label = "getTypedCategoryRankPage type=$type categoryId=$categoryId metric=$metric page=$page",
            type = type,
            metric = metric
        )

    suspend fun getArtifactRankPage(
        type: String,
        metric: String,
        page: Int
    ): Result<ArtifactProjectRankPageResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sort = metric.toV2Sort()
                val response =
                    requestStaticJson(
                        pathSegments = typeListPathSegments(type, sort, page),
                        label = "getArtifactRankPage type=$type metric=$metric page=$page"
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                val projects =
                    response.items
                        .map { it.toArtifactProjectRankEntry() }
                ArtifactProjectRankPageResponse(
                    updatedAt = response.generatedAt,
                    type = type,
                    metric = metric,
                    page = response.page,
                    pageSize = response.pageSize,
                    totalPages = ((response.total + response.pageSize - 1) / response.pageSize).coerceAtLeast(1),
                    totalItems = response.total,
                    items = projects
                )
            }
        }

    private suspend fun getListRankPage(
        pathSegments: List<String>,
        label: String,
        type: String,
        metric: String
    ): Result<MarketRankPageResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    requestStaticJson(
                        pathSegments = pathSegments,
                        label = label
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                MarketRankPageResponse(
                    updatedAt = response.generatedAt,
                    type = type,
                    metric = metric,
                    page = response.page,
                    pageSize = response.pageSize,
                    totalPages = ((response.total + response.pageSize - 1) / response.pageSize).coerceAtLeast(1),
                    totalItems = response.total,
                    items = response.items.map { it.toRankEntry() }
                )
            }
        }

    suspend fun getArtifactProject(
        projectId: String
    ): Result<ArtifactProjectDetailResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entry = getEntry(projectId).getOrElse { error -> throw error }
                    ?: error("Entry not found: $projectId")
                artifactProjectFromEntry(entry)
            }
        }

    fun artifactProjectFromEntry(entry: MarketV2Entry): ArtifactProjectDetailResponse {
        val artifact = entry.artifact ?: error("Entry is not artifact: ${entry.id}")
        val versions = entry.versions
        val latestVersion = versions.firstOrNull() ?: error("Entry versions is empty: ${entry.id}")
        val assetByVersionId = entry.assets.associateBy { it.versionId }
        val artifactVersions = versions.map { version ->
            val runtimePackageId = version.runtimePackageId.ifBlank { error("Artifact runtime package id not found for version: ${version.id}") }
            val asset = assetByVersionId[version.id] ?: error("Artifact asset not found for version: ${version.id}")
            ArtifactProjectVersionResponse(
                projectId = artifact.projectId,
                type = entry.type,
                projectDisplayName = entry.title,
                projectDescription = entry.description,
                runtimePackageId = runtimePackageId,
                versionId = version.id,
                publisherLogin = entry.publisherLogin(),
                releaseTag = "",
                assetName = asset.assetName.ifBlank { asset.name },
                assetId = asset.id,
                downloadUrl = MiraMarketNetworkPolicy.directArtifactUrl(asset.url),
                sha256 = asset.sha256,
                version = version.version,
                displayName = entry.title,
                description = entry.detail,
                sourceFileName = asset.assetName.ifBlank { asset.name },
                minSupportedAppVersion = version.minAppVer,
                maxSupportedAppVersion = version.maxAppVer,
                publishedAt = version.publishedAt,
                state = version.stateCode.toPublicationState(),
                entry = entry.copy(
                    latestVersion = version,
                    assets = listOf(asset)
                )
            )
        }
        return ArtifactProjectDetailResponse(
            projectId = artifact.projectId,
            type = entry.type,
            projectDisplayName = entry.title,
            projectDescription = entry.description,
            rootPublisherLogin = entry.publisherLogin(),
            rootPublisherAvatarUrl = entry.publisherAvatarUrl(),
            contributorCount = 1,
            downloads = entry.downloadCountValue(),
            likes = entry.likesCount(),
            latestVersionId = latestVersion.id,
            defaultVersionId = latestVersion.id,
            latestPublishedAt = latestVersion.publishedAt,
            versions = artifactVersions
        )
    }

    suspend fun getEntry(entryId: String): Result<MarketV2Entry?> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.getEntry(entryId)
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val shard = entryId.marketShard()
                    requestStaticJson(
                        pathSegments = listOf("market", "v2", "entries", "$shard.json"),
                        label = "getEntry entryId=$entryId shard=$shard"
                    ) { body ->
                        json.decodeFromString(MarketV2EntriesShardResponse.serializer(), body)
                    }.entriesById[entryId]
                }
            }
        }

    suspend fun getComments(entryId: String, page: Int = 1): Result<List<MarketV2Comment>> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.getComments(entryId, page)
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    requestStaticJson(
                        pathSegments = listOf("market", "v2", "comments", entryId, "page-$page.json"),
                        label = "getComments entryId=$entryId page=$page",
                        notFoundValue = MarketV2CommentsPageResponse(entryId = entryId, page = page)
                    ) { body ->
                        json.decodeFromString(MarketV2CommentsPageResponse.serializer(), body)
                    }.items
                }
            }
        }

    suspend fun postComment(entryId: String, body: String, parentId: String? = null): Result<MarketV2Comment> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.postComment(entryId, body, parentId)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("comments for legacy entries"))
        }

    suspend fun addReaction(entryId: String): Result<MarketV2Reaction> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.addReaction(entryId)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("reactions for legacy entries"))
        }

    suspend fun getUserPublishedEntries(type: String? = null): Result<List<MarketV2PublisherEntrySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val user = authPreferences.getCurrentUserInfo()
                    ?: error("GitHub login required")
                val legacyEntries = getPublisherEntries("gh_${user.id}").getOrThrow()
                val miraEntries = githubMarketRepository.getCurrentUserEntries(type).getOrThrow()
                (legacyEntries + miraEntries)
                    .filter { type.isNullOrBlank() || it.type.equals(type, ignoreCase = true) }
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedAt }
            }
        }

    suspend fun getPublisherEntries(authorId: String): Result<List<MarketV2PublisherEntrySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedAuthorId = authorId.trim().ifBlank { error("Missing author id") }
                val shard = resolvedAuthorId.marketShard()
                requestStaticJson(
                    pathSegments = listOf("market", "v2", "private", "publishers", "$shard.json"),
                    label = "getPublisherEntries authorId=$resolvedAuthorId shard=$shard",
                    notFoundValue = MarketV2PublisherShardResponse(shard = shard)
                ) { body ->
                    json.decodeFromString(MarketV2PublisherShardResponse.serializer(), body)
                }.authors[resolvedAuthorId]?.entries.orEmpty().sortedByDescending { it.updatedAt }
            }
        }

    suspend fun getNotifications(limit: Int = 50, offset: Int = 0): Result<List<MarketV2Notification>> =
        githubMarketRepository.getNotifications(limit, offset)

    suspend fun editComment(commentId: String, body: String): Result<Unit> =
        if (commentId.startsWith("mira-comment-")) {
            githubMarketRepository.editComment(commentId, body)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("comment editing for legacy entries"))
        }

    suspend fun deleteComment(commentId: String): Result<Unit> =
        if (commentId.startsWith("mira-comment-")) {
            githubMarketRepository.deleteComment(commentId)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("comment deletion for legacy entries"))
        }

    suspend fun publish(request: MarketV2PublishRequest): Result<MarketV2Entry> =
        githubMarketRepository.createEntry(request)

    suspend fun updateEntry(entryId: String, request: MarketV2EntryUpdateRequest): Result<MarketV2Entry> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.updateEntry(entryId, request)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("entry updates for legacy entries"))
        }

    suspend fun publishNewVersion(
        entryId: String,
        request: MarketV2PublishRequest,
        includeEntryPatch: Boolean = false
    ): Result<MarketV2NewVersionResponse> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.publishNewVersion(entryId, request, includeEntryPatch)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("version publication for legacy entries"))
        }

    suspend fun withdrawEntry(entryId: String): Result<MarketV2Entry> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.setEntryOpen(entryId, open = false)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("entry withdrawal for legacy entries"))
        }

    suspend fun resubmitEntry(entryId: String): Result<MarketV2Entry> =
        if (GitHubMarketRepository.isMiraEntryId(entryId)) {
            githubMarketRepository.setEntryOpen(entryId, open = true)
        } else {
            Result.failure(MiraMarketNetworkPolicy.writeUnavailable("entry resubmission for legacy entries"))
        }

    private inline fun <T> requestStaticJson(
        pathSegments: List<String>,
        label: String,
        notFoundValue: T? = null,
        decode: (String) -> T
    ): T {
        val urlBuilder = STATIC_BASE_URL.newBuilder()
        pathSegments.forEach(urlBuilder::addPathSegment)
        val url = urlBuilder.build()

        val request =
            Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .build()

        val startedAt = SystemClock.elapsedRealtime()
        AppLogger.d(TAG, "HTTP GET $label url=$url")
        staticClient.newCall(request).execute().use { response ->
            AppLogger.d(
                TAG,
                "HTTP RESP $label code=${response.code} source=${resolveResponseSource(response)} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms url=$url"
            )
            val body = response.body?.string().orEmpty()
            if (response.code == 404 && notFoundValue != null) {
                return notFoundValue
            }
            if (!response.isSuccessful) {
                error(buildHttpErrorMessage(response, body))
            }
            return decode(body)
        }
    }

    private fun buildHttpErrorMessage(
        response: Response,
        body: String
    ): String {
        val requestPath = response.request.url.encodedPath
        val summary =
            when {
                body.isBlank() -> ""
                body.contains("<html", ignoreCase = true) || body.contains("<!DOCTYPE html", ignoreCase = true) ->
                    " [html body omitted]"
                else -> " ${body.lineSequence().firstOrNull().orEmpty().trim().take(180)}"
            }

        return "HTTP ${response.code}: ${response.message} ($requestPath)$summary"
    }

    private fun resolveResponseSource(response: Response): String {
        return when {
            response.cacheResponse != null && response.networkResponse == null -> "cache"
            response.cacheResponse != null && response.networkResponse != null -> "conditional-cache"
            else -> "network"
        }
    }

    private fun String.toV2Sort(): String {
        return when (lowercase()) {
            "likes" -> "likes"
            "downloads" -> "downloads"
            else -> "updated"
        }
    }

    private fun allListPathSegments(sort: String, page: Int): List<String> {
        return listOf("market", "v2", "lists", "all", sort, "page-$page.json")
    }

    private fun typeListPathSegments(type: String, sort: String, page: Int): List<String> {
        return listOf("market", "v2", "lists", "type", type.lowercase(), sort, "page-$page.json")
    }

    private fun categoryListPathSegments(categoryId: String, sort: String, page: Int): List<String> {
        return listOf("market", "v2", "lists", "category", categoryId.lowercase(), sort, "page-$page.json")
    }

    private fun typedCategoryListPathSegments(
        type: String,
        categoryId: String,
        sort: String,
        page: Int
    ): List<String> {
        return listOf("market", "v2", "lists", "type", type.lowercase(), "category", categoryId.lowercase(), sort, "page-$page.json")
    }

    private fun MarketV2Entry.toRankEntry(): MarketRankEntryResponse {
        return MarketRankEntryResponse(
            id = id,
            downloads = downloadCountValue(),
            lastDownloadAt = stats?.lastDownloadAt,
            updatedAt = updatedAt,
            statsUpdatedAt = stats?.updatedAt ?: updatedAt,
            displayTitle = title,
            summaryDescription = description,
            authorLogin = publisherLogin(),
            authorAvatarUrl = publisherAvatarUrl(),
            metadata = buildJsonObject {
                put("entryId", id)
                put("type", type)
                put("repositoryUrl", source?.url.orEmpty())
                put("categoryId", categoryId)
                put("version", latestVersion?.version.orEmpty())
                put("detail", detail)
            },
            entry = this
        )
    }

    private fun MarketV2Entry.toArtifactProjectRankEntry(): ArtifactProjectRankEntryResponse {
        val artifactValue = artifact
        val version = latestVersion
        val versionId = version?.id.orEmpty().ifBlank { id }
        val asset = assets.firstOrNull { it.versionId == version?.id }
        return ArtifactProjectRankEntryResponse(
            entryId = id,
            projectId = artifactValue?.projectId.orEmpty().ifBlank { normalizeMarketArtifactId(id) },
            type = type,
            projectDisplayName = title,
            projectDescription = description,
            rootPublisherLogin = publisherLogin(),
            rootPublisherAvatarUrl = publisherAvatarUrl(),
            contributorCount = 1,
            downloads = downloadCountValue(),
            likes = likesCount(),
            latestVersionId = versionId,
            defaultVersionId = versionId,
            latestPublishedAt = publishedAt ?: updatedAt,
            defaultVersion =
                ArtifactProjectRankDefaultVersionResponse(
                    versionId = versionId,
                    runtimePackageId = version?.runtimePackageId.orEmpty(),
                    sha256 = asset?.sha256.orEmpty(),
                    version = latestVersion?.version.orEmpty(),
                    downloadUrl = asset?.url?.takeIf(String::isNotBlank)?.let(MiraMarketNetworkPolicy::directArtifactUrl).orEmpty(),
                    state = stateCode.toPublicationState(),
                    publishedAt = publishedAt ?: updatedAt
                ),
            runtimePackageVersionSha256s = listOf(asset?.sha256.orEmpty()).filter(String::isNotBlank)
        )
    }

    private fun MarketV2Entry.publisherUser(): GitHubUser {
        return (publisher ?: author)?.toPublisherUser()
            ?: GitHubUser(
                id = stableLongId(publisherId.ifBlank { authorId }),
                login = publisherLogin(),
                avatarUrl = publisherAvatarUrl()
            )
    }


    private fun MarketV2Author.toPublisherUser(): GitHubUser {
        return GitHubUser(
            id = githubId ?: stableLongId(id.ifBlank { login }),
            login = login.ifBlank { id },
            avatarUrl = avatarUrl ?: avatar ?: ""
        )
    }
    private fun MarketV2Entry.publisherLogin(): String {
        return publisher?.login?.ifBlank { author?.login.orEmpty() }
            ?.ifBlank { publisherId.removePrefix("gh_").ifBlank { authorId.removePrefix("gh_") } }
            .orEmpty()
    }

    private fun MarketV2Entry.publisherAvatarUrl(): String {
        return publisher?.avatarUrl ?: publisher?.avatar ?: author?.avatarUrl ?: author?.avatar ?: ""
    }

    private fun MarketV2Entry.downloadCountValue(): Int {
        return stats?.downloads ?: downloadCount.takeIf { it > 0 } ?: downloads
    }

    private fun MarketV2Entry.likesCount(): Int {
        return stats?.likes ?: reactions.sumOf { reaction ->
            val key = reaction.reaction.ifBlank { reaction.content }
            if (key == "+1" || key.equals("like", ignoreCase = true)) reaction.total.coerceAtLeast(1) else 0
        }
    }

    private fun String.toPublicationState(): String {
        return if (equals("withdrawn", ignoreCase = true)) "closed" else "open"
    }

    private fun String.marketShard(): String {
        return fnv1a32Hex(this).take(2)
    }

    private fun stableLongId(value: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        var result = 0L
        for (index in 0 until 8) {
            result = (result shl 8) or (bytes[index].toLong() and 0xff)
        }
        return result and Long.MAX_VALUE
    }

    private fun fnv1a32Hex(value: String): String {
        var hash = 0x811c9dc5u
        value.forEach { char ->
            hash = hash xor char.code.toUInt()
            hash *= 0x01000193u
        }
        return hash.toString(16)
    }

    companion object {
        private const val TAG = "MarketStatsApiService"
        private const val USER_AGENT = "Mira-Market-V2 (Operit-compatible)"
        private const val TIMEOUT_SECONDS = 15L
        // Mira owns the public static catalog. The dynamic API below remains the
        // compatibility catalog until GitHub-native entries are indexed.
        private val STATIC_BASE_URL = "https://kernelx30.github.io/Mira".toHttpUrl()

        private val STATIC_CLIENT by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

    }
}
