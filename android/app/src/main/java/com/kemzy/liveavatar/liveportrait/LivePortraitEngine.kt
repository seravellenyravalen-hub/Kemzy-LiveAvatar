package com.kemzy.liveavatar.liveportrait

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import com.google.mlkit.vision.face.Face
import com.kemzy.liveavatar.LiveModelBundle
import com.kemzy.liveavatar.OnnxInferenceEngine
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/** Device-side, file-backed LivePortrait runtime with relative driver motion. */
class LivePortraitEngine(private val bundle: LiveModelBundle) : Closeable {
    private lateinit var appearance: OnnxInferenceEngine
    private lateinit var motion: OnnxInferenceEngine
    private lateinit var stitching: OnnxInferenceEngine
    private lateinit var warping: OnnxInferenceEngine
    private var sourceFeature: FloatTensor? = null
    private var sourceMotion: MotionState? = null
    private var sourceBitmap: Bitmap? = null
    private var driverZero: MotionState? = null
    private var initialized = false

    fun initialize(source: Bitmap, sourceFace: Face) {
        check(bundle.isComplete) { "LivePortrait model bundle is incomplete: ${bundle.missingRoles().joinToString()}" }
        closeSessionsOnly()
        appearance = OnnxInferenceEngine.fromFile(requireNotNull(bundle.appearance), "appearance feature extractor")
        motion = OnnxInferenceEngine.fromFile(requireNotNull(bundle.motion), "motion extractor")
        stitching = OnnxInferenceEngine.fromFile(requireNotNull(bundle.stitching), "stitching")
        warping = OnnxInferenceEngine.fromFile(requireNotNull(bundle.warpingSpade), "warping SPADE")
        val crop = FaceCrop.square(source, sourceFace, 2.3f, 256)
        sourceBitmap?.let { if (!it.isRecycled) it.recycle() }
        sourceBitmap = crop
        sourceFeature = appearance.inferImage(crop)
        sourceMotion = motion.inferMotion(crop)
        driverZero = null
        initialized = true
    }

    fun process(frame: Bitmap, face: Face): Bitmap? {
        if (!initialized) return null
        val driverCrop = FaceCrop.square(frame, face, 2.3f, 256)
        return try {
            val driver = motion.inferMotion(driverCrop)
            val source = requireNotNull(sourceMotion)
            val zero = driverZero ?: driver.also { driverZero = it }
            val kpDriving = relativeKeypoints(source, zero, driver)
            val stitched = stitching.inferStitch(source.transformedKeypoints, kpDriving)
            val generated = warping.inferWarp(requireNotNull(sourceFeature), stitched, source.transformedKeypoints)
            composite(frame, generated, FaceCrop.squareRect(frame, face, 2.3f))
        } finally {
            if (!driverCrop.isRecycled) driverCrop.recycle()
        }
    }

    override fun close() {
        initialized = false
        sourceFeature = null
        sourceMotion = null
        driverZero = null
        sourceBitmap?.let { if (!it.isRecycled) it.recycle() }
        sourceBitmap = null
        closeSessionsOnly()
    }

    private fun closeSessionsOnly() {
        runCatching { if (::appearance.isInitialized) appearance.close() }
        runCatching { if (::motion.isInitialized) motion.close() }
        runCatching { if (::stitching.isInitialized) stitching.close() }
        runCatching { if (::warping.isInitialized) warping.close() }
    }

    private fun composite(frame: Bitmap, generated: Bitmap, target: RectF): Bitmap {
        val out = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val pad = max(8f, target.width() * 0.04f)
        val mask = RectF(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad)
        canvas.save(); canvas.clipRect(mask); canvas.drawBitmap(generated, null, mask, paint); canvas.restore()
        if (!generated.isRecycled) generated.recycle()
        return out
    }
}

data class FloatTensor(val values: FloatArray, val shape: LongArray)
data class MotionState(
    val pitch: Float,
    val yaw: Float,
    val roll: Float,
    val translation: FloatArray,
    val expression: FloatArray,
    val scale: Float,
    val keypoints: FloatArray,
    val rotation: FloatArray,
    val transformedKeypoints: FloatArray
)

