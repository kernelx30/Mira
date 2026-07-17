package com.ai.assistance.operit.core.chat

enum class MiraTurnCapability {
    GENERAL,
    ATTACHMENTS,
    DEVICE_ASSIST,
}

object MiraTurnCapabilityResolver {
    private val instructionalQuestion =
        Regex(
            "(?:怎么|如何|怎样|教程|步骤|how\\s+(?:do\\s+i|can\\s+i|to)|what(?:'s|\\s+is)\\s+the\\s+way)",
            RegexOption.IGNORE_CASE,
        )

    private val directUiAction =
        Regex(
            "(?:点击|点按|点一下|按下|长按|滑动|上滑|下滑|左滑|右滑|返回键|回到桌面|返回桌面|截个图|截屏|截图一下|打开通知栏|拉下通知栏)",
        )

    private val appAction =
        Regex(
            "(?:打开|启动|关闭|退出|切换到|进入|安装|卸载).{0,16}(?:应用|app|软件|微信|qq|支付宝|抖音|汽水音乐|浏览器|相机|设置|音乐|地图|日历|文件|相册)",
            RegexOption.IGNORE_CASE,
        )

    private val appActionReversed =
        Regex(
            "(?:应用|app|软件|微信|qq|支付宝|抖音|汽水音乐|浏览器|相机|设置|音乐|地图|日历|文件|相册).{0,10}(?:打开|启动|关闭|退出|切换|安装|卸载)",
            RegexOption.IGNORE_CASE,
        )

    private val settingAction =
        Regex(
            "(?:打开|开启|关闭|关掉|设置|调高|调低|调大|调小).{0,12}(?:蓝牙|wi-?fi|无线网络|网络|音量|亮度|飞行模式|手电筒|定位|热点)",
            RegexOption.IGNORE_CASE,
        )

    private val settingActionReversed =
        Regex(
            "(?:蓝牙|wi-?fi|无线网络|网络|音量|亮度|飞行模式|手电筒|定位|热点).{0,10}(?:打开|开启|关闭|关掉|设置|调高|调低|调大|调小)",
            RegexOption.IGNORE_CASE,
        )

    private val mediaAction =
        Regex("(?:播放|暂停|继续播放|切歌|上一首|下一首).{0,12}(?:歌|音乐|视频|播客|音频)")

    private val englishDeviceAction =
        Regex(
            "\\b(?:open|launch|close|switch to|tap|click|long press|swipe|go back|take a screenshot|turn on|turn off|increase|decrease)\\b.{0,24}\\b(?:app|screen|button|phone|settings|notification|bluetooth|wi-?fi|volume|brightness|camera|browser|music)\\b",
            RegexOption.IGNORE_CASE,
        )

    fun resolve(text: String, hasAttachments: Boolean): MiraTurnCapability {
        val normalized = text.trim()
        if (requiresDeviceAssist(normalized)) return MiraTurnCapability.DEVICE_ASSIST
        if (hasAttachments) return MiraTurnCapability.ATTACHMENTS
        return MiraTurnCapability.GENERAL
    }

    fun requiresDeviceAssist(text: String): Boolean {
        if (text.isBlank()) return false
        if (instructionalQuestion.containsMatchIn(text)) return false
        return directUiAction.containsMatchIn(text) ||
            appAction.containsMatchIn(text) ||
            appActionReversed.containsMatchIn(text) ||
            settingAction.containsMatchIn(text) ||
            settingActionReversed.containsMatchIn(text) ||
            mediaAction.containsMatchIn(text) ||
            englishDeviceAction.containsMatchIn(text)
    }
}
