package com.example.timetable.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 西安建筑科技大学教务系统（树维 / Supwisdom）课表抓取与解析。
 *
 * ## 抓取方式
 * 在 WebView 页面上下文内注入 JS，复用用户已登录的 Cookie 请求课表接口：
 *
 * ```
 * GET /student/for-std/course-table/get-data?bizTypeId=2&semesterId={id}
 * ```
 *
 * 关键点：必须带 `bizTypeId=2`，否则服务端返回 500。
 * 其中 `semesterId` 从课表页 `<option selected value="...">` 动态读取。
 *
 * ## 返回结构（已实测）
 * ```
 * {
 *   "lessons": [ { "course": {"nameZh": ...},
 *                  "scheduleText": {"dateTimePlaceText": {"text": "..."}},
 *                  "teacherAssignmentStr": "赵亮(010105200),..." } ],
 *   "currentWeek": 5,
 *   "weekIndices": [1..26]
 * }
 * ```
 *
 * `scheduleText.dateTimePlaceText.text` 是规整的课表文本，形如：
 * ```
 * 1~5,7~8周 周三 第三节~第四节 草堂校区 草堂16-208;
 * 5~7(单),8~13周 周二 第三节~第四节 草堂校区 草堂9-212
 * ```
 * 本解析器以这段文本为主要数据源，逐段拆解出周次、星期、节次、教室、教师。
 */
object EamsImporter {

    /** 教务系统学生端入口 */
    const val ENTRY_URL = "https://swjw.xauat.edu.cn/student"

    /** 课表页面地址（数据页） */
    const val COURSE_TABLE_URL = "https://swjw.xauat.edu.cn/student/for-std/course-table"

    /** 成绩查询页（成绩单 / 学期索引） */
    const val GRADE_URL =
        "https://swjw.xauat.edu.cn/student/for-std/grade/sheet/semester-index/69140"

    /**
     * 成绩抓取脚本。
     *
     * 树维教务的成绩模块数据来自页面内嵌脚本 / 表格渲染，接口路径随版本变化较大。
     * 相比猜接口，**直接读取渲染完成的表格 DOM** 更稳：
     * 脚本会遍历 `table` 的每一行，按表头文字定位「课程名称 / 学分 / 成绩 / 绩点」等列，
     * 组装成结构化数组回传。
     *
     * 若页面上暂无表格（仍在加载），会返回 `ok:false`，由外层超时兜底。
     */
    val GRADE_FETCH_SCRIPT: String = """
    (function() {
      var log = [];

      function done(payload) {
        var s = 'EAMS_RESULT:' + JSON.stringify(payload);
        try {
          if (typeof eamsBridge !== 'undefined' && eamsBridge.onResult) {
            eamsBridge.onResult(s);
          } else {
            window.__EAMS_LAST_RESULT = s;
          }
        } catch (e) { window.__EAMS_LAST_RESULT = s; }
      }

      function isLoginPage() {
        var html = document.body ? document.body.innerHTML : '';
        return document.querySelector('input[type=password]') !== null ||
               html.indexOf('统一身份认证') >= 0;
      }

      // 从页面内联脚本里取出 semesters 与 studentId。
      // 形如： var semesters = JSON.parse('[ ... ]'); var studentId = 69140;
      function readSemestersAndStudent() {
        var scripts = document.querySelectorAll('script:not([src])');
        for (var i = 0; i < scripts.length; i++) {
          var c = scripts[i].textContent || '';
          if (c.indexOf('var semesters') < 0) continue;
          // 抓 JSON.parse(' .... ') 里的单引号字符串（内容里的 \" 是 JS 源码写法，本身就是合法 JSON）
          var m = c.match(/var\s+semesters\s*=\s*JSON\.parse\(\s*'([\s\S]*?)'\s*\)/);
          if (!m) {
            // 兼容双引号包裹
            m = c.match(/var\s+semesters\s*=\s*JSON\.parse\(\s*"([\s\S]*?)"\s*\)/);
          }
          if (!m) { log.push('semesters regex no match'); continue; }
          var mStudent = c.match(/var\s+studentId\s*=\s*(\d+)/);
          var sid = mStudent ? mStudent[1] : '69140';
          // 源码里是 JS 单引号字符串，内部 \" 是转义写法，需还原成真正的 JSON 文本
          var jsonText = m[1].replace(/\\"/g, '"').replace(/\\'/g, "'");
          try {
            return { semesters: JSON.parse(jsonText), studentId: sid };
          } catch (e) {
            log.push('semester parse err: ' + e + ' head=' + jsonText.substring(0, 60));
            return null;
          }
        }
        return null;
      }

      function xhrGet(url) {
        return new Promise(function(resolve) {
          var x = new XMLHttpRequest();
          x.open('GET', url, true);
          x.onreadystatechange = function() {
            if (x.readyState === 4) {
              resolve({ status: x.status, text: x.responseText || '' });
            }
          };
          x.onerror = function() { resolve({ status: -1, text: '' }); };
          x.send(null);
        });
      }

      function start() {
        if (isLoginPage()) {
          done({ ok: false, needLogin: true, data: '', log: log });
          return;
        }
        var meta = readSemestersAndStudent();
        if (!meta) {
          log.push('no inline semesters found');
          done({ ok: false, data: '', log: log });
          return;
        }
        log.push('studentId=' + meta.studentId + ' semesters=' + meta.semesters.length);

        // semester 传空 → 一次返回全部学期
        var url = window.CONTEXT_PATH + '/for-std/grade/sheet/info/' + meta.studentId + '?semester=';
        xhrGet(url).then(function(res) {
          log.push('api status=' + res.status + ' len=' + res.text.length);
          var head = (res.text || '').trim().slice(0, 1);
          if (res.status !== 200 || (head !== '{' && head !== '[')) {
            done({ ok: false, data: '', log: log });
            return;
          }
          done({
            ok: true,
            semesters: meta.semesters,
            studentId: meta.studentId,
            data: res.text,
            log: log
          });
        });
      }

      // 页面加载后稍等，确保内联脚本已执行
      setTimeout(start, 600);
    })();
    """.trimIndent()

