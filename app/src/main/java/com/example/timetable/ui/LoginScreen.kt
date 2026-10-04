package com.example.timetable.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.timetable.data.EamsImporter
import com.example.timetable.data.SavedCredentials
import com.example.timetable.data.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「登录」页。
 *
 * 采用**统一身份认证（CAS）**：用户在原生表单里输入学号 / 密码，
 * 后台用一个 1×1 隐藏 WebView 走完整登录流程：
 *
 *  1. 打开教务系统入口 → 未登录会被 302 到 `authserver.xauat.edu.cn` 的 CAS 页；
 *  2. 在 CAS 页注入 [EamsImporter.buildLoginScript]：取 execution / salt →
 *     用页面自带的 startLogin() 完成 AES 加密并提交；
 *  3. 提交成功后 CAS 带 ticket 跳回教务系统，在教务系统页注入
 *     [EamsImporter.STUDENT_INFO_SCRIPT] 拉取姓名 / 学号 / 学院 / 专业 / 班级；
 *  4. 拿到信息后回调 [onLoginSuccess] 持久化，页面切换为「已登录」态。
 *
 * **自动登录**分两级，进入页面时按顺序尝试：
 *  - 一级「会话复用」：教务系统 cookie 是磁盘持久的。先静默访问教务系统入口，
 *    若没被弹回 CAS 登录页，说明上次会话仍有效，直接拉个人信息，用户完全无感；
 *  - 二级「记住的凭据」：会话已失效时，若本地记住了账号密码
 *    （[SavedCredentials]，密码经 Android Keystore AES 加密），则自动填表登录。
 *
 * 会话是否有效由 [EamsImporter.SESSION_CHECK_SCRIPT] 探测脚本判定。
 */
@Composable
fun LoginScreen(
    profile: UserProfile?,
    saved: SavedCredentials?,
    onLoginSuccess: (UserProfile) -> Unit,
    onSaveCredentials: (String, String, Boolean) -> Unit,
    onSetAutoLogin: (Boolean) -> Unit,
    onForgetCredentials: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profile != null && !profile.isEmpty) {
        LoggedInView(
            profile = profile,
            savedUsername = saved?.username.orEmpty(),
            onForget = onForgetCredentials,
            onLogout = onLogout,
            modifier = modifier,
        )
    } else {
        LoginForm(
            saved = saved,
            onLoginSuccess = onLoginSuccess,
            onSaveCredentials = onSaveCredentials,
            onSetAutoLogin = onSetAutoLogin,
            modifier = modifier,
        )
    }
}

// ---------------------------------------------------------------------------
// 已登录态
// ---------------------------------------------------------------------------

