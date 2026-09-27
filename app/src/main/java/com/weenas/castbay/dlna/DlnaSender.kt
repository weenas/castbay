package com.weenas.castbay.dlna

/**
 * Which app is casting over DLNA. DLNA doesn't say which phone is casting, but apps' requests
 * name the app in their User-Agent (iQiyi: "UPnP/1.0 IQIYIDLNA/iqiyidlna/NewDLNA/1.0").
 */
object DlnaSender {
    const val IQIYI = "iqiyi"
    const val BILIBILI = "bilibili"
    const val NETEASE_MUSIC = "netease_music"
    const val QQ_MUSIC = "qq_music"
    const val TENCENT_VIDEO = "tencent_video"
    const val YOUKU = "youku"
    const val MANGO_TV = "mango_tv"
    const val KUGOU = "kugou"
    const val KUWO = "kuwo"

    /** User-Agent fragments (lowercase) to the app; first match wins. */
    private val PATTERNS = listOf(
        "iqiyi" to IQIYI,
        "bili" to BILIBILI,
        "cloudmusic" to NETEASE_MUSIC,
        "netease" to NETEASE_MUSIC,
        "qqmusic" to QQ_MUSIC,
        "qqlive" to TENCENT_VIDEO,
        "tencentvideo" to TENCENT_VIDEO,
        "youku" to YOUKU,
        "mgtv" to MANGO_TV,
        "imgotv" to MANGO_TV,
        "kugou" to KUGOU,
        "kuwo" to KUWO,
    )

    /** The app's id, or "" when the User-Agent doesn't name a known one. */
    fun fromUserAgent(userAgent: String?): String {
        val agent = userAgent?.lowercase() ?: return ""
        return PATTERNS.firstOrNull { (fragment, _) -> fragment in agent }?.second.orEmpty()
    }
}