    /**
     * 节次前移量。
     *
     * 学校现行作息是「4 个大节 × 2 小节」，下午 **14:00** 从第 5 节开始，
     * 没有课排在「原第 5、6 节」这个时间段（12:00–14:00 午休）。
     * 而教务系统导出的数据仍按**旧作息**编号 ——
     * 它把「下午第一大节」标成第 7、8 节、「下午第二大节」标成第 9、10 节。
     *
     * 因此导入时统一把 **≥7 节**的课整体上移 2 节：
     * ```
     * 原 7 节 → 5 节     原 9 节  → 7 节
     * 原 8 节 → 6 节     原 10 节 → 8 节
     * 原 11 节 → 9 节（晚上最后一节一并前移）
     * ```
     * 第 1~6 节保持原位。持续节数不变，跨节课程整体平移。
     *
     * @see shiftForwardSections
     */
    const val SECTION_SHIFT_THRESHOLD = 7

    /** 前移的节数 */
    const val SECTION_SHIFT_OFFSET = 2

    /**
     * 把教务系统的节次编号换算成本 App 的节次编号。
     *
     * 规则：`startSection >= 7` 时整体前移 2 节，其余原样返回。
     * 该换算同时用于「课程」与「调课记录」，保证两者一致。
     */
    fun shiftForwardSections(startSection: Int): Int =
        if (startSection >= SECTION_SHIFT_THRESHOLD) {
            (startSection - SECTION_SHIFT_OFFSET).coerceAtLeast(1)
        } else {
            startSection
        }

    /** 解析结果的标记前缀，用于从 WebView 回传中识别我们自己的数据 */
    const val RESULT_PREFIX = "EAMS_RESULT:"

    // ------------------------------------------------------------------
    // 统一身份认证（CAS）
    // ------------------------------------------------------------------

    /**
     * 统一身份认证入口。
     *
     * 未登录访问 `/student` 会被重定向到这里，成功后 CAS 会带 ticket 跳回 `service`，
     * 教务系统据此建立会话。因此我们只需要在隐藏 WebView 里加载这个 URL 即可。
     */
    const val SSO_LOGIN_URL = "https://swjw.xauat.edu.cn/student/sso/login"

    /** 学生个人信息接口（树维教务通用），登录后可用 */
    const val STUDENT_INFO_URL = "https://swjw.xauat.edu.cn/student/ws/student/student-info"

