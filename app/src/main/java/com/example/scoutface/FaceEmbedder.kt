package com.example.scoutface

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Wraps the bundled MobileFaceNet TFLite model to turn a cropped face
 * image into a 192-dim, L2-normalized embedding that can be compared
 * across frames/sessions to recognize the same person.
 *
 * Model: MobileFaceNet.tflite (MIT licensed, bundled in assets/).
 * Input: 112x112 RGB, pixels normalized to roughly [-1, 1] via (px - 127.5) / 128.
 * Output: 192-dim float embedding.
 */
class FaceEmbedder(context: Context) {

    companion object {
        private const val MODEL_FILE = "MobileFaceNet.tflite"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 192
    }

    private val interpreter: Interpreter = Interpreter(loadModelFile(context))

    /**
     * Returns a 192-dim L2-normalized embedding for the given face image.
     * The bitmap should already be cropped tightly around the face; it
     * will be resized to 112x112 before inference.
     */
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val resized = if (faceBitmap.width == INPUT_SIZE && faceBitmap.height == INPUT_SIZE) {
            faceBitmap
        } else {
            Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        }

        val input = bitmapToInputBuffer(resized)
        val output = Array(2) { FloatArray(EMBEDDING_SIZE) }

        interpreter.run(input, output)

        return l2Normalize(output[0])
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        // Model was compiled with batch_size=2; allocate 2× and fill both slots
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3 * 2)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        repeat(2) {
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                buffer.putFloat((r - 127.5f) / 128f)
                buffer.putFloat((g - 127.5f) / 128f)
                buffer.putFloat((b - 127.5f) / 128f)
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sumSquares = 0f
        for (v in embedding) sumSquares += v * v
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm == 0f) return embedding
        return FloatArray(embedding.size) { i -> embedding[i] / norm }
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        val assetFd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val channel = inputStream.channel
        val startOffset = assetFd.startOffset
        val declaredLength = assetFd.declaredLength
        return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter.close()
    }
}
