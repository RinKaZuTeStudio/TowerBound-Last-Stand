package com.towerbound.laststand.ui

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * Base class for full-screen game screens (splash, menus, results...).
 * Provides the standard dark gradient backdrop and show/hide animation hooks.
 */
abstract class Screen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onShown: (() -> Unit)? = null
    var onHidden: (() -> Unit)? = null

    protected fun applyBackdrop(vararg colors: Int) {
        if (colors.size < 2) {
            setBackgroundColor(UiKit.cBgDark)
            return
        }
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            colors
        )
    }

    open fun onEnter() {}
    open fun onExit() {}

    /** Screens returning true consume the Android back key. */
    open fun onBack(): Boolean = false
}

/**
 * Stack-based screen container wired to the activity's screen layer.
 * The top screen gets input; screens below are disabled to avoid dead touches.
 */
class ScreenManager(private val container: FrameLayout) {

    private val stack = mutableListOf<Screen>()

    val current: Screen? get() = stack.lastOrNull()
    val depth: Int get() = stack.size

    fun push(screen: Screen, animated: Boolean = true) {
        disableInputBelow()
        container.addView(screen)
        if (screen.parent === container) {
            stack.add(screen)
        }
        if (animated) {
            screen.alpha = 0f
            screen.scaleX = 1.06f
            screen.scaleY = 1.06f
            val fade = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    screen.alpha = it.animatedValue as Float
                    val s = 1.06f - 0.06f * (it.animatedValue as Float)
                    screen.scaleX = s
                    screen.scaleY = s
                }
            }
            AnimatorSet().apply { play(fade); start() }
        }
        screen.onEnter()
        screen.onShown?.invoke()
    }

    fun pop(animated: Boolean = true): Screen? {
        val screen = stack.removeLastOrNull() ?: return null
        val finish = {
            screen.onExit()
            container.removeView(screen)
            screen.onHidden?.invoke()
            enableTopInput()
        }
        if (animated) {
            val fade = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 160
                addUpdateListener { screen.alpha = it.animatedValue as Float }
            }
            AnimatorSet().apply {
                play(fade)
                doOnEndCompat(finish)
                start()
            }
        } else finish()
        return screen
    }

    fun replace(screen: Screen, animated: Boolean = true) {
        while (stack.isNotEmpty()) pop(false)
        push(screen, animated)
    }

    /** Remove a specific screen if present. */
    fun remove(screen: Screen): Boolean {
        if (!stack.contains(screen)) return false
        stack.remove(screen)
        container.removeView(screen)
        screen.onExit()
        enableTopInput()
        return true
    }

    fun clear() {
        while (stack.isNotEmpty()) pop(false)
    }

    /** Route the Android back key through the top screen. */
    fun onBack(): Boolean {
        val top = current ?: return false
        if (top.onBack()) return true
        if (stack.size > 1) {
            pop()
            return true
        }
        return false
    }

    private fun disableInputBelow() {
        for (s in stack) s.isClickable = false
    }

    private fun enableTopInput() {
        for (s in stack) s.isClickable = true
        current?.isClickable = true
    }

    private fun AnimatorSet.doOnEndCompat(action: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(a: android.animation.Animator) {}
            override fun onAnimationCancel(a: android.animation.Animator) {}
            override fun onAnimationRepeat(a: android.animation.Animator) {}
            override fun onAnimationEnd(a: android.animation.Animator) = action()
        })
    }
}
