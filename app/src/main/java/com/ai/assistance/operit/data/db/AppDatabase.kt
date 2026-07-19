package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.dao.CompanionMemoryDao
import com.ai.assistance.operit.data.dao.MessageDao
import com.ai.assistance.operit.data.dao.MessageVariantDao
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEpisodeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEvidenceEntity
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.MemoryGrantEntity
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity

/** 应用数据库，包含聊天表和消息表 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageVariantEntity::class,
        CompanionMemoryRecordEntity::class,
        CompanionMemoryEvidenceEntity::class,
        CompanionMemoryEpisodeEntity::class,
        CompanionMemoryEdgeEntity::class,
        MemoryGrantEntity::class,
    ],
    version = 28,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** 获取聊天DAO */
    abstract fun chatDao(): ChatDao

    /** 获取消息DAO */
    abstract fun messageDao(): MessageDao

    abstract fun messageVariantDao(): MessageVariantDao

    abstract fun companionMemoryDao(): CompanionMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 定义从版本1到2的迁移
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 创建chats表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `chats` (
                                `id` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent()
                    )

                    // 创建messages表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `messages` (
                                `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `sender` TEXT NOT NULL,
                                `content` TEXT NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `orderIndex` INTEGER NOT NULL,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )

                    // 为messages表创建索引
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
                }

            }

        // 定义从版本10到11的迁移
        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspaceEnv列
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `workspaceEnv` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本11到12的迁移
        private val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加characterGroupId列（用于绑定群组角色卡）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `characterGroupId` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        private val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `inputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `cachedInputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `sentAt` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `waitDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                }
            }

        private val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `problem_records`")
                }
            }

        private val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `selectedVariantIndex` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `message_variants` (
                                `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `messageTimestamp` INTEGER NOT NULL,
                                `variantIndex` INTEGER NOT NULL,
                                `content` TEXT NOT NULL,
                                `roleName` TEXT NOT NULL DEFAULT '',
                                `provider` TEXT NOT NULL DEFAULT '',
                                `modelName` TEXT NOT NULL DEFAULT '',
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                `cachedInputTokens` INTEGER NOT NULL DEFAULT 0,
                                `sentAt` INTEGER NOT NULL DEFAULT 0,
                                `outputDurationMs` INTEGER NOT NULL DEFAULT 0,
                                `waitDurationMs` INTEGER NOT NULL DEFAULT 0,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp` ON `message_variants` (`chatId`, `messageTimestamp`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp_variantIndex` ON `message_variants` (`chatId`, `messageTimestamp`, `variantIndex`)"
                    )
                }
            }

        private val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `displayMode` TEXT NOT NULL DEFAULT 'NORMAL'"
                    )
                }
            }

        private val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)"
                    )
                }
            }

        private val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE message_variants ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_19_20 =
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_20_21 =
            object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `companion_memory_records` (
                            `id` TEXT NOT NULL, `profileId` TEXT NOT NULL,
                            `companionId` TEXT NOT NULL, `conversationId` TEXT NOT NULL,
                            `scope` TEXT NOT NULL, `type` TEXT NOT NULL,
                            `subjectKey` TEXT NOT NULL, `predicate` TEXT NOT NULL,
                            `valueJson` TEXT NOT NULL, `normalizedValue` TEXT NOT NULL,
                            `status` TEXT NOT NULL, `confidence` REAL NOT NULL,
                            `importance` REAL NOT NULL, `validFrom` INTEGER,
                            `validUntil` INTEGER, `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL, `lastConfirmedAt` INTEGER NOT NULL,
                            `lastAccessedAt` INTEGER NOT NULL, `sourceKind` TEXT NOT NULL,
                            `versionOfId` TEXT, `supersedesId` TEXT, `reviewAt` INTEGER,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_records_profileId_status` ON `companion_memory_records` (`profileId`, `status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_records_profileId_companionId_scope_status` ON `companion_memory_records` (`profileId`, `companionId`, `scope`, `status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_records_profileId_scope_type_subjectKey_predicate_status` ON `companion_memory_records` (`profileId`, `scope`, `type`, `subjectKey`, `predicate`, `status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_records_conversationId` ON `companion_memory_records` (`conversationId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_records_normalizedValue` ON `companion_memory_records` (`normalizedValue`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_records_updatedAt` ON `companion_memory_records` (`updatedAt`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `companion_memory_evidence` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `memoryId` TEXT NOT NULL, `conversationId` TEXT NOT NULL,
                            `messageId` INTEGER, `messageTimestamp` INTEGER NOT NULL,
                            `quote` TEXT NOT NULL, `speaker` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            FOREIGN KEY(`memoryId`) REFERENCES `companion_memory_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_evidence_memoryId` ON `companion_memory_evidence` (`memoryId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_evidence_conversationId_messageTimestamp` ON `companion_memory_evidence` (`conversationId`, `messageTimestamp`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_companion_memory_evidence_memoryId_conversationId_messageTimestamp_speaker` ON `companion_memory_evidence` (`memoryId`, `conversationId`, `messageTimestamp`, `speaker`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `companion_memory_episodes` (
                            `id` TEXT NOT NULL, `profileId` TEXT NOT NULL,
                            `companionId` TEXT NOT NULL, `conversationId` TEXT NOT NULL,
                            `startMessageTimestamp` INTEGER NOT NULL,
                            `endMessageTimestamp` INTEGER NOT NULL,
                            `summary` TEXT NOT NULL, `topicTags` TEXT NOT NULL,
                            `emotionState` TEXT NOT NULL, `outcome` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL, `validUntil` INTEGER,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_episodes_profileId_companionId_createdAt` ON `companion_memory_episodes` (`profileId`, `companionId`, `createdAt`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_episodes_conversationId_endMessageTimestamp` ON `companion_memory_episodes` (`conversationId`, `endMessageTimestamp`)")
                    ensureCompanionMemorySearchIndex(db)
                }
            }

        private val MIGRATION_21_22 =
            object : Migration(21, 22) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN `chatModelConfigId` TEXT")
                    db.execSQL("ALTER TABLE chats ADD COLUMN `chatModelIndex` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE chats ADD COLUMN `memoryAutoUpdateOverride` INTEGER")
                    db.execSQL("ALTER TABLE chats ADD COLUMN `autoReadOverride` INTEGER")
                    db.execSQL("ALTER TABLE chats ADD COLUMN `isTemporary` INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_22_23 =
            object : Migration(22, 23) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `companion_memory_edges` (
                            `id` TEXT NOT NULL,
                            `fromMemoryId` TEXT NOT NULL,
                            `toMemoryId` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `confidence` REAL NOT NULL,
                            `strength` REAL NOT NULL,
                            `evidenceMessageIds` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `validUntil` INTEGER,
                            `status` TEXT NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`fromMemoryId`) REFERENCES `companion_memory_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`toMemoryId`) REFERENCES `companion_memory_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_edges_fromMemoryId_status` ON `companion_memory_edges` (`fromMemoryId`, `status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_edges_toMemoryId_status` ON `companion_memory_edges` (`toMemoryId`, `status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_companion_memory_edges_type_status` ON `companion_memory_edges` (`type`, `status`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_companion_memory_edges_fromMemoryId_toMemoryId_type_status` ON `companion_memory_edges` (`fromMemoryId`, `toMemoryId`, `type`, `status`)")
                }
            }

        private val MIGRATION_23_24 =
            object : Migration(23, 24) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_24_25 =
            object : Migration(24, 25) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    ensureCompanionMemorySearchIndex(db)
                }
            }

        private val MIGRATION_25_26 =
            object : Migration(25, 26) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 25 版实体没有这些字段。迁移必须逐项执行并暴露真实 schema 错误，
                    // 否则吞掉 ALTER TABLE 异常后，Room 会在最终 schema 校验阶段才崩溃。
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN subjectScope TEXT NOT NULL DEFAULT 'USER'")
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN ownerScope TEXT NOT NULL DEFAULT 'GLOBAL_USER'")
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN ownerCompanionId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PRIVATE'")
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN perspective TEXT NOT NULL DEFAULT 'USER_DISCLOSED'")
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN evidenceKind TEXT NOT NULL DEFAULT 'USER_DIRECT'")
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN triggerKind TEXT NOT NULL DEFAULT 'AUTO_EXTRACT'")
                    db.execSQL("ALTER TABLE companion_memory_records ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE companion_memory_evidence ADD COLUMN evidenceKind TEXT NOT NULL DEFAULT 'USER_DIRECT'")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS companion_memory_grants (
                            id TEXT NOT NULL PRIMARY KEY,
                            memoryId TEXT NOT NULL,
                            granteeCompanionId TEXT NOT NULL,
                            permission TEXT NOT NULL,
                            includeSuccessors INTEGER NOT NULL,
                            grantedBy TEXT NOT NULL,
                            grantedAt INTEGER NOT NULL,
                            revokedAt INTEGER,
                            FOREIGN KEY(memoryId) REFERENCES companion_memory_records(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_companion_memory_grants_memoryId ON companion_memory_grants(memoryId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_companion_memory_grants_granteeCompanionId_revokedAt ON companion_memory_grants(granteeCompanionId, revokedAt)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_companion_memory_grants_memoryId_granteeCompanionId ON companion_memory_grants(memoryId, granteeCompanionId)")
                    db.execSQL("UPDATE companion_memory_records SET subjectScope = scope")
                    db.execSQL("UPDATE companion_memory_records SET ownerCompanionId = companionId WHERE ownerCompanionId = '' AND companionId <> ''")
                    db.execSQL(
                        """
                        UPDATE companion_memory_records
                        SET ownerCompanionId = COALESCE((
                            SELECT CASE
                                WHEN chat.characterGroupId IS NOT NULL AND TRIM(chat.characterGroupId) <> ''
                                    THEN 'group:' || TRIM(chat.characterGroupId)
                                WHEN chat.characterCardName IS NOT NULL AND TRIM(chat.characterCardName) <> ''
                                    THEN 'character_name:' || TRIM(chat.characterCardName)
                                ELSE ''
                            END
                            FROM chats chat
                            WHERE chat.id = companion_memory_records.conversationId
                            LIMIT 1
                        ), '')
                        WHERE scope = 'USER'
                          AND ownerCompanionId = ''
                          AND conversationId <> ''
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "UPDATE companion_memory_records SET companionId = ownerCompanionId WHERE scope = 'USER' AND companionId = '' AND ownerCompanionId <> ''",
                    )
                    db.execSQL(
                        "UPDATE companion_memory_records SET ownerScope = CASE scope WHEN 'COMPANION' THEN 'COMPANION_PRIVATE' WHEN 'RELATIONSHIP' THEN 'RELATIONSHIP_PAIR' WHEN 'CONVERSATION' THEN 'CONVERSATION' WHEN 'USER' THEN CASE WHEN ownerCompanionId <> '' THEN 'RELATIONSHIP_PAIR' ELSE 'GLOBAL_USER' END ELSE 'GLOBAL_USER' END",
                    )
                    db.execSQL("UPDATE companion_memory_records SET visibility = CASE WHEN ownerScope = 'GLOBAL_USER' THEN 'ALL_COMPANIONS' WHEN ownerScope = 'CONVERSATION' THEN 'PARTICIPANTS' ELSE 'PRIVATE' END")
                    db.execSQL(
                        "UPDATE companion_memory_records SET needsReview = 1, reviewAt = updatedAt WHERE scope = 'USER' AND ownerCompanionId = ''",
                    )
                }
            }

        private val MIGRATION_26_27 =
            object : Migration(26, 27) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `speechDirectionJson` TEXT"
                    )
                    db.execSQL(
                        "ALTER TABLE message_variants ADD COLUMN `speechDirectionJson` TEXT"
                    )
                }
            }

        private val MIGRATION_27_28 =
            object : Migration(27, 28) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE companion_memory_edges ADD COLUMN `pendingEvidenceReferencesJson` TEXT"
                    )
                }
            }

        private val COMPANION_MEMORY_DATABASE_CALLBACK =
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    ensureCompanionMemorySearchIndex(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    ensureCompanionMemorySearchIndex(db)
                }
            }

        private fun ensureCompanionMemorySearchIndex(db: SupportSQLiteDatabase) {
            val existingCreateSql =
                db.query(
                    "SELECT `sql` FROM `sqlite_master` WHERE `type` = 'table' AND `name` = 'companion_memory_fts'",
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            if (companionMemorySearchIndexNeedsRepair(existingCreateSql)) {
                db.execSQL("DROP TRIGGER IF EXISTS `companion_memory_fts_insert`")
                db.execSQL("DROP TRIGGER IF EXISTS `companion_memory_fts_update`")
                db.execSQL("DROP TRIGGER IF EXISTS `companion_memory_fts_delete`")
                db.execSQL("DROP TABLE IF EXISTS `companion_memory_fts`")
            }
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `companion_memory_fts` USING fts4(`recordId`, `searchableText`, tokenize=unicode61)",
            )
            db.execSQL("DROP TRIGGER IF EXISTS `companion_memory_fts_insert`")
            db.execSQL("DROP TRIGGER IF EXISTS `companion_memory_fts_update`")
            db.execSQL("DROP TRIGGER IF EXISTS `companion_memory_fts_delete`")
            db.execSQL(
                """
                CREATE TRIGGER `companion_memory_fts_insert`
                AFTER INSERT ON `companion_memory_records`
                BEGIN
                    INSERT INTO `companion_memory_fts` (`recordId`, `searchableText`)
                    SELECT new.`id`, new.`subjectKey` || ' ' || new.`predicate` || ' ' || new.`normalizedValue` || ' ' || new.`valueJson`
                    WHERE new.`status` = 'ACTIVE';
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER `companion_memory_fts_update`
                AFTER UPDATE ON `companion_memory_records`
                BEGIN
                    DELETE FROM `companion_memory_fts` WHERE `recordId` = old.`id`;
                    INSERT INTO `companion_memory_fts` (`recordId`, `searchableText`)
                    SELECT new.`id`, new.`subjectKey` || ' ' || new.`predicate` || ' ' || new.`normalizedValue` || ' ' || new.`valueJson`
                    WHERE new.`status` = 'ACTIVE';
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER `companion_memory_fts_delete`
                AFTER DELETE ON `companion_memory_records`
                BEGIN
                    DELETE FROM `companion_memory_fts` WHERE `recordId` = old.`id`;
                END
                """.trimIndent(),
            )
            db.execSQL(
                "DELETE FROM `companion_memory_fts` WHERE `recordId` IN (SELECT `id` FROM `companion_memory_records` WHERE `status` != 'ACTIVE')",
            )
            db.execSQL(
                """
                INSERT INTO `companion_memory_fts` (`recordId`, `searchableText`)
                SELECT r.`id`, r.`subjectKey` || ' ' || r.`predicate` || ' ' || r.`normalizedValue` || ' ' || r.`valueJson`
                FROM `companion_memory_records` r
                WHERE r.`status` = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM `companion_memory_fts` f WHERE f.`recordId` = r.`id`)
                """.trimIndent(),
            )
        }

        // 定义从版本2到3的迁移
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加group列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `group` TEXT")
                }
            }

        // 定义从版本3到4的迁移
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加displayOrder列，并用updatedAt填充现有数据
                    db.execSQL(
                        "ALTER TABLE chats ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL("UPDATE chats SET displayOrder = updatedAt")
                }
            }

        // 定义从版本4到5的迁移
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspace列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `workspace` TEXT")
                }
            }

        // 定义从版本5到6的迁移
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 检查currentWindowSize列是否已存在，如果不存在则添加
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `currentWindowSize` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本6到7的迁移
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加roleName列
                    db.execSQL("ALTER TABLE messages ADD COLUMN `roleName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本7到8的迁移
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加parentChatId列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `parentChatId` TEXT")
                    // 向chats表添加characterCardName列（用于绑定角色卡）
                    db.execSQL("ALTER TABLE chats ADD COLUMN `characterCardName` TEXT")
                }
            }

        // 定义从版本8到9的迁移
        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加provider列（供应商）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `provider` TEXT NOT NULL DEFAULT ''")
                    // 向messages表添加modelName列（模型名称）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本9到10的迁移
        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加locked列（锁定聊天，禁止删除）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        /** 获取数据库实例，单例模式 */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "app_database"
                        )
                            .addMigrations(
                                MIGRATION_1_2,
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6,
                                MIGRATION_6_7,
                                MIGRATION_7_8,
                                MIGRATION_8_9,
                                MIGRATION_9_10,
                                MIGRATION_10_11,
                                MIGRATION_11_12,
                                MIGRATION_12_13,
                                MIGRATION_13_14,
                                MIGRATION_14_15,
                                MIGRATION_15_16,
                                MIGRATION_16_17,
                                MIGRATION_17_18,
                                MIGRATION_18_19,
                                MIGRATION_19_20,
                                MIGRATION_20_21,
                                MIGRATION_21_22,
                                MIGRATION_22_23,
                                MIGRATION_23_24,
                                MIGRATION_24_25,
                                MIGRATION_25_26,
                                MIGRATION_26_27,
                                MIGRATION_27_28,
                            ) // 添加新的迁移
                            .addCallback(COMPANION_MEMORY_DATABASE_CALLBACK)
                            .build()
                    INSTANCE = instance
                    instance
                }
        }

        fun closeDatabase() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } finally {
                    INSTANCE = null
                }
            }
        }
    }
}

internal fun companionMemorySearchIndexNeedsRepair(createSql: String?): Boolean =
    createSql != null &&
        !Regex("\\bUSING\\s+fts[345]\\b", RegexOption.IGNORE_CASE).containsMatchIn(createSql)
