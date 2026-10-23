package app.skylark.wallet.data.captcha

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MlKitCaptchaSolver @Inject constructor() : CaptchaSolver {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun solve(imageBytes: ByteArray): CaptchaSolver.SolveResult =
        withContext(Dispatchers.Default) {
            try {
                val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: return@withContext CaptchaSolver.SolveResult.Failure("Bitmap decode failed")

                // Otsuの二値化により純白背景・純黒文字に前処理
                val binarizedBitmap = CaptchaPreprocessor.binarize(originalBitmap)
                val inputImage = InputImage.fromBitmap(binarizedBitmap, 0)

                val visionText = suspendCancellableCoroutine { continuation ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { text ->
                            if (continuation.isActive) continuation.resume(text)
                        }
                        .addOnFailureListener { e ->
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                }

                val normalized = CaptchaPreprocessor.normalizeText(visionText.text)
                if (normalized != null) {
                    CaptchaSolver.SolveResult.Success(normalized)
                } else {
                    CaptchaSolver.SolveResult.Failure("Unmatched digits from text: '${visionText.text}'")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CaptchaSolver.SolveResult.Failure(e.message)
            }
        }
}
