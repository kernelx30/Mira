package com.ai.assistance.operit.data.api

import android.content.Context
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.*

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubAccessTokenResponse(
    val access_token: String,
    val token_type: String,
    val scope: String? = null
)

@Serializable
data class GitHubDeviceAuthorizationResponse(
    val device_code: String,
    val user_code: String,
    val verification_uri: String,
    val expires_in: Long,
    val interval: Long = 5L
)

@Serializable
private data class GitHubDeviceTokenWireResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val scope: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

sealed interface GitHubDeviceTokenPollResult {
    data class Granted(val token: GitHubAccessTokenResponse) : GitHubDeviceTokenPollResult
    data object AuthorizationPending : GitHubDeviceTokenPollResult
    data object SlowDown : GitHubDeviceTokenPollResult
    data object Expired : GitHubDeviceTokenPollResult
    data object AccessDenied : GitHubDeviceTokenPollResult
    data class Rejected(
        val error: String,
        val description: String?
    ) : GitHubDeviceTokenPollResult
}

private val githubDeviceFlowJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun decodeGitHubDeviceTokenResponse(responseBody: String): GitHubDeviceTokenPollResult {
    val wire = githubDeviceFlowJson.decodeFromString<GitHubDeviceTokenWireResponse>(responseBody)
    val accessToken = wire.access_token?.takeIf { it.isNotBlank() }
    val tokenType = wire.token_type?.takeIf { it.isNotBlank() }
    if (accessToken != null && tokenType != null) {
        return GitHubDeviceTokenPollResult.Granted(
            GitHubAccessTokenResponse(
                access_token = accessToken,
                token_type = tokenType,
                scope = wire.scope
            )
        )
    }

    if (accessToken != null) {
        return GitHubDeviceTokenPollResult.Rejected(
            error = "invalid_response",
            description = "GitHub omitted token_type"
        )
    }

    return when (wire.error) {
        "authorization_pending" -> GitHubDeviceTokenPollResult.AuthorizationPending
        "slow_down" -> GitHubDeviceTokenPollResult.SlowDown
        "expired_token" -> GitHubDeviceTokenPollResult.Expired
        "access_denied" -> GitHubDeviceTokenPollResult.AccessDenied
        null -> GitHubDeviceTokenPollResult.Rejected(
            error = "invalid_response",
            description = "GitHub returned neither an access token nor an error"
        )
        else -> GitHubDeviceTokenPollResult.Rejected(
            error = wire.error,
            description = wire.error_description
        )
    }
}

@Serializable
data class GitHubRepository(
    val id: Long,
    val name: String,
    val full_name: String,
    val description: String?,
    val html_url: String,
    val clone_url: String,
    val stargazers_count: Int,
    val forks_count: Int,
    val language: String?,
    val topics: List<String> = emptyList(),
    val size: Int = 0,
    @SerialName("default_branch")
    val defaultBranch: String = "",
    val created_at: String,
    val updated_at: String,
    val owner: GitHubUser
)


@Serializable
data class CreateRepositoryRequest(
    val name: String,
    val description: String? = null,
    val homepage: String? = null,
    val `private`: Boolean = false,
    val has_issues: Boolean = true,
    val has_projects: Boolean = false,
    val has_wiki: Boolean = false,
    val auto_init: Boolean = false
)

@Serializable
data class CreateRepositoryContentRequest(
    val message: String,
    val content: String,
    val branch: String? = null,
    val sha: String? = null
)

@Serializable
data class GitHubRepositoryContentFile(
    val name: String,
    val path: String,
    val sha: String,
    val type: String
)