    /**
     * 抓取学生个人信息（姓名 / 学号 / 学院 / 专业 / 班级）。
     *
     * 实测接口：`GET /student/for-std/student-info`（页面标题「学籍 - 详情」），
     * 返回 HTML，信息挂在若干 `<table>` 里，结构形如「键 | 值 | 键 | 值 …」。
     *
     * 姓名通常不在表格内，而在页面头部的某个元素里，因此额外做多选择器兜底。
     *
     * 回传结构：{ ok, name, studentNo, className, majorName, departmentName, url, log }
     */
    val STUDENT_INFO_SCRIPT: String = """
    (function() {
      function done(payload) {
        var s = 'EAMS_RESULT:' + JSON.stringify(payload);
        try {
          if (typeof eamsBridge !== 'undefined' && eamsBridge.onResult) eamsBridge.onResult(s);
          else window.__EAMS_LAST_RESULT = s;
        } catch (e) { window.__EAMS_LAST_RESULT = s; }
      }
      function xhrGet(url) {
        return new Promise(function(res) {
          try {
            var x = new XMLHttpRequest();
            x.open('GET', url, true);
            x.onreadystatechange = function() {
              if (x.readyState === 4) res({ status: x.status, text: x.responseText || '' });
            };
            x.send();
          } catch (e) { res({ status: -1, text: '' }); }
        });
      }

      // 在文档里按「键」找相邻「值」：遍历所有 td/th，若文本等于 key 则取下一个兄弟
      function valueOfKey(root, key) {
        var nodes = root.querySelectorAll('td,th,span,div,label');
        for (var i = 0; i < nodes.length; i++) {
          var txt = (nodes[i].innerText || '').replace(/[\s:：]+/g, '');
          if (txt === key) {
            // 下一个兄弟元素
            var n = nodes[i].nextElementSibling;
            if (n) {
              var v = (n.innerText || '').trim();
              if (v) return v;
            }
          }
        }
        return '';
      }

      (async function() {
        var log = [];
        // 登录刚跳转完成时会话可能尚未就绪，先等一下再请求
        await new Promise(function(r) { setTimeout(r, 800); });

        var res = await xhrGet('/student/for-std/student-info');
        // 失败重试一次（status 0 常见于页面刚跳转、cookie 未就绪）
        if (res.status !== 200) {
          log.push('first try status=' + res.status + ', retry after 1.5s');
          await new Promise(function(r) { setTimeout(r, 1500); });
          res = await xhrGet('/student/for-std/student-info');
        }
        if (res.status !== 200) {
          done({ ok: false, error: '学籍页状态 ' + res.status, url: location.href, log: log });
          return;
        }
        var doc = new DOMParser().parseFromString(res.text, 'text/html');

        var studentNo = valueOfKey(doc, '学号');
        var departmentName = valueOfKey(doc, '专业院系') || valueOfKey(doc, '管理部门') || valueOfKey(doc, '学院');
        var majorName = valueOfKey(doc, '专业');
        var className = valueOfKey(doc, '行政班') || valueOfKey(doc, '班级');

        // ---- 抓姓名 ----
        // 学籍页里姓名可能出现在：页面标题旁、面包屑、或表格上方的信息栏。
        // 逐个选择器尝试；判据：2~4 个汉字且不含关键词。
        var name = '';
        var bad = ['学籍', '详情', '教务', '系统', '首页', '学生', '信息', '姓名', '--', '未知'];
        function looksLikeName(t) {
          if (!t) return false;
          t = t.trim();
          if (t.length < 2 || t.length > 6) return false;
          if (!/^[\u4e00-\u9fa5]+$/.test(t)) return false;   // 纯中文
          for (var i = 0; i < bad.length; i++) if (t.indexOf(bad[i]) >= 0) return false;
          return true;
        }
        var sels = [
          '.student-name', '.stu-name', '.person-name', '.info-name',
          '.user-name', '.username', '.top-name', '.header-name',
          '.title-name', '.breadcrumb span', 'h1', 'h2', '.name'
        ];
        for (var i = 0; i < sels.length && !name; i++) {
          var els = doc.querySelectorAll(sels[i]);
          for (var j = 0; j < els.length; j++) {
            var t = (els[j].innerText || '').trim();
            if (looksLikeName(t)) { name = t; break; }
          }
        }
        // 兜底 1：全文「姓名：xxx」
        var bodyText = doc.body ? doc.body.innerText : '';
        if (!name) {
          var m = bodyText.match(/姓名\s*[：:]\s*([\u4e00-\u9fa5]{2,4})/);
          if (m) name = m[1];
        }

        // 兜底 2：抓教务系统首页顶部用户区（Async）
        // 实测：首页右上角学生信息栏的结构是
        //   <a class="dropdown-toggle">你好, 高甫</a>
        // 姓名 = 「你好, 」后面的中文。这是本系统最可靠的姓名来源。
        if (!name) {
          var home = await xhrGet('/student/home');
          if (home.status === 200) {
            var hdoc = new DOMParser().parseFromString(home.text, 'text/html');
            // 2a. 精确选择器（实测命中）
            var toggle = hdoc.querySelector('a.dropdown-toggle');
            if (toggle) {
              var tv = (toggle.innerText || toggle.textContent || '').trim();
              var tm = tv.match(/(?:你好|您好|欢迎)\s*[,，、]?\s*([\u4e00-\u9fa5]{2,4})/);
              if (tm && looksLikeName(tm[1])) name = tm[1];
              log.push('dropdown-toggle="' + tv + '"');
            }
            // 2b. 其它可能的用户区选择器
            if (!name) {
              var hsels = ['.user-name', '.username', '.name', '.stu-name',
                           '.header .name', '.top .name', '.nav-user', '.avatar-name',
                           'a.studentProfile', 'a.dropdown-toggle'];
              for (var k = 0; k < hsels.length && !name; k++) {
                var hels = hdoc.querySelectorAll(hsels[k]);
                for (var z = 0; z < hels.length; z++) {
                  var ht = (hels[z].innerText || '').trim();
                  var htm = ht.match(/(?:你好|您好|欢迎)\s*[,，、]?\s*([\u4e00-\u9fa5]{2,4})/);
                  if (htm && looksLikeName(htm[1])) { name = htm[1]; break; }
                  if (looksLikeName(ht)) { name = ht; break; }
                }
              }
            }
            // 2c. 全文正则兜底
            if (!name) {
              var htext = hdoc.body ? hdoc.body.innerText : '';
              var hm = htext.match(/(?:你好|您好|欢迎)\s*[,，、]?\s*([\u4e00-\u9fa5]{2,4})/)
                    || htext.match(/姓名\s*[：:]\s*([\u4e00-\u9fa5]{2,4})/);
              if (hm && looksLikeName(hm[1])) name = hm[1];
            }
            log.push('home tried, len=' + home.text.length + ', name=' + name);
          }
        }

        // 兜底 3：从课表页/成绩页的内联脚本里找姓名
        // （树维教务多数页面顶部会内联 `var userName = '张三';` 之类）
        if (!name) {
          var scripts = document.querySelectorAll('script:not([src])');
          var nameKeys = ['userName', 'realName', 'studentName', 'nameZh', 'userRealName'];
          for (var s = 0; s < scripts.length && !name; s++) {
            var code = scripts[s].textContent || '';
            for (var k = 0; k < nameKeys.length && !name; k++) {
              var re = new RegExp('var\\s+' + nameKeys[k] +
                                  "\\s*=\\s*['\\\"]([\\u4e00-\\u9fa5]{2,4})['\\\"]");
              var mm = code.match(re);
              if (mm && looksLikeName(mm[1])) name = mm[1];
            }
          }
          if (!name) log.push('inline scripts tried');
        }

        // 兜底 4：当前页顶部「你好 xxx」/「欢迎 xxx」/「xxx 同学」文案
        // （登录后当前 URL 常常就是 /student/home，可直接命中）
        if (!name) {
          var toggleNow = document.querySelector('a.dropdown-toggle');
          if (toggleNow) {
            var tnv = (toggleNow.innerText || toggleNow.textContent || '').trim();
            var tnm = tnv.match(/(?:你好|您好|欢迎)\s*[,，、]?\s*([\u4e00-\u9fa5]{2,4})/);
            if (tnm && looksLikeName(tnm[1])) name = tnm[1];
          }
        }
        if (!name) {
          var bodyText2 = doc.body ? doc.body.innerText : '';
          var wm = bodyText2.match(/(?:欢迎|你好|您好)[,，\s]*([\u4e00-\u9fa5]{2,4})/)
                || bodyText2.match(/([\u4e00-\u9fa5]{2,4})\s*同学/);
          if (wm && looksLikeName(wm[1])) name = wm[1];
        }

        log.push('len=' + res.text.length);
        done({ ok: true, name: name, studentNo: studentNo,
               className: className, majorName: majorName,
               departmentName: departmentName, url: location.href, log: log });
      })();
    })();
    """.trimIndent()

    /**
     * 静默探测脚本：判断当前 WebView 会话是否**已经是登录态**。
     *
     * 用于「下次打开自动登录」的第一优先级路径 —— 教务系统的 cookie
     * 是磁盘持久的，只要上次的会话没过期，冷启动直接就是登录状态，
     * 根本不需要账号密码。
     *
     * **实测坑（务必看）**：教务系统的 `/student/login` 是个 Vue 空壳页
     * （标题「登入页面」，靠 require(['main']) 异步渲染，DOM 里一开始没有表单），
     * 而且它本身**不含账号密码表单** —— 它是靠 `ssoLinkList=[{'entiry':'/sso/login'}]`
     * 跳去统一身份认证的。因此「URL 里有没有 login 字样」并不可靠。
     *
     * 这里改用**功能性判据**：直接请求一个只有登录后才能访问的接口
     * （学籍页 `/student/for-std/student-info`），
     *  - 返回 200 且拿到学号等信息 → 会话有效；
     *  - 被重定向到登入页（HTML 很短 / 含「登入页面」）→ 会话失效。
     *
     * 回传：{ ok:true, loggedIn:bool, url, probeUrl, status, len }
     */
    val SESSION_CHECK_SCRIPT: String = """
    (function() {
      function done(payload) {
        var s = 'EAMS_RESULT:' + JSON.stringify(payload);
        try {
          if (typeof eamsBridge !== 'undefined' && eamsBridge.onResult) eamsBridge.onResult(s);
          else window.__EAMS_LAST_RESULT = s;
        } catch (e) { window.__EAMS_LAST_RESULT = s; }
      }
      function xhrGet(url) {
        return new Promise(function(res) {
          try {
            var x = new XMLHttpRequest();
            x.open('GET', url, true);
            x.onreadystatechange = function() {
              if (x.readyState === 4) {
                res({ status: x.status, text: x.responseText || '',
                      finalUrl: x.responseURL || url });
              }
            };
            x.send();
          } catch (e) { res({ status: -1, text: '', finalUrl: url }); }
        });
      }
      (function() {
        // 先看当前页面是不是明显的登入页（快速否定）
        var url = location.href || '';
        var title = document.title || '';
        var body = document.body ? document.body.innerText : '';

        function looksLikeLoginPage(u, t, b) {
          if (u.indexOf('authserver') >= 0) return 'cas-url';
          if (t.indexOf('登入') >= 0 || t.indexOf('登录') >= 0) return 'title';
          if (b.length < 3000 &&
              (b.indexOf('统一身份认证') >= 0 || b.indexOf('忘记密码') >= 0)) {
            return 'body';
          }
          return null;
        }

        var quick = looksLikeLoginPage(url, title, body);
        if (quick) {
          done({ ok: true, loggedIn: false, url: url, reason: quick });
          return;
        }

        // 功能性判据：请求一个必须登录才有的页面
        xhrGet('/student/for-std/student-info').then(function(res) {
          var t = res.text || '';
          var head = t.slice(0, 4000);
          // 学籍页标题是「学籍 - 详情」；被重定向时会是「登入页面」
          var isLogin = res.status !== 200 ||
                        head.indexOf('登入页面') >= 0 ||
                        head.indexOf('统一身份认证') >= 0 ||
                        t.length < 1200;
          var loggedIn = !isLogin;
          done({ ok: true, loggedIn: loggedIn, url: url, status: res.status,
                 len: t.length, probeUrl: '/student/for-std/student-info' });
        });
      })();
    })();
    """.trimIndent()

