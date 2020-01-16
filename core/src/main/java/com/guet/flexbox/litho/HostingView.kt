package com.guet.flexbox.litho

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import androidx.annotation.MainThread
import com.facebook.litho.*
import com.facebook.litho.config.ComponentsConfiguration
import com.guet.flexbox.PageContext
import com.guet.flexbox.TemplateNode
import com.guet.flexbox.el.PropsELContext
import com.guet.flexbox.transaction.HttpTransaction
import com.guet.flexbox.transaction.RefreshTransaction

class HostingView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : LithoView(context, attrs) {

    private val pageContext = HostingContext()

    private inner class HostingContext : PageContext() {

        override fun send(key: String, vararg data: Any?) {
            _eventListener?.handleEvent(this@HostingView, key, data)
        }

        override fun http(): HttpTransaction? {
            return HostingHttpTransaction()
        }

        override fun refresh(): RefreshTransaction? {
            return HostingRefreshTransaction()
        }
    }

    private inner class HostingRefreshTransaction : RefreshTransaction() {
        override fun commit(): (PropsELContext) -> Unit {
            return { elContext ->
                val node = template
                if (node != null) {
                    actions.forEach {
                        it.invoke(elContext)
                    }
                    setContentAsync(node, elContext)
                }
            }
        }
    }

    private inner class HostingHttpTransaction : HttpTransaction() {
        override fun commit(): (PropsELContext) -> Unit {
            return { elContext ->
                val node = template
                val http = _httpClient
                val success = success
                val error = error
                if (node != null && http != null) {
                    http.enqueue(
                            url!!,
                            method!!,
                            prams,
                            {
                                if (success != null) {
                                    post {
                                        success.invoke(elContext)
                                    }
                                }
                            },
                            {
                                if (error != null) {
                                    post {
                                        error.invoke(elContext)
                                    }
                                }
                            }
                    )
                }
            }
        }
    }


    private var template: TemplateNode? = null

    private var _httpClient: HttpClient? = null

    private var _onDirtyMountListener: OnDirtyMountListener? = null

    private var _eventListener: EventListener? = null

    init {
        componentTree = ComponentTree.create(componentContext)
                .isReconciliationEnabled(false)
                .layoutThreadHandler(Asynchronous)
                .build()
        super.setOnDirtyMountListener { view ->
            this.performIncrementalMount(
                    Rect(0, 0, measuredWidth, measuredHeight), false
            )
            _onDirtyMountListener?.onDirtyMount(view)
        }
    }


    override fun performLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.performLayout(changed, left, top, right, bottom)
        this.performIncrementalMount(
                Rect(0, 0, measuredWidth, measuredHeight), false
        )
    }


    fun setEventHandler(eventListener: EventListener?) {
        _eventListener = eventListener
    }

    fun setHttpClient(httpClient: HttpClient?) {
        _httpClient = httpClient
    }

    override fun setOnDirtyMountListener(onDirtyMountListener: OnDirtyMountListener?) {
        _onDirtyMountListener = onDirtyMountListener
    }

    override fun setLayoutParams(params: LayoutParams?) {
        check(params?.width != LayoutParams.WRAP_CONTENT) { "width forbid wrap_content" }
        super.setLayoutParams(params)
    }

    @MainThread
    fun setContentAsync(page: PreloadPage) {
        ThreadUtils.assertMainThread()
        template = page.template
        page.eventBridge.target = pageContext
        componentTree?.setRootAndSizeSpecAsync(page.component,
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
        template = node
        setContentAsync(node, elContext)
    }

    private fun setContentAsync(
            node: TemplateNode,
            elContext: PropsELContext
    ) {
        val tree = componentTree
        if (tree != null) {
            val c = componentContext
            val height = layoutParams?.width ?: 0
            val mH = measuredHeight
            val mW = measuredWidth
            Asynchronous.post {
                val component = LithoBuildUtils.bindNode(
                        node,
                        pageContext,
                        elContext,
                        true,
                        c
                ).single()
                tree.setRootAndSizeSpec(
                        component.widget as Component,
                        SizeSpec.makeSizeSpec(mW, SizeSpec.EXACTLY),
                        when (height) {
                            LayoutParams.WRAP_CONTENT ->
                                SizeSpec.makeSizeSpec(
                                        0,
                                        SizeSpec.UNSPECIFIED
                                )
                            else ->
                                SizeSpec.makeSizeSpec(
                                        mH,
                                        SizeSpec.EXACTLY
                                )
                        })
            }
        }
    }

    interface EventListener {
        fun handleEvent(host: HostingView, key: String, value: Array<out Any?>)
    }

    interface HttpClient {
        fun enqueue(
                url: String,
                method: String,
                prams: Map<String, String>,
                success: (Any) -> Unit,
                error: () -> Unit
        )
    }

    private companion object Asynchronous : Handler({
        val thread = HandlerThread("WorkerThread")
        thread.start()
        thread.looper
    }()), LithoHandler {

        init {
            ComponentsConfiguration.incrementalMountWhenNotVisible = true
        }

        override fun post(runnable: Runnable, tag: String?) {
            post(runnable)
        }

        override fun postAtFront(runnable: Runnable, tag: String?) {
            postAtFrontOfQueue(runnable)
        }

        override fun isTracing(): Boolean = true

        override fun remove(runnable: Runnable) {
            removeCallbacks(runnable)
        }
    }

}