private fun relativeKeypoints(source: MotionState, zero: MotionState, driver: MotionState): FloatArray {
    val rd0t = transpose3(zero.rotation)
    val rNew = mul3(mul3(driver.rotation, rd0t), source.rotation)
    val count = source.keypoints.size / 3
    val out = FloatArray(source.keypoints.size)
    for (i in 0 until count) {
        val base = i * 3
        val x = source.keypoints[base]; val y = source.keypoints[base + 1]; val z = source.keypoints[base + 2]
        val dx = x * rNew[0] + y * rNew[3] + z * rNew[6]
        val dy = x * rNew[1] + y * rNew[4] + z * rNew[7]
        val dz = x * rNew[2] + y * rNew[5] + z * rNew[8]
        val expScale = driver.scale / zero.scale.coerceAtLeast(1e-6f)
        val e0 = source.expression[base]; val e1 = source.expression[base + 1]; val e2 = source.expression[base + 2]
        val d0 = driver.expression.getOrElse(base) { 0f } - zero.expression.getOrElse(base) { 0f }
        val d1 = driver.expression.getOrElse(base + 1) { 0f } - zero.expression.getOrElse(base + 1) { 0f }
        val d2 = driver.expression.getOrElse(base + 2) { 0f } - zero.expression.getOrElse(base + 2) { 0f }
        out[base] = expScale * (dx + e0 + d0) + source.translation[0] + driver.translation[0] - zero.translation[0]
        out[base + 1] = expScale * (dy + e1 + d1) + source.translation[1] + driver.translation[1] - zero.translation[1]
        out[base + 2] = expScale * (dz + e2 + d2)
    }
    return out
}

private fun OnnxInferenceEngine.inferImage(bitmap: Bitmap): FloatTensor {
    val input = bitmap.toNchwFloat()
    return runTensor(input.values, input.shape).first()
}

private fun OnnxInferenceEngine.inferMotion(bitmap: Bitmap): MotionState {
    val result = runTensor(bitmap.toNchwFloat().values, longArrayOf(1, 3, 256, 256))
    require(result.size >= 7) { "Motion extractor returned ${result.size} outputs; expected at least 7." }
    val pitch = headpose(result[0].values); val yaw = headpose(result[1].values); val roll = headpose(result[2].values)
    val rotation = rotationMatrix(pitch, yaw, roll)
    val kp = result[6].values.reshapeKeypoints()
    val exp = result[4].values.copyOf()
    val transformed = transformKeypoints(pitch, yaw, roll, result[3].values, exp, result[5].values.first(), kp)
    return MotionState(pitch, yaw, roll, result[3].values.copyOf(), exp, result[5].values.first(), kp, rotation, transformed)
}

private fun transformKeypoints(pitch: Float, yaw: Float, roll: Float, t: FloatArray, exp: FloatArray, scale: Float, kp: FloatArray): FloatArray {
    val r = rotationMatrix(pitch, yaw, roll); val out = FloatArray(kp.size); val count = kp.size / 3
    for (i in 0 until count) {
        val b = i * 3; val x = kp[b]; val y = kp[b + 1]; val z = kp[b + 2]
        out[b] = scale * (x * r[0] + y * r[3] + z * r[6] + exp.getOrElse(b) { 0f }) + t.getOrElse(0) { 0f }
        out[b + 1] = scale * (x * r[1] + y * r[4] + z * r[7] + exp.getOrElse(b + 1) { 0f }) + t.getOrElse(1) { 0f }
        out[b + 2] = scale * (x * r[2] + y * r[5] + z * r[8] + exp.getOrElse(b + 2) { 0f })
    }
    return out
}

private fun rotationMatrix(pitch: Float, yaw: Float, roll: Float): FloatArray {
    val p = Math.toRadians(pitch.toDouble()); val y = Math.toRadians(yaw.toDouble()); val r = Math.toRadians(roll.toDouble())
    val rx = floatArrayOf(1f,0f,0f,0f,cos(p).toFloat(),(-sin(p)).toFloat(),0f,sin(p).toFloat(),cos(p).toFloat())
    val ry = floatArrayOf(cos(y).toFloat(),0f,sin(y).toFloat(),0f,1f,0f,(-sin(y)).toFloat(),0f,cos(y).toFloat())
    val rz = floatArrayOf(cos(r).toFloat(),(-sin(r)).toFloat(),0f,sin(r).toFloat(),cos(r).toFloat(),0f,0f,0f,1f)
    return transpose3(mul3(mul3(rz, ry), rx))
}

private fun mul3(a: FloatArray, b: FloatArray): FloatArray = FloatArray(9) { i ->
    val row = i / 3; val col = i % 3
    a[row*3] * b[col] + a[row*3+1] * b[3+col] + a[row*3+2] * b[6+col]
}
private fun transpose3(a: FloatArray): FloatArray = FloatArray(9) { i -> a[(i % 3) * 3 + i / 3] }

private fun OnnxInferenceEngine.inferStitch(source: FloatArray, driving: FloatArray): FloatArray {
    val joined = FloatArray(source.size + driving.size); source.copyInto(joined); driving.copyInto(joined, source.size)
    val delta = runTensor(joined, longArrayOf(1, joined.size.toLong())).first().values
    val kpCount = source.size / 3; val out = driving.copyOf(); val expressionCount = min(kpCount * 3, delta.size)
    for (i in 0 until expressionCount) out[i] += delta[i]
    if (delta.size >= expressionCount + 2) for (i in 0 until kpCount) { out[i*3] += delta[expressionCount]; out[i*3+1] += delta[expressionCount+1] }
    return out
}

