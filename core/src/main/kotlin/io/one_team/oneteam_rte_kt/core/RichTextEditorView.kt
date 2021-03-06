package io.one_team.oneteam_rte_kt.core

import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.rich_text_editor_view.view.*
import java.net.URL

/**
 * A View wrapping oneteam-rte javascript library.
 * @see https://github.com/oneteam-dev/oneteam-rte
 */
class RichTextEditorView(context: Context, attr: AttributeSet?) : LinearLayout(context, attr) {
    /**
     * Html content in a editor
     * This is two-way bound value so that make exactly same as a value in the editor.
     */
    var content: String
        get() = _content
        set(value) {
            _content = value
            webView.setHTML(value)
        }

    private var _content = ""
        set(value) {
            field = value
            onContentChanged?.invoke(value)
        }

    var rawMentions: List<Mentionable>
        get() = _rawMentions
        set(value) {
            _rawMentions = value
            webView.setMentions(value)
        }

    private var _rawMentions = emptyList<Mentionable>()
        set(value) {
            field = value
        }

    /**
     * Inline styles applied to selected line
     * @see [InlineStyle]
     */
    var inlineStyles: List<InlineStyle> = listOf()
        set(value) {
            field = value
            onInlineStylesChanged?.invoke(value)
        }

    /**
     * Block style applied to selected line
     * @see [BlockStyle]
     */
    var blockStyle: BlockStyle = BlockStyle.Unstyled
        set(value) {
            field = value
            onBlockStyleChanged?.invoke(value)
        }

    var onContentChanged: ((String) -> Unit)? = null
    var onInlineStylesChanged: ((List<InlineStyle>) -> Unit)? = null
    var onBlockStyleChanged: ((BlockStyle) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.rich_text_editor_view, this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupWebView()
    }

    /**
     * Toggle line style with [BlockStyle]
     *
     * @param style a block style you want to apply or unapply
     * @see [BlockStyle]
     */
    fun toggleBlockStyle(style: BlockStyle) {
        webView.toggleBlockType(style.stringValue)
    }

    /**
     * Toggle selected text with [InlineStyle]
     * @param style a inline style you want to apply or unapply
     * @see [InlineStyle]
     */
    fun toggleInlineStyle(style: InlineStyle) {
        webView.toggleInlineStyle(style.stringValue)
    }

    /**
     * Add link href to selected text
     *
     * @param url url link you want to attach selected text
     */
    fun insertLink(url: URL) {
        webView.insertLink(url.toString())
    }

    /**
     * Remove link href from selected text
     */
    fun removeLink() {
        webView.removeLink()
    }

    /**
     * Insert iframe code
     *
     * @param src iframe code you want to insert into content
     */
    fun insertIFrame(src: String) {
        webView.insertIFrame(src.replace("\"", "\\\""))
    }

    /**
     * Insert a file
     *
     * @param name file name
     * @param url file url
     */
    fun insertFile(name: String, url: URL) {
        val json = """{"name": "$name", "url": "$url"}"""
        webView.insertFileDownload(json)
    }

    /**
     * Insert a image
     * @param name image name that will be used by alt attribute etc...
     * @param originalUrl image url that will be shown by clicking image
     * @param previewUrl image src url that displaying as preview
     */
    fun insertImage(name: String, originalUrl: URL, previewUrl: URL) {
        val json = """{"alt": "$name", "title": "$name", "src": "$previewUrl", "data-original-url": "$originalUrl"}"""
        webView.insertImage(json)
    }

    /**
     * Set cookie value to webView
     * @param host host to set cookie
     * @param cookie key=value string
     */
    fun setCookie(host: String, cookie: String) {
        val cookieManager = CookieManager.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        } else {
            cookieManager.setAcceptCookie(true)
        }
        cookieManager.setCookie(host, cookie)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        webView.requestFocus(View.FOCUS_DOWN)
        webView.focus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
        return super.onTouchEvent(event)
    }

    private fun setupWebView() {
        webView.loadUrl("file:///android_asset/index.html")
        webView.settings.javaScriptEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.addJavascriptInterface(JSInterface(), "AndroidInterface")
        webView.setWebViewClient(WebViewClient())
        webView.setWebChromeClient(WebChromeClient())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    private inner class JSInterface {
        @JavascriptInterface
        fun didMountComponent(): Unit {
            Handler(context.mainLooper).post {
                webView.setHTML(_content)
                webView.setMentions(_rawMentions)
                webView.visibility = View.VISIBLE
                progressView.visibility = View.GONE
            }
        }

        @JavascriptInterface
        fun didChangeInlineStyles(styles: String?): Unit {
            Handler(context.mainLooper).post {
                inlineStyles = styles
                        ?.split(",")
                        ?.map { InlineStyle.from(it) }
                        ?.filterNotNull()
                        ?: listOf()
            }
        }

        @JavascriptInterface
        fun didChangeBlockType(type: String?): Unit {
            Handler(context.mainLooper).post {
                blockStyle = type
                        ?.let { BlockStyle.from(it) }
                        ?: BlockStyle.Unstyled
            }
        }

        @JavascriptInterface
        fun didChangeContent(content: String?): Unit {
            Handler(context.mainLooper).post {
                _content = content ?: ""
            }
        }
    }
}

class InputWebView(context: Context, attr: AttributeSet?) : WebView(context, attr) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        return connection?.let { EditTextInputConnection(it, true) }
    }

    inner class EditTextInputConnection(
            target: InputConnection?, mutable: Boolean
    ) : InputConnectionWrapper(target, mutable) {
        override fun closeConnection() {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this@InputWebView, 0)
            super.closeConnection()
        }
    }
}
