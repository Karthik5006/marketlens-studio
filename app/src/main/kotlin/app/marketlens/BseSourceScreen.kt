package app.marketlens

import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONTokener

@Composable internal fun BseSourceScreen(vm: LensViewModel, a: UiActions) {
    var browser by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("Loading the official BSE page…") }
    var reading by remember { mutableStateOf(false) }
    var pageFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Label("Original BSE source", Violet)
    Text("Read the exchange.\nKeep the facts.", color = Ink, fontSize = 29.sp)
    Caption("This is BSE's original website. Select its current IPO list, wait for the table, then read it into Studio. If the exchange shows an access check, complete it yourself. No login or order placement is needed.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action("Reload BSE", { browser?.loadUrl(BseIssues.PAGE) }, icon = Icons.Rounded.Refresh)
        Action("Open in browser", { a.url(browser?.url?.takeIf { BseIssues.trusted(it) } ?: BseIssues.PAGE) }, icon = Icons.Rounded.OpenInNew)
    }
    Caption(status)
    AndroidView(modifier = Modifier.fillMaxWidth().height(570.dp), factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setGeolocationEnabled(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (BseIssues.trusted(url)) return false
                    if (url.startsWith("https://")) a.url(url)
                    return true
                }
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { pageFailed = false; status = "Loading BSE…" }
                override fun onPageFinished(view: WebView, url: String) { if (!pageFailed) status = "When the dated IPO table is visible, select Read displayed IPOs below." }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) { pageFailed = true; status = "BSE could not load: ${error.description}. You can add facts from an original document in the IPO screen." }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) { pageFailed = true; status = "BSE returned HTTP ${response.statusCode}. No issue data were imported. Try your browser or add original-source facts." }
                }
            }
            browser = this
            loadUrl(BseIssues.PAGE)
        }
    })
    Action(if (reading) "Reading…" else "Read displayed IPOs", {
        val view = browser
        if (view == null || !BseIssues.canCapture(view.url.orEmpty())) { status = "Open the original BSE public-issues page first."; return@Action }
        reading = true
        view.evaluateJavascript(BSE_TABLE_SCRIPT) { result ->
            scope.launch {
                try {
                    val raw = JSONTokener(result).nextValue() as? String ?: error("No readable table returned")
                    val count = vm.importBsePage(raw)
                    status = "Saved $count BSE issues with their original dates and source links. Open IPOs to inspect them."
                } catch (e: Exception) { status = e.message ?: "Could not read the displayed table" }
                finally { reading = false }
            }
        }
    }, primary = true, enabled = !reading, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Download)
    Action("Return to IPOs", { a.route("IPOs") }, modifier = Modifier.fillMaxWidth())
    Caption("Only named columns and valid opening/closing dates are accepted. Unsupported table layouts remain unimported. Counts and prices are never guessed. The website may store first-party cookies; third-party cookies and file access are disabled.")
    DisposableEffect(Unit) { onDispose { browser?.stopLoading(); browser?.destroy() } }
}

/** Reads rendered public tables only; no native JS bridge or interaction automation. */
internal val BSE_TABLE_SCRIPT = """
(function() {
 const clean = e => (e.innerText || e.textContent || '').replace(/\s+/g,' ').trim();
 const visible = e => e.getClientRects().length > 0;
 const tables = Array.from(document.querySelectorAll('table,[role="table"],[role="grid"]')).filter(visible).slice(0,40).map(table => {
   const rows = Array.from(table.querySelectorAll('tr,[role="row"]')).filter(visible).slice(0,501).map(row => ({
     cells: Array.from(row.querySelectorAll('th,td,[role="cell"],[role="columnheader"],[role="gridcell"]')).map(clean),
     links: Array.from(row.querySelectorAll('a[href]')).map(a => a.href)
   }));
   const selected = Array.from(document.querySelectorAll('select option:checked,[role="tab"][aria-selected="true"]')).map(clean).join(' ');
   return {context: clean(table.querySelector('caption') || {textContent:''}) + ' ' + selected, rows:rows};
 });
 const out = JSON.stringify({url:location.href,title:document.title,stamp:'',tables:tables});
 return out.length <= 1000000 ? out : JSON.stringify({url:location.href,tables:[]});
})()
""".trimIndent()
