package com.socialmediascraper.scraper

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TwitterParserTest {

    @Test
    fun decodesJsStringEscapes() {
        val src = """x"a\"b\\c\ndç\x41\u{1F600}"rest"""
        val (decoded, end) = TwitterParser.decodeJsString(src, 2)!!
        assertEquals("a\"b\\c\ndçA😀", decoded)
        assertEquals("rest", src.substring(end))
    }

    @Test
    fun parsesEmbeddedGraphQlTimeline() {
        val tweet = """{"__typename":"Tweet","rest_id":"2111336450058817751",""" +
            """"core":{"user_results":{"result":{"__typename":"User","avatar":{"image_url":"https://pbs.twimg.com/a_normal.jpg"},"core":{"name":"Örnek","screen_name":"Ornek"}}}},""" +
            """"counts":{"favorite_count":5,"retweet_count":2,"reply_count":1},""" +
            """"details":{"created_at_ms":1792216811000,"full_text":"Merhaba &amp; dünya https://t.co/abc https://t.co/media"},""" +
            """"url_entities":[{"url":"https://t.co/abc","expanded_url":"https://example.com/x","display_url":"example.com/x"}],""" +
            """"media_entities2":[{"type":"photo","media_url_https":"https://pbs.twimg.com/media/X.jpg","original_info":{"width":1200,"height":900}}],""" +
            """"views":{"count":"42"}}"""
        val json = """{"data":{"user_result_by_screen_name":{"result":{"__typename":"User","profile_user_originals_timeline":{"timeline":{"instructions":[""" +
            """{"__typename":"TimelineAddEntries","entries":[{"content":{"content":{"tweet_results":{"result":$tweet}}}}]}]}}}}}}"""
        val profileJson = """{"data":{"user_result_by_screen_name":{"result":{"__typename":"User","core":{"name":"Örnek","screen_name":"Ornek"},"avatar":{"image_url":"https://pbs.twimg.com/a_normal.jpg"}}}}}"""
        val html = "<script>(\$R=>\$R[1]={kind:\"Result.Ok\",value:\"${jsEscape(profileJson)}\"},\$R[2]={kind:\"Result.Ok\",value:\"${jsEscape(json)}\"})</script>"

        val page = TwitterParser.parse(html, "Ornek")
        assertEquals("Ornek", page.profile?.handle)
        assertEquals("https://pbs.twimg.com/a_bigger.jpg", page.profile?.avatarUrl)
        assertEquals(1, page.posts.size)
        val post = page.posts.single()
        assertEquals("2111336450058817751", post.id)
        assertEquals("Merhaba & dünya https://example.com/x", post.text)
        assertEquals(1792216811000, post.createdAt)
        assertEquals(5, post.likeCount)
        assertEquals(42L, post.viewCount)
        assertEquals(1, post.media.size)
        assertEquals("https://x.com/Ornek/status/2111336450058817751", post.url)
    }

    private fun legacyTweet(id: String, handle: String, text: String, extra: String = "") =
        """{"__typename":"Tweet","rest_id":"$id","core":{"user_results":{"result":{"__typename":"User","legacy":{"screen_name":"$handle","name":"$handle"}}}},""" +
            """"legacy":{"full_text":"$text","created_at":"Wed Oct 16 10:00:00 +0000 2026","favorite_count":3$extra}}"""

    private fun entry(id: String, tweet: String) =
        """{"entryId":"$id","content":{"itemContent":{"tweet_results":{"result":$tweet}}}}"""

    @Test
    fun parsesLoggedInRepliesAndRetweets() {
        val reply = legacyTweet("3", "Ornek", "@baska evet", ""","in_reply_to_screen_name":"baska"""")
        val parent = legacyTweet("2", "baska", "soru?")
        val retweet = legacyTweet(
            "4", "Ornek", "RT @baska: soru?",
            ""","retweeted_status_result":{"result":$parent}""",
        )
        val ad = legacyTweet("5", "Reklam", "satın al")
        val json = JSONObject(
            """{"data":{"user":{"result":{"__typename":"User","timeline":{"timeline":{"instructions":[{"type":"TimelineAddEntries","entries":[""" +
                listOf(
                    """{"entryId":"profile-conversation-1","content":{"items":[""" +
                        """{"item":{"itemContent":{"tweet_results":{"result":$parent}}}},""" +
                        """{"item":{"itemContent":{"tweet_results":{"result":$reply}}}}]}}""",
                    entry("tweet-4", retweet),
                    entry("promoted-tweet-5", ad),
                ).joinToString(",") + "]}]}}}}}}",
        )

        val page = TwitterParser.parsePayloads(listOf(json), "Ornek", ownOnly = true)
        assertEquals(listOf("3", "4"), page.posts.map { it.id }.sorted())
        val r = page.posts.first { it.id == "3" }
        assertEquals("baska", r.replyTo)
        assertEquals("evet", r.text)
        val rt = page.posts.first { it.id == "4" }
        assertEquals("Ornek", rt.retweetedBy)
        assertEquals("baska", rt.authorHandle)
        assertEquals("soru?", rt.text)
        assertEquals("https://x.com/baska/status/2", rt.url)
    }

    @Test
    fun detectsLayoutChange() {
        val noTimeline = JSONObject("""{"data":{"user_result_by_screen_name":{"result":{"__typename":"User","core":{"name":"A","screen_name":"A"}}}}}""")
        val page = TwitterParser.parsePayloads(listOf(noTimeline), "A", ownOnly = false)
        assertEquals(ScrapeErrors.LAYOUT_CHANGED, ScrapeErrors.check(page, "A")?.message)

        val unknownTweets = JSONObject(
            """{"data":{"user":{"result":{"timeline":{"instructions":[{"entries":[{"entryId":"tweet-1","content":{"yeniAlan":{}}}]}]}}}}}""",
        )
        val page2 = TwitterParser.parsePayloads(listOf(unknownTweets), "A", ownOnly = false)
        assertEquals(ScrapeErrors.LAYOUT_CHANGED, ScrapeErrors.check(page2, "A")?.message)
        assertTrue(!TwitterParser.hasEmbeddedData("<html>bambaşka bir sayfa</html>"))
    }

    @Test
    fun emptyPageHasNoData() {
        val page = TwitterParser.parse("<html>kind:\"Result.Ok\",value:\"{\\\"data\\\":{}}\"</html>", "yok")
        assertNull(page.profile)
        assertTrue(page.posts.isEmpty())
    }

    @Test
    fun normalizesHandles() {
        assertEquals("TwitterHesabi", TwitterScraper.normalizeHandle(" @TwitterHesabi "))
        assertEquals("abc_1", TwitterScraper.normalizeHandle("https://x.com/abc_1/status/1"))
        assertEquals("abc", TwitterScraper.normalizeHandle("twitter.com/abc"))
        assertNull(TwitterScraper.normalizeHandle("geçersiz hesap"))
        assertNull(TwitterScraper.normalizeHandle("@"))
    }

    /** `-PxHtmlDir=<klasör>` ile verilen, x.com'dan kaydedilmiş gerçek sayfaları ayrıştırır. */
    @Test
    fun parsesSavedRealPages() {
        val dir = System.getProperty("xHtmlDir").orEmpty()
        assumeTrue(dir.isNotEmpty())
        val files = File(dir).listFiles { f -> f.extension == "html" }.orEmpty()
        assumeTrue(files.isNotEmpty())
        for (file in files) {
            val page = TwitterParser.parse(file.readText(), file.nameWithoutExtension)
            println("${file.name}: profil=${page.profile} gönderi=${page.posts.size}")
            page.posts.forEach { println("  ${it.id} ${it.createdAt} pinned=${it.isPinned} media=${it.media.map { m -> m.type }} quoted=${it.quoted?.authorHandle} | ${it.text.take(70).replace('\n', ' ')}") }
            if (page.profile != null) assertTrue("${file.name} gönderi bulunamadı", page.posts.isNotEmpty())
        }
    }

    /** `-PliveAccounts=hesap1,hesap2` ile x.com'dan canlı veri çeker. */
    @Test
    fun fetchesLive() = runBlocking {
        val accounts = System.getProperty("liveAccounts").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        assumeTrue(accounts.isNotEmpty())
        val scraper = TwitterScraper()
        for (account in accounts) {
            val result = scraper.fetch(account)
            println("$account -> $result".take(400))
            assertTrue("$account: $result", result is ScrapeResult.Success)
            result as ScrapeResult.Success
            assertNotNull(result.profile)
            assertTrue(result.posts.isNotEmpty())
            result.posts.forEach { println("  ${it.id} ${it.authorHandle} ${it.text.take(60).replace('\n', ' ')}") }
        }
    }

    private fun jsEscape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
