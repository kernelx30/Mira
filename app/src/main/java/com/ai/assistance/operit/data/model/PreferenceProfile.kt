package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/**
 * 用户偏好配置文件
 * 包含多个分类的偏好设置，每个分类可以单独锁定
 */
@Serializable
data class PreferenceProfile(
    val id: String,                // 配置文件唯一标识符
    val name: String,              // 配置文件名称
    val displayName: String = "",  // 用户希望展示的名字
    val preferredAddress: String = "", // 角色称呼用户的方式
    val pronouns: String = "",     // 代词或称谓偏好
    val avatarUri: String = "",    // 此用户资料对应的头像
    val birthDate: Long = 0L,      // 完整出生日期（时间戳）
    val gender: String = "",       // 性别
    val personality: String = "",  // 性格特点
    val identity: String = "",     // 身份认同
    val occupation: String = "",   // 职业
    val aiStyle: String = "",      // 期待的AI风格
    val isInitialized: Boolean = false  // 是否已初始化
)
