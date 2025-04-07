package anystream.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.makeFromEncoded
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreImage.*
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@Composable
internal actual fun QrCodeImageNative(
    content: String,
    loadingContent: @Composable (Dp) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val qrCodeSizeDp = remember(constraints) { min(maxWidth, maxHeight) }
        val qrCodeImage by produceState<ImageBitmap?>(null, content, constraints) {
            val qrCodeSize = minOf(constraints.maxWidth, constraints.maxHeight)
            value = withContext(Dispatchers.Default) {
                createQRCodeUIImage(
                    content = content,
                    qrCodeSize = qrCodeSize,
                    color = Color.Black,
                    backgroundColor = Color.White
                )?.asImageBitmap()
            }
        }

        when (val currentQrCodeImage = qrCodeImage) {
            null -> loadingContent(qrCodeSizeDp)
            else -> {
                Image(
                    bitmap = currentQrCodeImage,
                    contentDescription = null,
                    modifier = Modifier.size(qrCodeSizeDp)
                )
            }
        }
    }
}

private fun UIImage.asImageBitmap(): ImageBitmap? {
    val imageData = UIImagePNGRepresentation(this) ?: return null
    return Image.makeFromEncoded(imageData).toComposeImageBitmap()
}

@OptIn(BetaInteropApi::class)
private fun createQRCodeUIImage(
    content: String,
    qrCodeSize: Int,
    color: Color,
    backgroundColor: Color,
): UIImage? {
    val dataAsByteArray = content.toByteArray()
    val dataAsNSData = dataAsByteArray.usePinned { pin ->
        NSData.create(
            bytes = pin.addressOf(0),
            length = dataAsByteArray.size.toULong()
        )
    }

    val qrCodeFilter = CIFilter.QRCodeGenerator()
        .apply { setValue(value = dataAsNSData, forKey = "inputMessage") }

    val colorFilter = CIFilter.falseColorFilter()
        .apply {
            setValue(qrCodeFilter.outputImage, "inputImage")
            setValue(
                CIColor(
                    red = color.red.toDouble(),
                    green = color.green.toDouble(),
                    blue = color.blue.toDouble()
                ),
                "inputColor0"
            )
            setValue(
                CIColor(
                    red = backgroundColor.red.toDouble(),
                    green = backgroundColor.green.toDouble(),
                    blue = backgroundColor.blue.toDouble()
                ),
                "inputColor1"
            )
        }

    val qrCodeImage = colorFilter.outputImage ?: return null

    val (scaleX, scaleY) = qrCodeImage.extent.useContents {
        Pair(
            qrCodeSize.toDouble() / size.width,
            qrCodeSize.toDouble() / size.height
        )
    }
    val scaleTransform = CGAffineTransformMakeScale(scaleX, scaleY)
    val transformedImage = qrCodeImage.imageByApplyingTransform(scaleTransform)

    return CIContext()
        .createCGImage(transformedImage, transformedImage.extent)
        .run(UIImage::imageWithCGImage)
}