    /**
     * 统一身份认证登录脚本。
     *
     * 教务系统走的是金智 CAS，登录页为：
     *   http://authserver.xauat.edu.cn/authserver/login?service=...
     *
     * 关键点（已实测确认）：
     *  1. 页面隐藏域里有 `execution` 与 `pwdEncryptSalt`（每次刷新都会变，必须现取现用）；
     *  2. 密码必须 AES 加密后提交：明文 = randomString(64) + password，
     *     key = salt，iv = randomString(16)，AES/CBC/PKCS7 → Base64；
     *     加密结果写入隐藏域 `saltPassword`，同时把 `#password` 置为 disabled（不参与提交）；
     *  3. 提交字段：username / saltPassword / execution / _eventId=submit /
     *     cllt=userNameLogin / dllt=generalLogin / lt / rmShown=1。
     *
     * 脚本参数通过 [buildLoginScript] 注入（账号密码由原生输入框收集）。
     */
    fun buildLoginScript(username: String, password: String): String {
        val u = jsEscape(username)
        val p = jsEscape(password)
        return """
        (function() {
          var log = [];
          function done(payload) {
            var s = 'EAMS_RESULT:' + JSON.stringify(payload);
            try {
              if (typeof eamsBridge !== 'undefined' && eamsBridge.onResult) {
                eamsBridge.onResult(s);
              } else { window.__EAMS_LAST_RESULT = s; }
            } catch (e) { window.__EAMS_LAST_RESULT = s; }
          }

          // ---- 与页面 encrypt.js 完全一致的 AES 实现（内联安全副本）----
          var aesChars = 'ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678';
          function randomString(n) {
            var s = '';
            for (var i = 0; i < n; i++) s += aesChars.charAt(Math.floor(Math.random() * aesChars.length));
            return s;
          }

          // CryptoJS 由页面自带；若未加载则报错
          if (typeof CryptoJS === 'undefined') {
            done({ ok: false, error: 'CryptoJS 未加载（可能不是 CAS 登录页）', url: location.href });
            return;
          }
          function getAesString(data, key, iv) {
            key = key.replace(/(^\s+)|(\s+$)/g, '');
            var k = CryptoJS.enc.Utf8.parse(key);
            var v = CryptoJS.enc.Utf8.parse(iv);
            return CryptoJS.AES.encrypt(data, k, { iv: v, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 }).toString();
          }
          function encryptPassword(pwd, salt) {
            try { return salt ? getAesString(randomString(64) + pwd, salt, randomString(16)) : pwd; }
            catch (e) { return pwd; }
          }

          // ---- 1. 精确定位「界面上真正可见」的账号密码表单 ----
          // 页面初始化时会把 #pwdLoginDiv 的 HTML 复制一份到 #loginViewDiv，
          // 于是 DOM 里会出现两份 #pwdFromId。用户操作的是复制品，
          // 因此必须选 #loginViewDiv 内的那一份，才能让页面校验逻辑读到值。
          var form = document.querySelector('#loginViewDiv #pwdFromId')
                  || document.querySelector('#pwdFromId');
          if (!form) { done({ ok: false, error: '未找到账号密码登录表单 #pwdFromId', url: location.href }); return; }
          var saltEl = document.querySelector('#pwdEncryptSalt');
          var salt = saltEl ? saltEl.value : '';
          if (!salt) { done({ ok: false, error: '未取到 pwdEncryptSalt', url: location.href }); return; }

          var userEl = form.querySelector('#username');
          if (userEl) {
            userEl.value = '$u';
            try { userEl.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) {}
          }

          // 可见密码框 #password 是 readonly，需要先解除才能赋值
          var pwdEl = form.querySelector('#password');
          if (pwdEl) {
            pwdEl.removeAttribute('readonly');
            pwdEl.value = '$p';
            try { pwdEl.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) {}
          }

          // ---- 3. 走页面自己的登录流程 ----
          // startLogin() 内部会：checkForm() 加密密码 → 写入 #saltPassword → 提交。
          // 延迟 1.2s，留时间给指纹上报（/bfp/info）。
          setTimeout(function () {
            try {
              var btn = document.querySelector('#loginViewDiv #login_submit')
                     || document.querySelector('#login_submit') || form;
              if (typeof startLogin === 'function') {
                startLogin(btn);
                done({
                  ok: true, stage: 'submitted', via: 'startLogin',
                  hasPwd: !!(form.querySelector('#password')),
                  log: [],
                });
              } else {
                var saltPwdEl = form.querySelector('#saltPassword');
                if (!saltPwdEl) {
                  saltPwdEl = document.createElement('input');
                  saltPwdEl.type = 'hidden';
                  saltPwdEl.id = 'saltPassword';
                  form.appendChild(saltPwdEl);
                }
                saltPwdEl.name = 'password';
                saltPwdEl.value = encryptPassword('$p', salt);
                var clltEl = form.querySelector('#cllt');
                if (clltEl) clltEl.value = 'userNameLogin';
                form.submit();
                done({ ok: true, stage: 'submitted', via: 'manual' });
              }
            } catch (e) {
              done({ ok: false, error: '提交失败: ' + e });
            }
          }, 1200);
        })();
        """.trimIndent()
    }


