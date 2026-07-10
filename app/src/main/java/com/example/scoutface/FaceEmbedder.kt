package com.example.scoutface

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

// MobileFaceNet trained with InsightFace/ArcFace loss (512-dim embeddings).
// Input: 112x112 RGB, (px - 127.5) / 128 per channel. Output: [1, 512] float.
class FaceEmbedder(context: Context) {

    companion object {
        private const val MODEL_FILE = "MobileFaceNet.tflite"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 512
    }

    private val interpreter: Interpreter = Interpreter(loadModelFile(context))

    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val resized = if (faceBitmap.width == INPUT_SIZE && faceBitmap.height == INPUT_SIZE) {
            faceBitmap
        } else {
            Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        }

        val input = bitmapToInputBuffer(resized)
        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }

        interpreter.run(input, output)

        return l2Normalize(output[0])
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat((r - 127.5f) / 128f)
            buffer.putFloat((g - 127.5f) / 128f)
            buffer.putFloat((b - 127.5f) / 128f)
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
