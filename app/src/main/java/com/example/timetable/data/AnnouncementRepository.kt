package com.example.timetable.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一条通知公告。
 *
 * @param title 公告标题
 * @param date 发布日期（形如 `2026-09-18`，抓不到时为空串）
 * @param url 详情页绝对地址
 */
data class Announcement(
    val title: String,
    val date: String,
    val url: String,
)

/**
 * 校园通知公告抓取。
 *
 * 数据源是西安建筑科技大学教务处的通知列表页。这些页面是服务端直出的静态
 * HTML（非 SPA），结构稳定、无需登录，所以**不引入 OkHttp / Jsoup 之类的依赖**，
 * 直接用 JDK 自带的 [HttpURLConnection] + 正则解析即可 —— 少一个依赖就少一份
 * 体积与版本冲突风险。
 *
 * 列表项的 HTML 结构（已实测确认）：
 * ```
 * <li id="line_u13_0">
 *   <a href="../xxwz_nry.jsp?...&wbnewsid=36814" target="_blank" title="标题">
 *     <h2>标题</h2>
 *     <span>2026-09-18</span>
 *   </a>
 * </li>
 * ```
 * 因此按 `<li>` 切块、再从每块里取 `href` / `<h2>` / `<span>` 是最稳的做法。
 */
object AnnouncementRepository {

    /** 教务处「学生相关」通知公告列表 */
    const val URL_STUDENT_NOTICE = "https://jwc.xauat.edu.cn/tzgg/xsxg.htm"

    /** 站点根，用于把 `../xxx.jsp` 这种相对链接补全 */
    private const val SITE_ROOT = "https://jwc.xauat.edu.cn/"

    /**
     * 抓取并解析某个通知列表页。
     *
     * @param pageUrl 列表页地址，默认用 [URL_STUDENT_NOTICE]
     * @return 公告列表；网络或解析失败时抛出异常，由调用方决定如何提示
     */
    suspend fun fetch(pageUrl: String = URL_STUDENT_NOTICE): List<Announcement> =
        withContext(Dispatchers.IO) {
            val html = httpGet(pageUrl)
            parseList(html, pageUrl)
        }

    // ---------------------------------------------------------------- 网络

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            // 有些高校站点会拦截空 UA，带上一个常规浏览器 UA 更稳
            setRequestProp()
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.setRequestProp() {
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
        )
        setRequestProperty("Accept", "text/html,application/xhtml+xml")
        setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
    }

    // ---------------------------------------------------------------- 解析

    // 按 <li> 切块。列表项在页面里带 id="line_u13_N"，但为了兼容其它栏目
    // （不同栏目的 id 前缀可能不同），这里只认 <li> 本身。
    private val liRegex = Regex("""<li\b[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)

    // 取 href：只关心指向详情页的链接
    private val hrefRegex = Regex("""href\s*=\s*["']([^"']+xxwz_nry[^"']*)["']""", RegexOption.IGNORE_CASE)

    // 标题优先取 <h2>；取不到再退到 a 标签的 title 属性
    private val h2Regex = Regex("""<h2\b[^>]*>(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL)
    private val titleAttrRegex = Regex("""title\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    // 日期：形如 2026-09-18 / 2026/9/18
    private val dateRegex = Regex("""(\d{4}[-/]\d{1,2}[-/]\d{1,2})""")

    /**
     * 从列表页 HTML 解析公告。
     *
     * 纯函数，便于单测；不依赖任何 Android API。
     *
     * @param pageUrl 该 HTML 所属的页面地址，用于把相对链接补成绝对地址
     */
    fun parseList(html: String, pageUrl: String = URL_STUDENT_NOTICE): List<Announcement> =
        liRegex.findAll(html).mapNotNull { match ->
            val block = match.groupValues[1]

            val href = hrefRegex.find(block)?.groupValues?.get(1) ?: return@mapNotNull null

            // 标题：<h2> 内容 → a.title 属性
            val title = (
                h2Regex.find(block)?.groupValues?.get(1)
                    ?: titleAttrRegex.find(block)?.groupValues?.get(1)
                )
                ?.let { unescape(cleanTags(it)) }
                ?.trim()
                .orEmpty()
            if (title.isEmpty()) return@mapNotNull null

            val date = dateRegex.find(block)?.groupValues?.get(1)?.replace('/', '-').orEmpty()

            Announcement(
                title = title,
                date = date,
                url = absolutize(href, pageUrl),
            )
        }.distinctBy { it.url }.toList()

    /** 去掉所有 HTML 标签，并把连续空白压成一个空格 */
    private fun cleanTags(raw: String): String =
        raw.replace(Regex("""<[^>]+>"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** 还原最常见的 HTML 实体 */
    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&ldquo;", "\u201C")
        .replace("&rdquo;", "\u201D")

    /** 把相对链接补成绝对地址 */
    private fun absolutize(href: String, pageUrl: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> SITE_ROOT.trimEnd('/') + href
        else -> {
            // 形如 ../xxwz_nry.jsp?... 或 xxwz_nry.jsp?...
            val base = pageUrl.substringBeforeLast('/') + "/"
            java.net.URL(java.net.URL(base), href).toString()
        }
    }
}