    /** 把字符串安全地嵌进 JS 单引号字面量 */
    fun jsEscape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    /**
     * 诊断脚本：把当前页面的关键状态回传，用于排查页面空白等问题。
     * 纯读取，无副作用。
     */
    val DIAGNOSE_SCRIPT: String = """
    (function() {
      function done(payload) {
        var s = 'EAMS_RESULT:' + JSON.stringify(payload);
        try {
          if (typeof eamsBridge !== 'undefined' && eamsBridge.onResult) {
            eamsBridge.onResult(s);
          } else {
            window.__EAMS_LAST_RESULT = s;
          }
        } catch (e) { window.__EAMS_LAST_RESULT = s; }
      }
      var info = { ok: true, diagnose: true, url: location.href,
                   title: document.title, readyState: document.readyState,
                   bodyLen: document.body ? document.body.innerHTML.length : -1 };
      done(info);
    })();
    """.trimIndent()

    /**
     * 抓取脚本：注入 WebView 执行。
     *
     * 流程：
     * 1. 打开课表页，从 `<option selected value="...">` 读取当前学期 ID；
     * 2. 请求 `/student/for-std/course-table/get-data?bizTypeId=2&semesterId={id}`；
     * 3. 把返回的 JSON 原样回传（结果以 `EAMS_RESULT:` 前缀标识）。
     */
    val FETCH_SCRIPT: String = """
    (function() {
      var log = [];
      var probes = [];

      function done(payload) {
        var s = 'EAMS_RESULT:' + JSON.stringify(payload);
        try {
          if (typeof eamsBridge !== 'undefined' && eamsBridge.onResult) {
            eamsBridge.onResult(s);
          } else {
            window.__EAMS_LAST_RESULT = s;
          }
        } catch (e) { window.__EAMS_LAST_RESULT = s; }
      }

      function fetchText(url) {
        return fetch(url, {
          credentials: 'include',
          headers: { 'Accept': 'application/json, text/html, */*' }
        }).then(function(r) {
          return r.text().then(function(t) {
            return { url: url, status: r.status, text: t || '' };
          });
        }).catch(function(e) {
          return { url: url, status: -1, text: '', error: String(e && e.message || e) };
        });
      }

      fetchText('/student/for-std/course-table').then(function(page) {
        probes.push({ url: page.url, status: page.status, len: page.text.length });

        if (page.status !== 200 || !page.text) {
          done({ ok: false, url: page.url, data: '', probes: probes, log: log });
          return;
        }

        // 读取当前选中学期 ID
        var semesterId = null;
        var m = page.text.match(/<option[^>]*selected[^>]*value=["'](\d+)["']/i)
             || page.text.match(/<option[^>]*value=["'](\d+)["'][^>]*selected/i)
             || page.text.match(/<option[^>]*value=["'](\d+)["']/i);
        if (m) semesterId = m[1];
        log.push('semesterId=' + semesterId);

        var candidates = [];
        if (semesterId) {
          candidates.push('/student/for-std/course-table/get-data?bizTypeId=2&semesterId=' + semesterId);
        }
        candidates.push('/student/for-std/course-table/get-data?bizTypeId=2');

        var i = 0;
        function next() {
          if (i >= candidates.length) {
            done({ ok: false, url: page.url, data: '', semesterId: semesterId,
                   probes: probes, log: log });
            return;
          }
          var u = candidates[i++];
          fetchText(u).then(function(res) {
            var head = (res.text || '').trim().slice(0, 1);
            var isJson = res.status === 200 && (head === '{' || head === '[');
            probes.push({ url: u, status: res.status, len: res.text.length });
            log.push(u + ' -> ' + res.status + ' len=' + res.text.length);
            if (isJson) {
              done({ ok: true, url: u, data: res.text, semesterId: semesterId,
                     probes: probes, log: log });
            } else {
              next();
            }
          });
        }
        next();
      });
    })();
    """.trimIndent()

    /**
     * 解析注入脚本回传的 JSON 载荷，转换为课程列表。
     *
     * @param raw 形如 `EAMS_RESULT:{"ok":true,...}` 的字符串
     */
    fun parse(raw: String): ImportResult {
        val jsonPart = raw.substringAfter(RESULT_PREFIX, "").trim()
        if (jsonPart.isEmpty()) {
            return ImportResult(false, emptyList(), "抓取脚本未返回数据", null)
        }
        return try {
            val root = JSONObject(jsonPart)
            val probeSummary = buildProbeSummary(root)

            if (!root.optBoolean("ok", false)) {
                return ImportResult(
                    false, emptyList(),
                    "未获取到课表数据。\n各接口探测：\n$probeSummary",
                    null,
                )
            }
            val dataText = root.optString("data", "")
            val courses = parseCourseTable(dataText)

            if (courses.isEmpty()) {
                ImportResult(
                    false, emptyList(),
                    "接口有响应但未解析出课程。\n接口：${root.optString("url")}\n" +
                        "响应长度：${dataText.length}\n" +
                        "响应开头：\n${dataText.take(400)}",
                    null,
                )
            } else {
                // 提取教务系统给出的「当前教学周」，用于反推开学日期
                val currentWeek = runCatching { JSONObject(dataText).optInt("currentWeek", 0) }
                    .getOrDefault(0)
                    .takeIf { it > 0 }
                ImportResult(true, courses, null, null, currentWeek)
            }
        } catch (e: Exception) {
            ImportResult(false, emptyList(), "解析异常：${e.message}", null)
        }
    }

    /** 把探测结果整理成可读文本 */
    private fun buildProbeSummary(root: JSONObject): String {
        val arr = root.optJSONArray("probes") ?: return "(无)"
        val sb = StringBuilder()
        for (i in 0 until minOf(arr.length(), 12)) {
            val p = arr.optJSONObject(i) ?: continue
            sb.append("• ").append(p.optString("url"))
                .append(" → HTTP ").append(p.optInt("status"))
                .append(", len=").append(p.optInt("len"))
                .append('\n')
        }
        return sb.toString().trimEnd()
    }

