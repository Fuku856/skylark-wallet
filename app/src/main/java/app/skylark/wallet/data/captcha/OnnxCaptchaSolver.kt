package app.skylark.wallet.data.captcha

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxCaptchaSolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : CaptchaSolver {

    private class ModelState(
        val env: OrtEnvironment,
        val session: OrtSession,
        val charset: List<String>,
        val digitIndices: IntArray,
    )

    private val initMutex = Mutex()

    @Volatile
    private var modelState: ModelState? = null

    private suspend fun getOrInitModel(): ModelState {
        modelState?.let { return it }
        return initMutex.withLock {
            modelState?.let { return@withLock it }

            val env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_ASSET_NAME).use { it.readBytes() }
            val session = OrtSession.SessionOptions().use { options ->
                env.createSession(modelBytes, options)
            }

            val jsonString = context.assets.open(CHARSET_ASSET_NAME).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            val charset = Json.decodeFromString<List<String>>(jsonString)
            val digitIndices = charset.mapIndexedNotNull { index, token ->
                if (token.length == 1 && token[0] in '0'..'9') index else null
            }.toIntArray()

            ModelState(
                env = env,
                session = session,
                charset = charset,
                digitIndices = digitIndices,
            ).also { modelState = it }
        }
    }

    override suspend fun solve(imageBytes: ByteArray): CaptchaSolver.SolveResult =
        withContext(Dispatchers.Default) {
            try {
                val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: return@withContext CaptchaSolver.SolveResult.Failure(CaptchaSolver.FailureKind.ImageDecode)

                val state = getOrInitModel()
                val targetHeight = CaptchaPreprocessor.TARGET_HEIGHT
                val targetWidth = CaptchaPreprocessor.targetWidth(originalBitmap.width, originalBitmap.height)
                val scaled = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

                val rawText = try {
                    val totalPixels = targetWidth * targetHeight
                    val pixels = IntArray(totalPixels)
                    scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

                    val inputBuffer = CaptchaPreprocessor.toGrayscaleFloatBuffer(pixels)
                    val shape = longArrayOf(1, 1, targetHeight.toLong(), targetWidth.toLong())

                    OnnxTensor.createTensor(state.env, inputBuffer, shape).use { inputTensor ->
                        val inputName = state.session.inputNames.iterator().next()
                        state.session.run(Collections.singletonMap(inputName, inputTensor)).use { result ->
                            val outputTensor = result.get(0) as OnnxTensor
                            outputTensor.use { tensor ->
                                val outShape = tensor.info.shape
                                val isFirstDimSeq = outShape.size == 3 && outShape[1] == 1L
                                val seqLen = if (isFirstDimSeq) outShape[0].toInt() else outShape[1].toInt()
                                val numClasses = outShape[2].toInt()

                                CaptchaPreprocessor.decodeDigitsCtc(
                                    outBuffer = tensor.floatBuffer,
                                    seqLen = seqLen,
                                    numClasses = numClasses,
                                    digitIndices = state.digitIndices,
                                    charset = state.charset,
                                )
                            }
                        }
                    }
                } finally {
                    if (scaled !== originalBitmap && !scaled.isRecycled) {
                        scaled.recycle()
                    }
                }

                val normalized = CaptchaPreprocessor.normalizeText(rawText)
                if (normalized != null) {
                    CaptchaSolver.SolveResult.Success(digits = normalized, rawText = rawText)
                } else {
                    CaptchaSolver.SolveResult.Failure(
                        kind = CaptchaSolver.FailureKind.NotDigits,
                        rawText = rawText,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CaptchaSolver.SolveResult.Failure(CaptchaSolver.FailureKind.Engine, e.javaClass.name)
            }
        }

    private companion object {
        const val MODEL_ASSET_NAME = "ddddocr.onnx"
        const val CHARSET_ASSET_NAME = "charsets_old.json"
    }
}
