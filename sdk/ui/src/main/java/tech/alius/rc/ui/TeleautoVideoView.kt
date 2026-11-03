package tech.alius.rc.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable

/** UI add-on Surface view. The decoder and transport remain in teleauto-core. */
class TeleautoVideoView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var published: Surface? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    var onSurfaceChanged: ((Surface?) -> Unit)? = null
    private val aspectMatrix = Matrix()
    private val clipPath = Path()
    private val clipRect = RectF()
    private var cornerRadiusPx = 0f
    private var lastGeometry = intArrayOf(-1, -1, -1, -1)
    private val texture = TextureView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                Surface(st).also { published = it; onSurfaceChanged?.invoke(it) }
                applyAspect(force = true)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = applyAspect(force = true)
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                onSurfaceChanged?.invoke(null)
                published?.release()
                published = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = applyAspect(force = false)
        }
    }
    init { background = ColorDrawable(Color.BLACK); addView(texture) }
    val publishedSurface: Surface? get() = published
    fun submitSourceSize(width: Int, height: Int) {
        if (sourceWidth == width && sourceHeight == height) return
        sourceWidth = width.coerceAtLeast(0)
        sourceHeight = height.coerceAtLeast(0)
        applyAspect(force = true)
    }
    /** Clips the actual TextureView output without recreating its decoder Surface. */
    fun setCornerRadius(radiusPx: Float) {
        cornerRadiusPx = radiusPx.coerceAtLeast(0f)
        clipToOutline = false
        background = if (cornerRadiusPx == 0f) {
            ColorDrawable(Color.BLACK)
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                cornerRadius = cornerRadiusPx
            }
        }
        rebuildClipPath()
        invalidate()
    }
    fun setExpandedPresentation(expanded: Boolean) {
        setCornerRadius(
            if (expanded) 0f else TeleautoUiTokens.FIGMA_VIDEO_RADIUS *
                resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context),
        )
    }
    fun captureBitmap(): Bitmap? {
        if (!texture.isAvailable || width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return texture.getBitmap(bitmap) ?: run { bitmap.recycle(); null }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildClipPath()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (cornerRadiusPx <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    private fun rebuildClipPath() {
        clipPath.reset()
        if (cornerRadiusPx <= 0f || width <= 0 || height <= 0) return
        clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.addRoundRect(clipRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
    }
    private fun applyAspect(force: Boolean) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val geometry = intArrayOf(width, height, sourceWidth, sourceHeight)
        if (!force && geometry.contentEquals(lastGeometry)) return
        lastGeometry = geometry
        val vw = width.toFloat().coerceAtLeast(1f); val vh = height.toFloat().coerceAtLeast(1f)
        val viewAspect = vw / vh; val sourceAspect = sourceWidth.toFloat() / sourceHeight
        // Fit-center: preserve every source pixel and use the black view background for letterbox.
        val sx = if (sourceAspect < viewAspect) sourceAspect / viewAspect else 1f
        val sy = if (sourceAspect > viewAspect) viewAspect / sourceAspect else 1f
        aspectMatrix.reset(); aspectMatrix.setScale(sx, sy, vw / 2f, vh / 2f); texture.setTransform(aspectMatrix)
    }
}
