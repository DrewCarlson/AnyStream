/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.ui.components

import android.Manifest.permission.CAMERA
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.ImageFormat.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

@Composable
internal actual fun QrScannerNative(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasRequestedPermission by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(checkSelfPermission(context, CAMERA) == PERMISSION_GRANTED)
    }

    val previewView = remember { PreviewView(context) }
    val cameraProvider = remember { ProcessCameraProvider.getInstance(context).get() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            onClose()
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasRequestedPermission && !hasCameraPermission) {
            permissionLauncher.launch(CAMERA)
            hasRequestedPermission = true
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            cameraProvider.unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    context.createQrCodeImageAnalysisUseCase(
                        lifecycleOwner = lifecycleOwner,
                        cameraProvider = cameraProvider,
                        previewView = previewView,
                        onScanned = onScanned
                    )

                    previewView
                }
            )
        }
    }
}

private fun Context.createQrCodeImageAnalysisUseCase(
    lifecycleOwner: LifecycleOwner,
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    onScanned: (String) -> Unit,
) {
    val preview =
        Preview.Builder()
            .build()
            .apply {
                surfaceProvider = previewView.surfaceProvider
            }
    val selector =
        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

    val imageAnalysis =
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

    imageAnalysis.setAnalyzer(
        ContextCompat.getMainExecutor(this),
        QrCodeAnalyzer(
            onScanned = onScanned
        )
    )

    val useCaseGroup =
        UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalysis)
            .build()

    try {
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            selector,
            useCaseGroup
        )
    } catch (e: Throwable) {
        // TODO: display error when camera start failed
    }
}

private class QrCodeAnalyzer(
    private val onScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = QRCodeReader()
    private val supportedFormats =
        listOf(YUV_420_888, YUV_422_888, YUV_444_888)

    override fun analyze(image: ImageProxy) {
        if (!supportedFormats.contains(image.format)) return
        val buffer = image.planes[0].buffer
        val imageBytes = ByteArray(buffer.remaining())
        buffer.get(imageBytes)

        val source = PlanarYUVLuminanceSource(
            imageBytes,
            image.width,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false
        )
        val zxingBinaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = reader.decode(zxingBinaryBitmap)
            onScanned(result.text.orEmpty())
            // TODO: display error when failed to process
        } catch (_: NotFoundException) {
        } catch (_: ChecksumException) {
        } catch (_: FormatException) {
        } finally {
            image.close()
        }
    }
}