    /**
     * 解析课表接口返回的 JSON。
     *
     * 主要数据源：`lessons[].scheduleText.dateTimePlaceText.text`
     * 该文本每段以 `;` 分隔，形如：
     * `1~5,7~8周 周三 第三节~第四节 草堂校区 草堂16-208`
     */
    private fun parseCourseTable(text: String): List<Course> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return emptyList()
        }
        val lessons = root.optJSONArray("lessons") ?: return emptyList()
        val out = mutableListOf<Course>()

        for (i in 0 until lessons.length()) {
            val lesson = lessons.optJSONObject(i) ?: continue

            // 课程名：优先 course.nameZh
            val name = lesson.optJSONObject("course")?.optString("nameZh").orEmpty()
                .ifBlank { lesson.optString("nameZh") }
                .ifBlank { lesson.optString("courseName") }
            if (name.isBlank()) continue

            // 教师：teacherAssignmentStr 形如 "赵亮(010105200),陈登峰(010105182)"
            val teacherRaw = lesson.optString("teacherAssignmentStr")
            val teacher = teacherRaw.split(",")
                .map { it.substringBefore("(").trim() }
                .filter { it.isNotBlank() }
                .joinToString("、")
                .ifBlank { lesson.optString("teacherName") }

            // 时间文本
            val textZh = lesson.optJSONObject("scheduleText")
                ?.optJSONObject("dateTimePlaceText")
                ?.optString("text")
                .orEmpty()
                .ifBlank {
                    lesson.optJSONObject("scheduleText")
                        ?.optJSONObject("dateTimePlaceText")
                        ?.optString("textZh").orEmpty()
                }
            if (textZh.isBlank()) continue

            val colorIndex = (name.hashCode().let { if (it < 0) -it else it }) % 10

            // 逐段解析：每段一个上课时段。
            // 注意：一个时段可能覆盖多段离散周次（如 "1~3,6~7周"），
            // 数据模型只支持单个连续区间，因此这里按周次区间**拆成多条课程**。
            for (seg in textZh.split(";", "；")) {
                val s = seg.trim().replace("\n", " ")
                if (s.isEmpty()) continue
                val slots = parseScheduleSegment(s)
                for (slot in slots) {
                    out += Course(
                        name = name,
                        teacher = teacher,
                        room = slot.room,
                        dayOfWeek = slot.dayOfWeek,
                        // 教务系统用的还是旧作息编号，统一前移 2 节后再入库
                        startSection = shiftForwardSections(slot.startSection),
                        sectionCount = slot.sectionCount,
                        startWeek = slot.startWeek,
                        endWeek = slot.endWeek,
                        weekParity = slot.parity,
                        colorIndex = colorIndex,
                    )
                }
            }
        }
        return out
    }

    /** 一个上课时段的解析结果 */
    private data class Slot(
        val dayOfWeek: Int,
        val startSection: Int,
        val sectionCount: Int,
        val startWeek: Int,
        val endWeek: Int,
        val parity: Int,
        val room: String,
    )

    /**
     * 解析单个时段文本，返回**一个或多个** Slot。
     *
     * 输入形如（字段以空格分隔）：
     * ```
     * 1~5,7~8周 周三 第三节~第四节 草堂校区 草堂16-208
     * 5~7(单),8~13周 周二 第三节~第四节 草堂校区 草堂9-212
     * 9~16周 周二 第一节~第二节 草堂校区 草堂9-204
     * ```
     *
     * 为什么返回列表：周次段可能是多段离散区间（`1~5,7~8`），
     * 而数据模型只支持单个连续区间 + 单双周，
     * 因此这里把每个区间**拆成独立的 Slot**，避免把 `1~5,7~8` 误压成 `1~8`。
     *
     * 例：`1~3,6~7周` → [ (1,3,每周), (6,7,每周) ]
     *     `5~7(单),8~13周` → [ (5,7,单周), (8,13,每周) ]
     */
    private fun parseScheduleSegment(text: String): List<Slot> {
        val parts = text.split(" ").filter { it.isNotBlank() }
        if (parts.size < 3) return emptyList()

        val weekPart = parts[0]
        val dayPart = parts[1]
        val sectionPart = parts[2]
        val restParts = parts.drop(3)

        // --- 星期 ---
        val dayOfWeek = dayPartToNumber(dayPart) ?: return emptyList()

        // --- 节次 ---
        val sectionNums = Regex("""第([一二三四五六七八九十两\d]+)节""").findAll(sectionPart)
            .mapNotNull { cnToInt(it.groupValues[1]) }
            .toList()
        if (sectionNums.isEmpty()) return emptyList()
        val startSection = sectionNums.min()
        val sectionCount = (sectionNums.max() - sectionNums.min() + 1).coerceAtLeast(1)

        // --- 教室 ---
        val room = restParts
            .filterNot { it.contains("校区") }
            .joinToString(" ")
            .trim()
            .ifBlank { restParts.lastOrNull().orEmpty() }

        // --- 周次：逐个区间解析，单双周标记只作用于紧跟其后的区间 ---
        // 形如 "1~5,7~8周"、"5~7(单),8~13周"、"6周"、"1~3,11周"
        val weekBody = weekPart.removeSuffix("周")
        val slots = mutableListOf<Slot>()
        for (seg in weekBody.split(",", "，")) {
            val piece = seg.trim()
            if (piece.isEmpty()) continue

            // 该区间的单双周标记
            val parity = when {
                piece.contains("单") -> Course.PARITY_ODD
                piece.contains("双") -> Course.PARITY_EVEN
                else -> Course.PARITY_ALL
            }

            // 提取区间或单周
            val rangeMatch = Regex("""(\d+)\s*[~\-]\s*(\d+)""").find(piece)
            val (a, b) = when {
                rangeMatch != null -> {
                    rangeMatch.groupValues[1].toInt() to rangeMatch.groupValues[2].toInt()
                }
                else -> {
                    val single = Regex("""(\d+)""").find(piece)?.groupValues?.get(1)?.toInt()
                    if (single == null) continue
                    single to single
                }
            }
            slots += Slot(
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                startWeek = minOf(a, b),
                endWeek = maxOf(a, b),
                parity = parity,
                room = room,
            )
        }
        return slots
    }

    /** 中文星期 → 数字（周一=1 … 周日=7） */
    private fun dayPartToNumber(s: String): Int? = when {
        s.contains("一") -> 1
        s.contains("二") -> 2
        s.contains("三") -> 3
        s.contains("四") -> 4
        s.contains("五") -> 5
        s.contains("六") -> 6
        s.contains("日") || s.contains("天") -> 7
        else -> null
    }

    /** 中文数字 → 整数，支持 1~20 的常见写法 */
    private fun cnToInt(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '一' to 1, '两' to 2, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
        )
        if (s == "十") return 10
        if (s.startsWith("十")) return 10 + (digits[s.getOrNull(1)] ?: 0)
        if (s.contains("十")) {
            val idx = s.indexOf('十')
            val tens = digits[s.getOrNull(idx - 1)] ?: 0
            val ones = if (idx + 1 < s.length) digits[s.getOrNull(idx + 1)] ?: 0 else 0
            return tens * 10 + ones
        }
        return digits[s.getOrNull(0)]
    }

    // ------------------------------------------------------------------
    // 成绩
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 成绩
    // ------------------------------------------------------------------

    /**
     * 解析成绩抓取脚本回传的载荷。
     *
     * 载荷结构（由 [GRADE_FETCH_SCRIPT] 产出）：
     * ```
     * { ok, studentId, semesters: [ {id, nameZh, ...} ], data: "<info 接口的原始 JSON>" }
     * ```
     * `data` 对应接口 `/for-std/grade/sheet/info/{studentId}?semester=`，
     * 其结构为：
     * ```
     * {
     *   "semesters": [ {id, nameZh, ...} ],
     *   "semesterId2studentGrades": { "341": [ {course:{nameZh,code,credits}, gp, gaGrade, passed, published, ...} ] }
     * }
     * ```
     */
    fun parseGrades(raw: String): GradeResult {
        val jsonPart = raw.substringAfter(RESULT_PREFIX, "").trim()
        if (jsonPart.isEmpty()) {
            return GradeResult(false, emptyList(), emptyList(), "抓取脚本未返回数据", null)
        }
        return try {
            val root = JSONObject(jsonPart)
            if (!root.optBoolean("ok", false)) {
                val needLogin = root.optBoolean("needLogin", false)
                return GradeResult(
                    false, emptyList(), emptyList(),
                    if (needLogin) "尚未登录教务系统" else
                        "未获取到成绩数据。\n诊断：${root.optJSONArray("log") ?: "(无)"}",
                    null,
                    needLogin = needLogin,
                )
            }

            // 学期名映射：id -> nameZh
            val semesterNames = mutableMapOf<String, String>()
            root.optJSONArray("semesters")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").ifBlank { o.optString("value") }
                    val name = o.optString("nameZh").ifBlank { o.optString("name") }
                    if (id.isNotBlank() && name.isNotBlank()) semesterNames[id] = name
                }
            }

            val dataText = root.optString("data", "")
            val data = runCatching { JSONObject(dataText) }.getOrNull()
                ?: return GradeResult(false, emptyList(), emptyList(), "成绩数据格式异常", null)

            // 合并接口里自带的 semesters（更权威）
            data.optJSONArray("semesters")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val name = o.optString("nameZh").ifBlank { o.optString("name") }
                    if (id.isNotBlank() && name.isNotBlank()) semesterNames[id] = name
                }
            }

            val bySemester = data.optJSONObject("semesterId2studentGrades")
                ?: return GradeResult(false, emptyList(), emptyList(), "成绩数据为空", null)

            val semesters = mutableListOf<SemesterGrades>()
            val keys = bySemester.keys()
            while (keys.hasNext()) {
                val sid = keys.next()
                val arr = bySemester.optJSONArray(sid) ?: continue
                val items = mutableListOf<GradeItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    gradeFromObject(o)?.let { items += it }
                }
                if (items.isEmpty()) continue
                val name = semesterNames[sid] ?: sid
                semesters += SemesterGrades(
                    semesterId = sid,
                    semesterName = name,
                    grades = items,
                )
            }

            if (semesters.isEmpty()) {
                GradeResult(
                    false, emptyList(), emptyList(),
                    "接口有响应但未解析出成绩。\n响应开头：\n${dataText.take(400)}",
                    null,
                )
            } else {
                // 按学期名倒序（新的在前）：2025-2026-2 > 2025-2026-1 > ...
                val ordered = semesters.sortedWith(
                    compareByDescending<SemesterGrades> { it.semesterName },
                )
                GradeResult(true, ordered.flatMap { it.grades }, ordered, null, null)
            }
        } catch (e: Exception) {
            GradeResult(false, emptyList(), emptyList(), "解析异常：${e.message}", null)
        }
    }

    /**
     * 尝试把一个「成绩条目」JSON 解释成 [GradeItem]；不像成绩则返回 null。
     *
     * 真实字段（树维 EAMS）：
     * `course.nameZh` / `course.code` / `course.credits` / `gp` / `gaGrade`
     * / `passed` / `published` / `courseType.nameZh` / `courseProperty.nameZh`
     * / `compulsory`
     */
    private fun gradeFromObject(o: JSONObject): GradeItem? {
        val course = o.optJSONObject("course")
        val name = course?.optString("nameZh").orEmpty()
            .ifBlank { course?.optString("name").orEmpty() }
            .ifBlank { o.optString("courseName") }
            .trim()
        if (name.isEmpty()) return null

        fun num(v: Any?): Double? = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }

        val credit = num(course?.opt("credits")) ?: num(course?.opt("credit"))
            ?: num(o.opt("credit"))
        val point = num(o.opt("gp")) ?: num(o.opt("gradePoint")) ?: num(o.opt("gpa"))
        val scoreText = o.optString("gaGrade")
            .ifBlank { o.optString("score") }
            .ifBlank { o.optString("scoreText") }
            .trim()
        val code = course?.optString("code").orEmpty().trim().takeIf { it.isNotBlank() }

        val courseType = o.optJSONObject("courseType")?.optString("nameZh").orEmpty()
            .ifBlank { o.optString("courseType").takeIf { it.isNotBlank() && it != "null" }.orEmpty() }
            .trim().takeIf { it.isNotBlank() }
        val courseProperty = o.optJSONObject("courseProperty")?.optString("nameZh").orEmpty()
            .trim().takeIf { it.isNotBlank() }

        // 是否必修：compulsory=true 必修，否则选修
        val compulsory = if (o.has("compulsory") && !o.isNull("compulsory")) {
            o.optBoolean("compulsory")
        } else null

        if (scoreText.isEmpty() && point == null && credit == null) return null

        return GradeItem(
            courseName = name,
            courseCode = code,
            scoreText = scoreText,
            credit = credit,
            gradePoint = point,
            semester = null,
            courseType = courseType,
            courseProperty = courseProperty,
            compulsory = compulsory,
            passed = if (o.has("passed") && !o.isNull("passed")) o.optBoolean("passed") else null,
        )
    }

    // ------------------------------------------------------------------
    // 登录相关解析
    // ------------------------------------------------------------------

    /**
     * 解析 [buildLoginScript] 的回传结果。
     *
     * 脚本只负责「把表单填好并提交」，因此这里只判断是否提交成功；
     * 真正的登录成败要靠后续请求学生信息来判断（提交后页面会跳转）。
     */
    fun parseLoginSubmit(raw: String): Pair<Boolean, String?> {
        val jsonPart = raw.substringAfter(RESULT_PREFIX, "").trim()
        if (jsonPart.isEmpty()) return false to "无返回内容"
        return try {
            val o = JSONObject(jsonPart)
            val ok = o.optBoolean("ok", false)
            if (ok) true to null
            else false to (o.optString("error").ifBlank { "登录提交失败" })
        } catch (e: Exception) {
            false to "解析失败: ${e.message}"
        }
    }

    /** 解析 [STUDENT_INFO_SCRIPT] 的回传结果 */
    fun parseStudentInfo(raw: String): LoginResult {
        val jsonPart = raw.substringAfter(RESULT_PREFIX, "").trim()
        if (jsonPart.isEmpty()) return LoginResult(false, error = "无返回内容")
        return try {
            val o = JSONObject(jsonPart)
            val name = o.optString("name").trim()
            val no = o.optString("studentNo").trim()
            val cls = o.optString("className").trim()
            val major = o.optString("majorName").trim()
            val dept = o.optString("departmentName").trim()
            val ok = o.optBoolean("ok", false)

            if (name.isBlank() && no.isBlank() && cls.isBlank() && dept.isBlank()) {
                LoginResult(false, error = o.optString("error").ifBlank { "未获取到学生信息" })
            } else {
                LoginResult(
                    success = true,
                    profile = UserProfile(
                        name = name,
                        studentNo = no,
                        className = cls,
                        majorName = major,
                        departmentName = dept,
                        loginTime = System.currentTimeMillis(),
                    ),
                )
            }
        } catch (e: Exception) {
            LoginResult(false, error = "解析失败: ${e.message}")
        }
    }

    /**
     * 解析 [SESSION_CHECK_SCRIPT] 的回传结果。
     *
     * @return true 表示当前 WebView 会话已经是登录态
     */
    fun parseSessionCheck(raw: String): Boolean {
        val jsonPart = raw.substringAfter(RESULT_PREFIX, "").trim()
        if (jsonPart.isEmpty()) return false
        return runCatching { JSONObject(jsonPart).optBoolean("loggedIn", false) }
            .getOrDefault(false)
    }

    /**
     * 判断当前页面是否「已登录」。
     *
     * 未登录时教务系统会重定向到登入页（标题「登入页面」或存在密码输入框）。
     */
    fun isLoginPage(raw: String): Boolean {
        val jsonPart = raw.substringAfter(RESULT_PREFIX, "").trim()
        if (jsonPart.isEmpty()) return true
        val body = if (jsonPart.startsWith("{")) {
            runCatching { JSONObject(jsonPart).optString("bodyLen") }.getOrDefault("")
        } else jsonPart
        // 页面标题或内容含「登入页面 / 统一身份认证」视为未登录
        return body.contains("登入") || body.contains("统一身份认证")
    }
}