@Composable
private fun LoggedInView(
    profile: UserProfile,
    savedUsername: String,
    onForget: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmForget by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profile.name.firstOrNull()?.toString() ?: "?",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = profile.name.ifBlank { "未知用户" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (profile.studentNo.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "学号 ${profile.studentNo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (profile.loginTime > 0) {
            Spacer(Modifier.height(6.dp))
            val fmt = remember { SimpleDateFormat("M月d日 HH:mm", Locale.CHINA) }
            Text(
                text = "登录于 ${fmt.format(Date(profile.loginTime))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                InfoRow(label = "姓名", value = profile.name)
                InfoRow(label = "学号", value = profile.studentNo)
                InfoRow(label = "学院", value = profile.departmentName)
                InfoRow(label = "专业", value = profile.majorName)
                InfoRow(label = "班级", value = profile.className)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "已通过统一身份认证登录西安建筑科技大学教务系统",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onLogout) {
            Text("退出登录", color = MaterialTheme.colorScheme.error)
        }
        if (savedUsername.isNotBlank()) {
            TextButton(onClick = { confirmForget = true }) {
                Text(
                    "忘掉已保存的账号（$savedUsername）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("清除已保存的账号密码？") },
            text = { Text("清除后，下次打开需要重新手动输入账号和密码。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForget()
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------------
// 未登录态：登录表单
// ---------------------------------------------------------------------------

/**
 * 一次登录任务。
 *
 * @property username 账号
 * @property password 密码；空串表示「只做会话复用探测」
 * @property attempt  递增序号，用于重建隐藏 WebView
 * @property autoInitiated 是否由自动登录发起（用于决定是否写回错误提示）
 */
private data class LoginTask(
    val username: String,
    val password: String,
    val attempt: Int,
    val autoInitiated: Boolean,
)

@Composable
private fun LoginForm(
    saved: SavedCredentials?,
    onLoginSuccess: (UserProfile) -> Unit,
    onSaveCredentials: (String, String, Boolean) -> Unit,
    onSetAutoLogin: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 记住账号密码
    var rememberMe by remember { mutableStateOf(true) }
    var autoLogin by remember { mutableStateOf(true) }

    var loggingIn by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var task by remember { mutableStateOf<LoginTask?>(null) }

    // 是否已经接过 DataStore 的第一份数据（区分「还没读到」和「确实没存」）
    var prefsLoaded by remember { mutableStateOf(false) }
    // 自动登录只跑一次
    var autoStarted by remember { mutableStateOf(false) }

    // ---- 第一段：DataStore 数据到达后，预填账号密码 ----
    LaunchedEffect(saved) {
        if (saved == null) {
            // 给 DataStore 一点时间；超时后也标记为已加载，避免一直等待
            delay(800)
            prefsLoaded = true
            return@LaunchedEffect
        }
        prefsLoaded = true
        if (username.isBlank()) username = saved.username
        if (password.isBlank()) password = saved.password
        rememberMe = true
        autoLogin = saved.autoLogin
    }

    // ---- 第二段：prefs 就绪 + 有凭据 + 开了自动登录 → 自动跑一次 ----
    LaunchedEffect(prefsLoaded, saved) {
        if (!prefsLoaded || autoStarted) return@LaunchedEffect
        val s = saved
        if (s == null) {
            android.util.Log.i("EamsLogin", "无已保存凭据，等待用户手动输入")
            return@LaunchedEffect
        }
        // 未开自动登录：仍然尝试「会话复用」（上次登录态可能还在），
        // 但失败时不打错误提示，安静地交回表单。
        autoStarted = true
        android.util.Log.i(
            "EamsLogin",
            "自动登录启动 user=${s.username} autoLogin=${s.autoLogin}",
        )
        loggingIn = true
        statusText = "正在恢复登录状态…"
        task = LoginTask(
            username = s.username,
            password = if (s.autoLogin) s.password else "",
            attempt = ++attempt,
            autoInitiated = true,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "统一身份认证",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "使用教务系统账号登录，自动获取成绩与个人信息",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorText = null
            },
            label = { Text("学号 / 账号") },
            singleLine = true,
            enabled = !loggingIn,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorText = null
            },
            label = { Text("密码") },
            singleLine = true,
            enabled = !loggingIn,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = {
                    rememberMe = it
                    if (!it) autoLogin = false
                },
            )
            Text(
                text = "记住账号密码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable {
                    rememberMe = !rememberMe
                    if (!rememberMe) autoLogin = false
                },
            )
            Spacer(Modifier.width(14.dp))
            Checkbox(
                checked = autoLogin,
                onCheckedChange = {
                    autoLogin = it
                    if (it) rememberMe = true
                    if (saved != null) onSetAutoLogin(it)
                },
                enabled = rememberMe,
            )
            Text(
                text = "下次自动登录",
                style = MaterialTheme.typography.bodyMedium,
                color = if (rememberMe) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        AnimatedVisibility(visible = errorText != null) {
            Text(
                text = errorText ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorText = "请输入账号和密码"
                    return@Button
                }
                errorText = null
                statusText = "正在打开统一身份认证…"
                loggingIn = true
                task = LoginTask(
                    username = username.trim(),
                    password = password,
                    attempt = ++attempt,
                    autoInitiated = false,
                )
            },
            enabled = !loggingIn,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .height(50.dp),
        ) {
            if (loggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text(statusText.ifBlank { "登录中…" })
            } else {
                Text("登录", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "账号密码仅加密保存在本机，不会上传",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
    }

    task?.let { t ->
        HiddenLoginWebView(
            task = t,
            onStatus = { statusText = it },
            onAutoSessionMiss = {
                // 会话复用失败：自动登录流程直接结束，把界面交回给用户重输密码
                autoStarted = false
                loggingIn = false
                task = null
                if (!t.autoInitiated) errorText = "上次的登录已过期，请重新输入密码"
            },
            onError = { msg ->
                loggingIn = false
                task = null
                if (!t.autoInitiated) errorText = msg
            },
            onSuccess = { p ->
                loggingIn = false
                task = null
                // 手动登录 → 按勾选情况落盘
                if (!t.autoInitiated && rememberMe && password.isNotBlank()) {
                    android.util.Log.i(
                        "EamsLogin",
                        "保存凭据 user=$username autoLogin=$autoLogin",
                    )
                    onSaveCredentials(username.trim(), password, autoLogin)
                } else if (t.autoInitiated && rememberMe && password.isNotBlank()) {
                    // 自动登录成功也刷新一次（例如用户改过开关）
                    android.util.Log.i("EamsLogin", "自动登录成功，刷新凭据开关")
                    onSaveCredentials(username.trim(), password, autoLogin)
                }
                onLoginSuccess(p)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 隐藏 WebView：执行完整登录流程
// ---------------------------------------------------------------------------

/**
 * 登录流程的 JS 桥。
 *
 * 回传约定见 [EamsImporter.RESULT_PREFIX]。
 */
private class LoginBridge {
    val latest = MutableStateFlow<String?>(null)

    @JavascriptInterface
    fun onResult(value: String?) {
        if (!value.isNullOrBlank()) latest.value = value
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HiddenLoginWebView(
    task: LoginTask,
    onStatus: (String) -> Unit,
    onAutoSessionMiss: () -> Unit,
    onError: (String) -> Unit,
    onSuccess: (UserProfile) -> Unit,
) {
    val attempt = task.attempt
    val bridge = remember(attempt) { LoginBridge() }
    var finished by remember(attempt) { mutableStateOf(false) }
    var submitCount by remember(attempt) { mutableIntStateOf(0) }
    var infoCount by remember(attempt) { mutableIntStateOf(0) }
    var probed by remember(attempt) { mutableStateOf(false) }
    // 会话探测发现有效 → 需要在同一页继续拉信息
    var sessionAlive by remember(attempt) { mutableStateOf(false) }
    // 保存 WebView 引用，供 collect 里注入脚本
    var webRef by remember(attempt) { mutableStateOf<WebView?>(null) }
    // CAS 页已提交的「失败复核」时间戳（毫秒）。
    // 实测坑：CAS 页提交后往往还会再触发一次 onPageFinished（同 URL），
    // 不能据此立刻判定密码错误 —— 要留出跳转时间，超时仍在 CAS 才算失败。
    var casFailAt by remember(attempt) { mutableStateOf(0L) }

    /** 注入学生信息脚本（带一次延迟，等页面脚本/cookie 就绪） */
    fun fetchStudentInfo(view: WebView?) {
        android.util.Log.i("EamsLogin", "注入学生信息脚本")
        view?.postDelayed({
            if (!finished) view.evaluateJavascript(EamsImporter.STUDENT_INFO_SCRIPT, null)
        }, 1200)
    }

    Box(modifier = Modifier.size(1.dp)) {
        AndroidView(
            modifier = Modifier.size(1.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    webRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = settings.userAgentString
                        ?.replace("; wv", "")
                        ?.replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    runCatching {
                        settings.javaClass
                            .getMethod("setForceDarkAllowed", Boolean::class.javaPrimitiveType)
                            .invoke(settings, false)
                    }
                    runCatching {
                        settings.javaClass
                            .getMethod("setForceDark", Int::class.javaPrimitiveType)
                            .invoke(settings, 0)
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(bridge, "eamsBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val u = url ?: return
                            if (finished) return

                            android.util.Log.i(
                                "EamsLogin",
                                "onPageFinished url=$u submit=$submitCount info=$infoCount " +
                                    "probed=$probed alive=$sessionAlive",
                            )

                            // 情况 A：会话已确认为有效 → 直接拉学生信息
                            if (sessionAlive && u.contains("swjw.xauat.edu.cn")) {
                                if (infoCount == 0) {
                                    infoCount += 1
                                    fetchStudentInfo(view)
                                }
                                return
                            }

                            // 情况 B：真正的 CAS 账号密码页
                            // 注意：教务系统自己的 /student/login 是个 Vue 空壳页，
                            // 上面**没有**账号密码表单，不能在这里注入登录脚本 ——
                            // 遇到它就跳去 CAS（SSO_LOGIN_URL 会 302 过去）。
                            if (u.contains("authserver")) {
                                probed = true
                                // 如果已经历过一次提交，且再次回到 CAS：
                                // 不要立刻判失败 —— 页面可能只是重渲染（同 URL 二次
                                // onPageFinished）。标记时间戳，交给延迟复核任务判断。
                                if (submitCount > 0) {
                                    if (casFailAt == 0L) {
                                        casFailAt = System.currentTimeMillis()
                                        android.util.Log.w(
                                            "EamsLogin",
                                            "再次回到 CAS 页，等待复核（可能是重渲染）",
                                        )
                                    }
                                    return
                                }
                                if (task.password.isBlank()) {
                                    android.util.Log.i("EamsLogin", "会话已失效，无密码可用")
                                    finished = true
                                    onAutoSessionMiss()
                                    return
                                }
                                submitCount += 1
                                android.util.Log.i("EamsLogin", "注入登录脚本 url=$u")
                                view?.evaluateJavascript(
                                    EamsImporter.buildLoginScript(
                                        task.username, task.password,
                                    ),
                                    null,
                                )
                                return
                            }

                            // 情况 C：教务系统的登入壳页 → 跳去统一身份认证
                            if (u.contains("/student/login") || u.contains("/sso/login")) {
                                probed = true
                                if (task.password.isBlank()) {
                                    finished = true
                                    onAutoSessionMiss()
                                    return
                                }
                                android.util.Log.i("EamsLogin", "遇到登入壳页 → 跳转 CAS")
                                onStatus("正在打开统一身份认证…")
                                view?.loadUrl(EamsImporter.SSO_LOGIN_URL)
                                return
                            }

                            // 情况 D：教务系统业务页（首次进入 / 登录后跳回）
                            if (u.contains("swjw.xauat.edu.cn")) {
                                if (!probed) {
                                    probed = true
                                    onStatus("正在检查登录状态…")
                                    android.util.Log.i("EamsLogin", "注入会话探测脚本")
                                    view?.evaluateJavascript(
                                        EamsImporter.SESSION_CHECK_SCRIPT, null,
                                    )
                                } else if (infoCount == 0 && submitCount > 0) {
                                    // 登录提交后跳回来 → 拉信息
                                    infoCount += 1
                                    fetchStudentInfo(view)
                                }
                            }
                        }
                    }

                    // 一律先进教务系统入口：cookie 持久，可能直接就是登录态
                    loadUrl(EamsImporter.ENTRY_URL)
                }
            },
            onRelease = { it.destroy() },
        )
    }

    LaunchedEffect(attempt) {
        bridge.latest.collect { raw ->
            if (raw == null) return@collect
            if (finished) return@collect
            android.util.Log.i("EamsLogin", "bridge: ${raw.take(2000)}")

            // 会话探测结果
            if (raw.contains("\"loggedIn\"")) {
                val alive = EamsImporter.parseSessionCheck(raw)
                android.util.Log.i("EamsLogin", "会话探测结果 loggedIn=$alive")
                if (alive) {
                    sessionAlive = true
                    onStatus("正在恢复上次的登录…")
                    // 已经在这个教务系统页面上，直接拉信息即可
                    if (infoCount == 0) {
                        infoCount += 1
                        fetchStudentInfo(webRef)
                    }
                } else if (task.password.isBlank()) {
                    // 会话失效且没有密码 → 交回表单
                    finished = true
                    onAutoSessionMiss()
                } else {
                    // 会话失效但记住了密码 → 去 CAS 页重新登录
                    onStatus("正在自动登录…")
                    webRef?.loadUrl(EamsImporter.SSO_LOGIN_URL)
                }
                return@collect
            }

            // 登录脚本回传「已提交」→ 等页面跳转
            if (raw.contains("\"stage\":\"submitted\"")) {
                onStatus("正在验证账号…")
                return@collect
            }

            // 学生信息结果
            val info = EamsImporter.parseStudentInfo(raw)
            if (info.success && info.profile != null && !info.profile.isEmpty) {
                finished = true
                android.util.Log.i("EamsLogin", "登录成功: ${info.profile}")
                onSuccess(info.profile)
            }
        }
    }

    // 提交后若再次回到 CAS，等待复核：给足跳转时间，仍留在 CAS 才算密码错误。
    // 注意：CAS 页提交后常会再触发一次同 URL 的 onPageFinished（重渲染），
    // 不能据此立刻判失败；而且认证本身可能耗时较久，所以要「延迟 + 查真实 URL」双重确认。
    LaunchedEffect(attempt, casFailAt) {
        if (casFailAt == 0L) return@LaunchedEffect
        delay(12_000)
        if (finished) return@LaunchedEffect
        val here = webRef?.url ?: ""
        if (here.contains("authserver")) {
            android.util.Log.w("EamsLogin", "复核超时仍在 CAS 页($here)，判定账号或密码错误")
            finished = true
            onError("账号或密码不正确，请重新输入")
        } else {
            // 已经离开 CAS（说明认证成功，正在跳回教务系统）→ 交给页面回调继续
            android.util.Log.i("EamsLogin", "复核时已离开 CAS($here)，继续等待跳转")
        }
    }

    LaunchedEffect(attempt) {
        delay(45_000)
        if (!finished) onError("登录超时，请检查网络后重试")
    }
}
