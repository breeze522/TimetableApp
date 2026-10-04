package com.example.timetable.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.timetable.data.Course
import com.example.timetable.data.EamsImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 教务系统课表导入界面。
 *
 * 流程：
 * 1. 用 WebView 打开教务系统，用户正常登录（页面与浏览器中完全一致）；
 * 2. 登录后点「开始抓取」，通过 evaluateJavascript 注入 [EamsImporter.FETCH_SCRIPT]；
 * 3. 脚本复用登录态请求课表接口，把结果通过 JS 桥 [ImportBridge] 回传；
 * 4. 解析成课程列表，交回上层导入。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onFinish: (courses: List<Course>, currentWeek: Int?) -> Unit,
    onCancel: () -> Unit,
    autoMode: Boolean = false,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember {
        mutableStateOf(
            if (autoMode) "正在抓取课表…" else "请先登录教务系统，登录成功后会自动抓取课表",
        )
    }
    var fetching by remember { mutableStateOf(false) }
    // 是否已经自动抓取过（避免每次页面跳转都重复抓）
    var autoFetched by remember { mutableStateOf(false) }
    // 抓取失败时的详细诊断信息（可滚动展示）
    var detail by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 桥回传的结果，用 MutableStateFlow 保证跨线程安全写入
    val resultFlow = remember { MutableStateFlow<String?>(null) }

    // 处理 JS 桥回传：等待结果或超时
    LaunchedEffect(fetching) {
        if (!fetching) return@LaunchedEffect
        // 每 250ms 检查一次桥结果
        val raw = withTimeoutOrNull(25_000) {
            resultFlow.filterNotNull().first()
        }
        if (raw == null) {
            status = "抓取超时。请确认已登录，或点「重登」后重试。"
            fetching = false
            return@LaunchedEffect
        }
        val result = EamsImporter.parse(raw)
        resultFlow.value = null
        if (result.success) {
            onFinish(result.courses, result.currentWeek)
        } else {
            // 把原始数据落盘，便于后续分析真实结构
            runCatching {
                val f = java.io.File(
                    context.filesDir,
                    "eams_last_response.txt",
                )
                f.writeText(raw)
                android.util.Log.d("EamsDiag", "saved to ${f.absolutePath}, len=${raw.length}")
            }
            status = "抓取未成功，详见下方诊断信息"
            detail = result.error
            fetching = false
            autoFetched = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "导入课表",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !fetching) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 状态条
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (fetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val wv = webView
                        if (wv == null) {
                            status = "页面尚未就绪，请稍候"
                            return@Button
                        }
                        resultFlow.value = null
                        detail = null
                        fetching = true
                        status = "正在抓取课表…"
                        wv.evaluateJavascript(EamsImporter.FETCH_SCRIPT, null)
                    },
                    enabled = !fetching,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (fetching) "抓取中…" else "重新抓取")
                }
                OutlinedButton(
                    onClick = {
                        val wv = webView
                        if (wv == null) {
                            status = "页面尚未就绪，请稍候"
                            return@OutlinedButton
                        }
                        // 诊断：把页面关键状态显示在界面上（不只看日志）
                        wv.evaluateJavascript(EamsImporter.DIAGNOSE_SCRIPT) { value ->
                            val text = value
                                ?.removePrefix("\"")
                                ?.removeSuffix("\"")
                                ?.replace("\\u003d", "=")
                                ?.replace("\\\"", "\"")
                                ?.replace("\\\\", "\\")
                                ?: "null"
                            android.util.Log.d("EamsDiag", text)
                            // 把诊断结果放到状态条里（截断显示）
                            status = "诊断：" + text.take(300)
                        }
                    },
                    enabled = !fetching,
                ) {
                    Text("诊断")
                }
                OutlinedButton(
                    onClick = {
                        status = "已重置，请重新登录"
                        resultFlow.value = null
                        detail = null
                        autoFetched = false
                        fetching = false
                        webView?.loadUrl(EamsImporter.ENTRY_URL)
                    },
                    enabled = !fetching,
                ) {
                    Text("重登")
                }
            }

            // 诊断详情（可滚动）
            detail?.let { text ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // WebView 容器
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // ---- 基础能力 ----
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = false
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                            // 关键 1：使用真实浏览器 UA。教务系统常按 UA 判断并返回不同页面，
                            // WebView 默认 UA（含 "; wv"）可能导致返回降级/空白页面。
                            val realUa = settings.userAgentString
                                ?.replace("; wv", "")
                                ?.replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                            if (!realUa.isNullOrBlank()) settings.userAgentString = realUa

                            // 关键 2：禁用强制深色。App 是深色主题，Android 的 forceDark 会把
                            // 教务系统白底页面反色，造成文字与背景颠倒、看起来"花"或空白。
                            // 这些 API 属隐藏/系统接口，用反射安全调用。
                            runCatching {
                                settings.javaClass
                                    .getMethod("setForceDarkAllowed", Boolean::class.javaPrimitiveType)
                                    .invoke(settings, false)
                            }
                            runCatching {
                                // FORCE_DARK_OFF = 0
                                settings.javaClass
                                    .getMethod("setForceDark", Int::class.javaPrimitiveType)
                                    .invoke(settings, 0)
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            // ---- 诊断：把 Console 日志打到 Logcat，便于排查空白页 ----
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(
                                    msg: android.webkit.ConsoleMessage?,
                                ): Boolean {
                                    android.util.Log.d(
                                        "EamsWeb",
                                        "${msg?.message()} @${msg?.sourceId()}:${msg?.lineNumber()}",
                                    )
                                    return true
                                }
                            }

                            addJavascriptInterface(
                                ImportBridge { raw -> resultFlow.value = raw },
                                "eamsBridge",
                            )

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                ): Boolean {
                                    // 所有跳转都留在 WebView 内，不交给系统浏览器
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    android.util.Log.d("EamsWeb", "onPageFinished: $url")
                                    // 自动识别登录成功：从登录页跳走后，若尚未抓取过，自动抓一次
                                    val u = url ?: return
                                    val isLoginPage = u.contains("/login")
                                    if (!isLoginPage && !autoFetched && !fetching) {
                                        autoFetched = true
                                        status = "检测到已登录，正在自动抓取课表…"
                                        fetching = true
                                        resultFlow.value = null
                                        view?.evaluateJavascript(EamsImporter.FETCH_SCRIPT, null)
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?,
                                ) {
                                    super.onReceivedError(view, request, error)
                                    android.util.Log.e(
                                        "EamsWeb",
                                        "onReceivedError: ${request?.url} -> ${error?.description}",
                                    )
                                }
                            }
                            loadUrl(EamsImporter.ENTRY_URL)
                            webView = this
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
        }
    }
}

/**
 * 内部 JS 桥。
 *
 * 注意：@JavascriptInterface 方法运行在 WebView 的 JS 线程，
 * 这里只做一件事——把原始字符串交给上层（写入 Compose state），
 * 不在桥里做任何解析或 UI 操作。
 */
private class ImportBridge(private val deliver: (String) -> Unit) {
    @JavascriptInterface
    fun onResult(raw: String) {
        deliver(raw)
    }
}
