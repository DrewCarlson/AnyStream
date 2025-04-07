package anystream.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal actual fun QrCodeImageNative(
    content: String,
    loadingContent: @Composable (Dp) -> Unit,
    modifier: Modifier,
) {
    val matrix by produceState<ByteMatrix?>(null, content) {
        value = withContext(Dispatchers.Default) {
            Encoder.encode(
                content,
                ErrorCorrectionLevel.H,
                mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 16,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
                )
            ).matrix
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.White)
    ) {
        val qrCodeSizeDp = remember(constraints) { min(maxWidth, maxHeight) }

        when (val currentMatrix = matrix) {
            null -> loadingContent(qrCodeSizeDp)

            else -> {
                Canvas(
                    modifier = Modifier
                        .size(qrCodeSizeDp),
                    contentDescription = "QR Code image to login using another device."
                ) {
                    val cellSize = kotlin.math.min(
                        size.width / currentMatrix.width,
                        size.height / currentMatrix.height
                    ).toInt()

                    fun offsetFor(start: Float, end: Int): Float {
                        return ((start - end * cellSize) / 2f).roundToInt().toFloat()
                    }
                    val offsetX = offsetFor(size.width, currentMatrix.width)
                    val offsetY = offsetFor(size.height, currentMatrix.width)

                    for (y in 0 until currentMatrix.height) {
                        for (x in 0 until currentMatrix.width) {
                            if (currentMatrix.get(x, y).toInt() == 1) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(
                                        offsetX + x * cellSize,
                                        offsetY + y * cellSize,
                                    ),
                                    size = Size(cellSize.toFloat(), cellSize.toFloat())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
