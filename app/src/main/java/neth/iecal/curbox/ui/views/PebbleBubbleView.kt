package neth.iecal.curbox.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import neth.iecal.curbox.R

class PebbleBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class PebbleData(
        val color: Int,
        val weight: Float, // relative weight (0-1), largest = 1.0
        val label: String
    )

    private var pebbles: List<PebbleData> = emptyList()
    private var animProgress: Float = 1f

    // Raw white pebble bitmap loaded from assets (black background expected)
    private val pebbleBitmap: Bitmap? by lazy {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.pebble)
        } catch (e: Exception) {
            null
        }
    }

    // SCREEN mode completely drops the black background and mixes overlapping colors beautifully
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)



    private val matrixTransform = Matrix()
    private var animator: ValueAnimator? = null

    // Layout configuration to handle exact offset, scale, and rotation of the organic shape
    private data class PebbleLayout(val offsetX: Float, val offsetY: Float, val scale: Float, val rotation: Float)

    fun setData(data: List<PebbleData>, animate: Boolean = true) {
        pebbles = data
        if (animate) {
            animator?.cancel()
            animProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 800
                interpolator = OvershootInterpolator(1.1f)
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animProgress = 1f
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = (280 * resources.displayMetrics.density).toInt()
        val width = resolveSize(desiredSize, widthMeasureSpec)
        val height = resolveSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = pebbleBitmap ?: return
        if (pebbles.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        val baseRadius = minOf(w, h) / 1.7f
        val bitmapMaxDim = maxOf(bitmap.width, bitmap.height).toFloat()

        // Canvas layer is strictly required to isolate the SCREEN blend mode properly
        val sc = canvas.saveLayer(0f, 0f, w, h, null)

        val count = pebbles.size.coerceAtMost(3)

        // Pre-calculated coordinates to mimic the exact layout of the target reference image
        val layouts = when (count) {
            1 -> arrayOf(PebbleLayout(0f, 0f, 1f, 0f))
            2 -> arrayOf(
                PebbleLayout(-0.15f, 0f, 1.0f, 15f),
                PebbleLayout(0.15f, 0f, 0.9f, -15f)
            )
            else -> arrayOf(
                // 1. Left (Pink) - Largest
                PebbleLayout(-0.12f, 0.02f, 1.15f, 25f),
                // 2. Top Right (Light Blue)
                PebbleLayout(0.18f, -0.15f, 0.90f, 115f),
                // 3. Bottom Right (Dark Blue/Purple)
                PebbleLayout(0.15f, 0.22f, 0.85f, -40f)
            )
        }

        for (i in 0 until count) {
            val pebble = pebbles[i]
            val layout = layouts[i]

            val targetScale = layout.scale * pebble.weight.coerceIn(0.5f, 1f) * animProgress
            val scaleFactor = (baseRadius * 2f * targetScale) / bitmapMaxDim

            val pebbleCx = cx + layout.offsetX * w
            val pebbleCy = cy + layout.offsetY * h

            matrixTransform.reset()
            matrixTransform.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            matrixTransform.postRotate(layout.rotation)
            matrixTransform.postScale(scaleFactor, scaleFactor)
            matrixTransform.postTranslate(pebbleCx, pebbleCy)

            // SRC_IN tints the solid parts of your transparent PNG to the target color
            bitmapPaint.colorFilter = PorterDuffColorFilter(pebble.color, PorterDuff.Mode.SRC_IN)

            // Set opacity to ~85% (215 out of 255) to allow the colors underneath to bleed through,
            // creating that glassy, overlapping Venn-diagram effect.
            // Tweak this value between 180 and 230 to get the exact depth you want.
            bitmapPaint.alpha = 215

            canvas.drawBitmap(bitmap, matrixTransform, bitmapPaint)
        }

        canvas.restoreToCount(sc)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}