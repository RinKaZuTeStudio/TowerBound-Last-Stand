package com.towerbound.laststand.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Background loading pipeline with progress reporting. Each task runs off the
 * main thread; progress + status text are posted to the main thread so the
 * loading screen never stutters. Tasks can be weighted (asset generation
 * dominates, for example).
 *
 * Used by the BOOT->LOADING flow and by LOADING_GAME (run preparation).
 */
class LoadingPipeline(private val label: String) {

    class Task(
        val name: String,
        val weight: Float,
        val action: (LoadingPipeline) -> Unit
    )

    interface Listener {
        fun onProgress(fraction: Float, statusText: String)
        fun onComplete()
        fun onTaskError(taskName: String, error: Throwable) // continue with fallback
    }

    private val tasks = mutableListOf<Task>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val done = AtomicInteger(0)
    @Volatile private var cancelled = false

    fun task(name: String, weight: Float = 1f, action: (LoadingPipeline) -> Unit): LoadingPipeline {
        tasks.add(Task(name, weight, action))
        return this
    }

    val totalWeight: Float get() = tasks.sumOf { it.weight.toDouble() }.toFloat()

    /** Run all tasks on a background thread. */
    fun start(listener: Listener, errorHandler: ((Throwable) -> Unit)? = null) {
        val thread = Thread({
            var completedWeight = 0f
            val total = if (totalWeight <= 0f) 1f else totalWeight
            for (t in tasks) {
                if (cancelled) return@Thread
                try {
                    val startedAt = System.currentTimeMillis()
                    t.action(this)
                    GameLog.d("Pipeline", "'${t.name}' finished in ${System.currentTimeMillis() - startedAt} ms")
                } catch (e: Throwable) {
                    GameLog.e("Pipeline", "task '${t.name}' failed", e)
                    mainHandler.post { listener.onTaskError(t.name, e) }
                }
                completedWeight += t.weight
                val frac = (completedWeight / total).coerceIn(0f, 1f)
                val text = t.name
                mainHandler.post { listener.onProgress(frac, text) }
            }
            mainHandler.post { listener.onComplete() }
        }, "TB-Load-$label")
        thread.priority = Thread.MAX_PRIORITY - 1
        thread.isDaemon = true
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
            GameLog.e("Pipeline", "pipeline '$label' crashed", e)
            errorHandler?.invoke(e)
        }
        thread.start()
    }

    fun cancel() { cancelled = true }
}
