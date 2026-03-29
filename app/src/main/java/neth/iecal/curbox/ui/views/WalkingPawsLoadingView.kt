package neth.iecal.curbox.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.android.material.color.MaterialColors

class WalkingPawsLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    // 4 paws animating in a walking sequence
    private val pawAlphas = FloatArray(4) { 0f }
    private var animator: ValueAnimator? = null
    private val pawCount = 4
    
    init {
        paint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        startAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800 
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                updatePaws(progress)
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
    }

    private fun updatePaws(progress: Float) {
        for (i in 0 until pawCount) {
            val phaseProgress = (progress - (i * 0.25f)) % 1f
            val wrappedProgress = if (phaseProgress < 0f) phaseProgress + 1f else phaseProgress
            
            pawAlphas[i] = when {
                wrappedProgress < 0.1f -> wrappedProgress / 0.1f
                wrappedProgress < 0.3f -> 1f
                wrappedProgress < 0.6f -> 1f - ((wrappedProgress - 0.3f) / 0.3f)
                else -> 0f
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        val startX = w * 0.15f
        val stepX = (w * 0.7f) / (pawCount - 1)
        val cy = h / 2f
        val pawSize = minOf(w, h) * 0.2f // Adjusted size for proportion

        for (i in 0 until pawCount) {
            val alpha = (pawAlphas[i] * 255).toInt()
            if (alpha <= 0) continue
            
            paint.alpha = alpha
            
            val px = startX + i * stepX
            val py = cy + if (i % 2 == 0) pawSize * 0.4f else -pawSize * 0.4f
            
            drawPaw(canvas, px, py, pawSize)
        }
    }

    private fun drawPaw(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // Main pad
        val mw = size * 0.6f
        val mh = size * 0.5f
        canvas.drawOval(cx - mw, cy - mh + size * 0.2f, cx + mw, cy + mh + size * 0.2f, paint)
        
        // 4 Toes
        val tw = size * 0.22f
        val th = size * 0.3f
        
        val toeY1 = cy - size * 0.45f
        val toeY2 = cy - size * 0.15f
        
        val dx1 = size * 0.55f
        val dx2 = size * 0.22f
        
        canvas.save()
        canvas.rotate(-25f, cx - dx1, toeY1)
        canvas.drawOval(cx - dx1 - tw, toeY1 - th, cx - dx1 + tw, toeY1 + th, paint)
        canvas.restore()
        
        canvas.save()
        canvas.rotate(-10f, cx - dx2, toeY2)
        canvas.drawOval(cx - dx2 - tw, toeY2 - th, cx - dx2 + tw, toeY2 + th, paint)
        canvas.restore()
        
        canvas.save()
        canvas.rotate(10f, cx + dx2, toeY2)
        canvas.drawOval(cx + dx2 - tw, toeY2 - th, cx + dx2 + tw, toeY2 + th, paint)
        canvas.restore()
        
        canvas.save()
        canvas.rotate(25f, cx + dx1, toeY1)
        canvas.drawOval(cx + dx1 - tw, toeY1 - th, cx + dx1 + tw, toeY1 + th, paint)
        canvas.restore()
    }
}