@Serializable
data class CreateReleaseRequest(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

@Serializable
data class UpdateReleaseRequest(
    val tag_name: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null
)


@Serializable
data class GitHubRelease(
    val id: Long,
    val tag_name: String,
    val name: String?,
    val body: String?,
    val html_url: String,
    val upload_url: String? = null,
    val published_at: String,
    val created_at: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val browser_download_url: String,
    val size: Long,
    val download_count: Int,
    val content_type: String
)

/**
 * GitHub API服务类
 * 提供GitHub OAuth认证、用户信息、仓库操作等功能
 */
class GitHubApiService(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
                .addHeader("User-Agent", "Mira-Android")
            if (request.header("Accept") == null) {
                builder.addHeader("Accept", "application/vnd.github.v3+json")
            }
            val newRequest = builder.build()
            chain.proceed(newRequest)
        }
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val authPreferences = GitHubAuthPreferences.getInstance(context)
    
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val GITHUB_DEVICE_CODE_URL = "https://github.com/login/device/code"
        private const val GITHUB_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    }

    suspend fun requestDeviceAuthorization(
        clientId: String,
        scope: String
    ): Result<GitHubDeviceAuthorizationResponse> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", scope)
                .build()

            val request = Request.Builder()
                .url(GITHUB_DEVICE_CODE_URL)
                .post(formBody)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val authorization = try {
                        json.decodeFromString<GitHubDeviceAuthorizationResponse>(responseBody)
                    } catch (error: Exception) {
                        return@use Result.failure(
                            IllegalStateException("GitHub returned an invalid device authorization")
                        )
                    }
                    val verificationUrl = authorization.verification_uri.toHttpUrlOrNull()
                    if (
                        authorization.device_code.isBlank() ||
                        authorization.user_code.isBlank() ||
                        verificationUrl == null ||
                        verificationUrl.scheme != "https" ||
                        verificationUrl.host != "github.com" ||
                        authorization.expires_in <= 0L
                    ) {
                        Result.failure(IllegalStateException("GitHub returned an invalid device authorization"))
                    } else {
                        Result.success(authorization)
                    }
                } else {
                    Result.failure(Exception("GitHub device authorization failed (HTTP ${response.code})"))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollDeviceAccessToken(
        clientId: String,
        deviceCode: String
    ): Result<GitHubDeviceTokenPollResult> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()

            val request = Request.Builder()
                .url(GITHUB_ACCESS_TOKEN_URL)
                .post(formBody)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    try {
                        Result.success(decodeGitHubDeviceTokenResponse(responseBody))
                    } catch (error: Exception) {
                        Result.failure(IllegalStateException("GitHub returned an invalid token response"))
                    }
                } else {
                    Result.failure(Exception("GitHub token polling failed (HTTP ${response.code})"))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUser(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))
            getCurrentUserWithAuthorization(authHeader)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Validates a newly issued token without changing the stored login session. */
    suspend fun getCurrentUserWithAccessToken(accessToken: String): Result<GitHubUser> =
        withContext(Dispatchers.IO) {
            try {
                if (accessToken.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Access token is empty"))
                }
                getCurrentUserWithAuthorization("Bearer $accessToken")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun getCurrentUserWithAuthorization(authorization: String): Result<GitHubUser> {
        val request = Request.Builder()
            .url("$GITHUB_API_BASE/user")
            .addHeader("Authorization", authorization)
            .build()

        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                Result.success(json.decodeFromString<GitHubUser>(responseBody))
            } else {
                Result.failure(Exception("GitHub user verification failed (HTTP ${response.code})"))
            }
        }
    }
    
    /**
     * 根据用户名获取GitHub用户信息
     */
    suspend fun getUser(username: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$GITHUB_API_BASE/users/$username")
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }

            val response = client.newCall(requestBuilder.build()).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val user = json.decodeFromString<GitHubUser>(responseBody)
                    Result.success(user)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 搜索仓库
     */
    suspend fun searchRepositories(
        query: String,
        sort: String = "stars",
        order: String = "desc",
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubRepository>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .addPathSegment("search")
                .addPathSegment("repositories")
                .addQueryParameter("q", query)
                .addQueryParameter("sort", sort)
                .addQueryParameter("order", order)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .build()
            
            val requestBuilder = Request.Builder()
                .url(url)
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }

            val response = client.newCall(requestBuilder.build()).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val searchResult = json.parseToJsonElement(responseBody).jsonObject
                    val itemsArray = searchResult["items"]?.jsonArray
                    val repositories = itemsArray?.map { item ->
                        json.decodeFromJsonElement(GitHubRepository.serializer(), item)
                    } ?: emptyList()
                    Result.success(repositories)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取用户仓库
     */
    suspend fun getUserRepositories(
        username: String? = null,
        type: String = "all",
        sort: String = "updated",
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubRepository>> = withContext(Dispatchers.IO) {
        try {
            val url = if (username != null) {
                "$GITHUB_API_BASE/users/$username/repos"
            } else {
                "$GITHUB_API_BASE/user/repos"
            }
            
            val httpUrl = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .apply {
                    if (username != null) {
                        addPathSegment("users")
                        addPathSegment(username)
                        addPathSegment("repos")
                    } else {
                        addPathSegment("user")
                        addPathSegment("repos")
                    }
                }
                .addQueryParameter("type", type)
                .addQueryParameter("sort", sort)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .build()
            
            val requestBuilder = Request.Builder().url(httpUrl)
            
            // 如果是获取当前用户的仓库，需要认证
            if (username == null) {
                val authHeader = authPreferences.getAuthorizationHeader()
                    ?: return@withContext Result.failure(Exception("No access token available"))
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val repositories = json.decodeFromString<List<GitHubRepository>>(responseBody)
                    Result.success(repositories)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取仓库信息
     */
    suspend fun getRepository(
        owner: String,
        repo: String
    ): Result<GitHubRepository> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo")
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val repository = json.decodeFromString<GitHubRepository>(responseBody)
                    Result.success(repository)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取仓库的Releases
     */
    suspend fun getRepositoryReleases(
        owner: String,
        repo: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .addPathSegment("repos")
                .addPathSegment(owner)
                .addPathSegment(repo)
                .addPathSegment("releases")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .build()
            
            val requestBuilder = Request.Builder()
                .url(url)
            
            // 如果用户已登录，添加认证头以提高API配额
            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val releases = json.decodeFromString<List<GitHubRelease>>(responseBody)
                    Result.success(releases)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRepository(
        name: String,
        description: String? = null,
        homepage: String? = null,
        isPrivate: Boolean = false,
        autoInit: Boolean = false
    ): Result<GitHubRepository> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val requestPayload =
                CreateRepositoryRequest(
                    name = name,
                    description = description,
                    homepage = homepage,
                    `private` = isPrivate,
                    auto_init = autoInit
                )
            val requestBody =
                json.encodeToString(
                    CreateRepositoryRequest.serializer(),
                    requestPayload
                )

            val request =
                Request.Builder()
                    .url("$GITHUB_API_BASE/user/repos")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Result.success(json.decodeFromString(GitHubRepository.serializer(), responseBody))
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTextFile(
        owner: String,
        repo: String,
        path: String,
        message: String,
        textContent: String,
        branch: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val existingFileSha =
                getRepositoryContentFile(
                    owner = owner,
                    repo = repo,
                    path = path
                ).fold(
                    onSuccess = { it?.sha },
                    onFailure = { error -> return@withContext Result.failure(error) }
                )

            val requestPayload =
                CreateRepositoryContentRequest(
                    message = message,
                    content = Base64.getEncoder().encodeToString(textContent.toByteArray(Charsets.UTF_8)),
                    branch = branch,
                    sha = existingFileSha
                )
            val requestBody =
                json.encodeToString(
                    CreateRepositoryContentRequest.serializer(),
                    requestPayload
                )

            val request =
                Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/contents/$path")
                    .put(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getRepositoryContentFile(
        owner: String,
        repo: String,
        path: String
    ): Result<GitHubRepositoryContentFile?> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder =
                Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/contents/$path")
                    .addHeader("Accept", "application/vnd.github+json")

            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Result.success(
                    json.decodeFromString(
                        GitHubRepositoryContentFile.serializer(),
                        responseBody
                    )
                )
            } else if (response.code == 404) {
                Result.success(null)
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReleaseByTag(
        owner: String,
        repo: String,
        tag: String
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder =
                Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/releases/tags/$tag")
                    .addHeader("Accept", "application/vnd.github+json")

            authPreferences.getAuthorizationHeader()?.let { authHeader ->
                requestBuilder.addHeader("Authorization", authHeader)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Result.success(json.decodeFromString(GitHubRelease.serializer(), responseBody))
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findReleaseByTag(
        owner: String,
        repo: String,
        tag: String
    ): Result<GitHubRelease?> = withContext(Dispatchers.IO) {
        getReleaseByTag(owner, repo, tag).fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (error.message?.contains("HTTP 404") == true) {
                    Result.success(null)
                } else {
                    Result.failure(error)
                }
            }
        )
    }

    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String? = null,
        body: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val payload =
                CreateReleaseRequest(
                    tag_name = tagName,
                    name = name,
                    body = body,
                    draft = draft,
                    prerelease = prerelease
                )
            val requestBody =
                json.encodeToString(
                    CreateReleaseRequest.serializer(),
                    payload
                )

            val request =
                Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/releases")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Result.success(json.decodeFromString(GitHubRelease.serializer(), responseBody))
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRelease(
        owner: String,
        repo: String,
        releaseId: Long,
        tagName: String? = null,
        name: String? = null,
        body: String? = null,
        draft: Boolean? = null,
        prerelease: Boolean? = null
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val payload =
                UpdateReleaseRequest(
                    tag_name = tagName,
                    name = name,
                    body = body,
                    draft = draft,
                    prerelease = prerelease
                )
            val requestBody =
                json.encodeToString(
                    UpdateReleaseRequest.serializer(),
                    payload
                )

            val request =
                Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/releases/$releaseId")
                    .patch(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Result.success(json.decodeFromString(GitHubRelease.serializer(), responseBody))
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRelease(
        owner: String,
        repo: String,
        releaseId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val request =
                Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/releases/$releaseId")
                    .delete()
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteReleaseAsset(
        owner: String,
        repo: String,
        assetId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val request =
                Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/releases/assets/$assetId")
                    .delete()
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadReleaseAsset(
        owner: String,
        repo: String,
        releaseId: Long,
        assetName: String,
        contentType: String,
        content: ByteArray
    ): Result<GitHubReleaseAsset> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authPreferences.getAuthorizationHeader()
                ?: return@withContext Result.failure(Exception("No access token available"))

            val uploadUrl =
                HttpUrl.Builder()
                    .scheme("https")
                    .host("uploads.github.com")
                    .addPathSegment("repos")
                    .addPathSegment(owner)
                    .addPathSegment(repo)
                    .addPathSegment("releases")
                    .addPathSegment(releaseId.toString())
                    .addPathSegment("assets")
                    .addQueryParameter("name", assetName)
                    .build()

            val request =
                Request.Builder()
                    .url(uploadUrl)
                    .post(content.toRequestBody(contentType.toMediaType()))
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Result.success(json.decodeFromString(GitHubReleaseAsset.serializer(), responseBody))
            } else {
                Result.failure(buildHttpException(response.code, response.message, responseBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildHttpException(
        code: Int,
        message: String,
        responseBody: String?
    ): Exception {
        val body = responseBody?.takeIf { it.isNotBlank() }
        return Exception(
            if (body != null) {
                "HTTP $code: $message\n$body"
            } else {
                "HTTP $code: $message"
            }
        )
    }
}