/** 导入结果 */
data class ImportResult(
    val success: Boolean,
    val courses: List<Course>,
    val error: String?,
    val termStart: LocalDate?,
    /** 教务系统给出的当前教学周（可能为 null） */
    val currentWeek: Int? = null,
)

/** 单条成绩 */
data class GradeItem(
    val courseName: String,
    /** 课程代码（如 A091010） */
    val courseCode: String? = null,
    /** 成绩原样文本（可能是数字，也可能是「优秀 / 良好 / 通过」等） */
    val scoreText: String,
    val credit: Double?,
    val gradePoint: Double?,
    val semester: String?,
    val courseType: String?,
    /** 课程性质，如「专业基础课程-必修」 */
    val courseProperty: String? = null,
    /** 是否必修 */
    val compulsory: Boolean? = null,
    /** 是否通过 */
    val passed: Boolean? = null,
) {
    /** 数值成绩（可选） */
    val scoreValue: Double? get() = scoreText.trim().toDoubleOrNull()
}

/** 单个学期的成绩集合 */
data class SemesterGrades(
    val semesterId: String,
    val semesterName: String,
    val grades: List<GradeItem>,
) {
    /** 该学期总学分（仅统计已发布且有学分的课程） */
    val totalCredit: Double get() = grades.mapNotNull { it.credit }.sum()

    /** 该学期加权绩点 */
    val weightedGpa: Double?
        get() {
            val samples = grades.filter { it.credit != null && it.gradePoint != null }
            if (samples.isEmpty()) return null
            val sum = samples.sumOf { it.credit!! * it.gradePoint!! }
            val cr = samples.sumOf { it.credit!! }
            return if (cr > 0) sum / cr else null
        }
}