private fun OnnxInferenceEngine.inferWarp(feature: FloatTensor, driving: FloatArray, source: FloatArray): Bitmap {
    val result = runMultiTensor(listOf(feature.values to feature.shape, driving to longArrayOf(1, driving.size.toLong()), source to longArrayOf(1, source.size.toLong()))).first()
    require(result.shape.size == 4 && result.shape[0] == 1L && result.shape[1] == 3L) { "Warping model must return 1x3xHxW, got ${result.shape.contentToString()}" }
    val h = result.shape[2].toInt(); val w = result.shape[3].toInt(); val plane = h * w; val pixels = IntArray(plane)
    for (i in pixels.indices) { val r=(result.values[i].coerceIn(0f,1f)*255).toInt(); val g=(result.values[plane+i].coerceIn(0f,1f)*255).toInt(); val b=(result.values[2*plane+i].coerceIn(0f,1f)*255).toInt(); pixels[i]=-0x1000000 or (r shl 16) or (g shl 8) or b }
    return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
}

private fun OnnxInferenceEngine.runTensor(values: FloatArray, shape: LongArray): List<FloatTensor> = runMultiTensor(listOf(values to shape))
private fun OnnxInferenceEngine.runMultiTensor(inputs: List<Pair<FloatArray, LongArray>>): List<FloatTensor> {
    val tensors = inputs.map { OnnxTensor.createTensor(environment(), it.first, it.second) }
    return try {
        val names=inputNames().toList(); require(names.size>=tensors.size){"Model expects ${names.size} inputs but runtime supplied ${tensors.size}."}
        val feed=names.take(tensors.size).mapIndexed{i,n->n to tensors[i]}.toMap()
        run(feed).use{result->(0 until result.size).map{idx->(result[idx].value as? OnnxTensor)?.flattenTensor() ?: error("ONNX output $idx is not a tensor")}}
    } finally { tensors.forEach{it.close()} }
}
private fun OnnxTensor.flattenTensor(): FloatTensor {
    val shape=info.shape; val out=FloatArray(shape.fold(1L){a,b->a*b}.toInt())
    fun copy(v:Any?,o:Int):Int=when(v){is FloatArray->{v.copyInto(out,o);v.size};is FloatBuffer->{val d=v.duplicate();val n=d.remaining();d.get(out,o,n);n};is Array<*>->{var p=o;v.forEach{p+=copy(it,p)};p-o};is Number->{out[o]=v.toFloat();1};else->error("Unsupported ONNX tensor value ${v?.javaClass}")}
    copy(value,0); return FloatTensor(out,shape)
}
private fun FloatArray.reshapeKeypoints():FloatArray=if(size%3==0)copyOf()else error("Expected keypoint tensor divisible by 3, got $size")
private fun headpose(v:FloatArray):Float{if(v.size!=66)return v.firstOrNull()?:0f;var s=0.0;var w=0.0;for(i in v.indices){val e=kotlin.math.exp(v[i].toDouble());s+=e;w+=e*i};return((w/s)*3-97.5).toFloat()}
private data class Nchw(val values:FloatArray,val shape:LongArray)
private fun Bitmap.toNchwFloat():Nchw{val bmp=if(width==256&&height==256)this else Bitmap.createScaledBitmap(this,256,256,true);val p=IntArray(256*256);bmp.getPixels(p,0,256,0,0,256,256);val o=FloatArray(3*256*256);val pl=256*256;for(i in p.indices){o[i]=((p[i]shr 16 and 255)/255f);o[pl+i]=((p[i]shr 8 and 255)/255f);o[2*pl+i]=((p[i]and 255)/255f)};if(bmp!==this)bmp.recycle();return Nchw(o,longArrayOf(1,3,256,256))}
private object FaceCrop{fun square(s:Bitmap,f:Face?,scale:Float,size:Int):Bitmap{val r=squareRect(s,f,scale);val c=Bitmap.createBitmap(s,r.left.toInt(),r.top.toInt(),max(1,r.width().toInt()),max(1,r.height().toInt()));return Bitmap.createScaledBitmap(c,size,size,true).also{if(it!==c)c.recycle()}};fun squareRect(s:Bitmap,f:Face?,scale:Float):RectF{val b=f?.boundingBox?:Rect(0,0,s.width,s.height);val cx=b.centerX().toFloat();val cy=b.centerY().toFloat();val side=max(b.width(),b.height()).toFloat()*scale;val l=(cx-side/2).coerceIn(0f,max(0f,s.width-side));val t=(cy-side/2).coerceIn(0f,max(0f,s.height-side));val a=min(side,min(s.width-l,s.height-t));return RectF(l,t,l+a,t+a)}}
