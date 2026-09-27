package com.weenas.castbay.dlna

/**
 * What a DLNA sender is casting, from its DIDL-Lite metadata: music apps (NetEase Cloud Music,
 * QQ Music, ...) send the song's title, artist, album and cover, which the music screen shows.
 */
data class DlnaMedia(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** An http(s) URL of the cover image. */
    val albumArtUrl: String? = null,
    /** Sound only (a song), shown on the music screen rather than as video. */
    val isAudio: Boolean = false,
    /** The casting app, from [DlnaSender.fromUserAgent]; empty when unknown. */
    val sender: String = ""
) {
    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "ape")

        /**
         * Parses DIDL-Lite loosely (senders' metadata is often not well-formed) and tells audio
         * from video by the item class, the resource's MIME type, or the URL's extension.
         */
        fun parse(metadata: String?, url: String): DlnaMedia {
            val didl = metadata.orEmpty()
            val itemClass = tag(didl, "upnp:class").orEmpty()
            val mime = Regex("""protocolInfo="[^:"]*:[^:"]*:([^:"]+)""").find(didl)?.groupValues?.get(1).orEmpty()
            val extension = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
            val isAudio = when {
                "audioItem" in itemClass -> true
                "videoItem" in itemClass || mime.startsWith("video/") -> false
                mime.startsWith("audio/") -> true
                else -> extension in AUDIO_EXTENSIONS
            }
            return DlnaMedia(
                title = tag(didl, "dc:title"),
                artist = tag(didl, "upnp:artist") ?: tag(didl, "dc:creator"),
                album = tag(didl, "upnp:album"),
                albumArtUrl = tag(didl, "upnp:albumArtURI")?.takeIf { it.startsWith("http") },
                isAudio = isAudio
            )
        }

        /** The text of the first <[name]> element, entities decoded, or null if absent or blank. */
        private fun tag(didl: String, name: String): String? {
            val match = Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(didl) ?: return null
            return match.groupValues[1]
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&")
                .trim().takeIf { it.isNotEmpty() }
        }
    }
}