/** 成绩抓取结果 */
data class GradeResult(
    val success: Boolean,
    val grades: List<GradeItem>,
    /** 按学期分组的成绩（新在前） */
    val semesters: List<SemesterGrades>,
    val error: String?,
    val semesterName: String?,
    /** 是否因为「未登录」而失败 —— 用于引导用户去登录页 */
    val needLogin: Boolean = false,
)

/**
 * 登录后拿到的学生身份信息。
 *
 * 全部字段都可能为空：不同版本的教务系统暴露的字段不同，
 * 能拿到多少显示多少，不做强制校验。
 */
data class UserProfile(
    /** 姓名 */
    val name: String = "",
    /** 学号 */
    val studentNo: String = "",
    /** 班级 */
    val className: String = "",
    /** 专业 */
    val majorName: String = "",
    /** 学院 */
    val departmentName: String = "",
    /** 登录时间（epoch millis） */
    val loginTime: Long = 0L,
) {
    val isEmpty: Boolean get() = name.isBlank() && studentNo.isBlank() && className.isBlank()
}

/** 登录流程结果 */
data class LoginResult(
    val success: Boolean,
    val profile: UserProfile? = null,
    val error: String? = null,
    /** 是否需要验证码（当前未处理，仅提示用户） */
    val needCaptcha: Boolean = false,
)

