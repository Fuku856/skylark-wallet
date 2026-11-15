package app.skylark.wallet.data.captcha

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
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

            // モデル（約13MB）と文字表はアセットから読むので IO で行う
            withContext(Dispatchers.IO) {
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
                )
            }.also { modelState = it }
        }
    }

    override suspend fun solve(imageBytes: ByteArray): CaptchaSolver.SolveResult =
        withContext(Dispatchers.Default) {
            try {
                val rawText = recognize(imageBytes)
                    ?: return@withContext CaptchaSolver.SolveResult.Failure(CaptchaSolver.FailureKind.ImageDecode)

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
                engineFailure(e)
            } catch (e: LinkageError) {
                // ネイティブライブラリを読み込めない端末では UnsatisfiedLinkError（2回目以降は NoClassDefFoundError）になる。
                // Error なので呼び出し側では受けられずアプリが落ちるため、ここで失敗として返し、手入力に戻れるようにする
                engineFailure(e)
            } catch (e: OutOfMemoryError) {
                engineFailure(e)
            }
        }

    /** 画像を読み取って数字の候補を返す。画像を読み込めなければ null。 */
    private suspend fun recognize(imageBytes: ByteArray): String? {
        // モデルの読み込みで失敗しても画像を解放し忘れないよう、先にモデルを用意する
        val state = getOrInitModel()
        val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        val targetHeight = CaptchaPreprocessor.TARGET_HEIGHT
        val targetWidth = CaptchaPreprocessor.targetWidth(original.width, original.height)
        val scaled = original.scale(targetWidth, targetHeight)

        try {
            val pixels = IntArray(targetWidth * targetHeight)
            scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

            val inputBuffer = CaptchaPreprocessor.toGrayscaleFloatBuffer(pixels)
            val shape = longArrayOf(1, 1, targetHeight.toLong(), targetWidth.toLong())

            return OnnxTensor.createTensor(state.env, inputBuffer, shape).use { inputTensor ->
                val inputName = state.session.inputNames.iterator().next()
                state.session.run(Collections.singletonMap(inputName, inputTensor)).use { result ->
                    (result.get(0) as OnnxTensor).use { tensor ->
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
            if (scaled !== original) scaled.recycle()
            original.recycle()
        }
    }

    private fun engineFailure(e: Throwable) =
        CaptchaSolver.SolveResult.Failure(CaptchaSolver.FailureKind.Engine, e.javaClass.name)

    private companion object {
        const val MODEL_ASSET_NAME = "ddddocr.onnx"
        const val CHARSET_ASSET_NAME = "charsets_old.json"
    }
}
