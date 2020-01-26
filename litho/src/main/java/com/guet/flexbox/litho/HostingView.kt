package com.guet.flexbox.litho

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.MainThread
import com.facebook.litho.*
import com.guet.flexbox.ConcurrentUtils
import com.guet.flexbox.ForwardContext
import com.guet.flexbox.HttpClient
import com.guet.flexbox.TemplateNode
import com.guet.flexbox.el.PropsELContext

class HostingView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : LithoView(context, attrs) {

    internal val pageContext = HostContextImpl(this)

    internal var page: Page? = null

    internal var httpClient: HttpClient? = null

    internal var pageEventListener: PageEventListener? = null

    init {
        componentTree = ComponentTree.create(componentContext)
                .isReconciliationEnabled(false)
                .layoutThreadHandler(LayoutThreadHandler)
                .build()
    }

    fun setPageEventListener(pageEventListener: PageEventListener?) {
        this.pageEventListener = pageEventListener
    }

    fun setHttpClient(httpClient: HttpClient?) {
        this.httpClient = httpClient
    }

    override fun setLayoutParams(params: LayoutParams?) {
        check(params?.width != LayoutParams.WRAP_CONTENT) { "width forbid wrap_content" }
        super.setLayoutParams(params)
    }

    @MainThread
    fun setContentAsync(page: Page) {
        ThreadUtils.assertMainThread()
        this.page = page
        page.forward.target = pageContext
        componentTree?.setRootAndSizeSpecAsync(page.display,
                SizeSpec.makeSizeSpec(measuredWidth, SizeSpec.EXACTLY),
                when (layoutParams?.height) {
                    LayoutParams.WRAP_CONTENT -> SizeSpec.makeSizeSpec(0, SizeSpec.UNSPECIFIED)
                    else -> SizeSpec.makeSizeSpec(measuredHeight, SizeSpec.EXACTLY)
                })
    }

    @MainThread
    fun setContentAsync(node: TemplateNode, data: Any?) {
        ThreadUtils.assertMainThread()
        val elContext = PropsELContext(data)
        val c = componentContext
        ConcurrentUtils.runOnAsyncThread {
            val component = LithoBuildTool.build(
                    node,
                    pageContext,
                    elContext,
                    c
            ) as Component
            val page = Page(node, component,
                    ForwardContext().apply {
                        target = pageContext
                    })
            post {
                this.page = page
                val tree = componentTree ?: return@post
                tree.setRootAndSizeSpecAsync(
                        component,
                        SizeSpec.makeSizeSpec(measuredWidth, SizeSpec.EXACTLY),
                        when (layoutParams?.width ?: 0) {
                            LayoutParams.WRAP_CONTENT ->
                                SizeSpec.makeSizeSpec(
                                        0,
                                        SizeSpec.UNSPECIFIED
                                )
                            else ->
                                SizeSpec.makeSizeSpec(
                                        measuredHeight,
                                        SizeSpec.EXACTLY
                                )
                        })
            }
        }
    }

    interface PageEventListener {
        fun onEventDispatched(
                h: HostingView,
                source: View?,
                values: Array<out Any?>?
        )
    }

}