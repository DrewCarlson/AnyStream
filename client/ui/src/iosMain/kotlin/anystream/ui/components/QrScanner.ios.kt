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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation.UIDeviceOrientationLandscapeLeft
import platform.UIKit.UIDeviceOrientation.UIDeviceOrientationLandscapeRight
import platform.UIKit.UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

private enum class PermissionState {
    UNSET, GRANTED, DENIED;
}

@Composable
internal actual fun QrScannerNative(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val permissionState by produceState<PermissionState>(PermissionState.UNSET) {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> value = PermissionState.GRANTED
            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted,
                -> value = PermissionState.DENIED

            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    value = if (granted) PermissionState.GRANTED else PermissionState.DENIED
                }
            }
        }
    }
    when (permissionState) {
        PermissionState.GRANTED -> {
            val camera: AVCaptureDevice? = remember {
                AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            }

            camera?.let {
                UIKitScannerView(
                    camera = it,
                    onDataScanned = onScanned
                )
            }
        }

        PermissionState.DENIED -> {
            PermissionDeniedDialog(onClose = onClose)
        }

        PermissionState.UNSET -> {
            // waiting for permission result
        }
    }
}

@Composable
private fun PermissionDeniedDialog(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Camera permission denied")
        /*
NSURL.URLWithString(UIApplicationOpenSettingsURLString)
?.let {
    UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>()) { success ->
        if (!success) {
            // todo: display feedback when settings co
        }
    }
}
         */
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun UIKitScannerView(
    camera: AVCaptureDevice,
    onDataScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraScannerController = remember {
        CameraScannerController(
            camera = camera,
            onDataScanned = onDataScanned
        )
    }

    LaunchedEffect("configure-auto-focus") {
        if (camera.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
            camera.lockForConfiguration(null)
            camera.setFocusMode(AVCaptureFocusModeContinuousAutoFocus)
            camera.unlockForConfiguration()
        }
    }

    UIKitView(
        factory = {
            object : UIView(cValue { CGRectZero }) {
                init {
                    backgroundColor = UIColor.clearColor
                    cameraScannerController.setup(layer)
                }

                override fun layoutSubviews() {
                    super.layoutSubviews()
                    cameraScannerController.setFrame(bounds)
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        onRelease = { cameraScannerController.dispose() },
        properties = UIKitInteropProperties(
            isInteractive = false,
            isNativeAccessibilityEnabled = false
        )
    )
}

@Suppress("UnusedPrivateClass")
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class CameraScannerController(
    private val camera: AVCaptureDevice,
    private val onDataScanned: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private lateinit var cameraPreview: AVCaptureVideoPreviewLayer
    private lateinit var captureSession: AVCaptureSession

    fun setup(viewLayer: CALayer) {
        check(!::captureSession.isInitialized) {
            "setup(..) cannot be called twice on the same CameraScannerController"
        }
        captureSession = AVCaptureSession()

        val cameraInput = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVCaptureDeviceInput(device = camera, error = error.ptr).also {
                if (error.value != null || !captureSession.canAddInput(it)) {
                    // TODO: display error when failed
                    return
                }
            }
        }

        captureSession.addInput(cameraInput)

        val metadataOutput = AVCaptureMetadataOutput()
        if (captureSession.canAddOutput(metadataOutput)) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
            metadataOutput.setMetadataObjectTypes(metadataOutput.availableMetadataObjectTypes)
        } else {
            // todo: display error when failed.
            return
        }

        cameraPreview = AVCaptureVideoPreviewLayer(session = captureSession).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
            orientation = currentVideoOrientation()
            connection?.setVideoOrientation(orientation)
            frame = viewLayer.bounds
            viewLayer.addSublayer(this)
        }

        scope.launch { captureSession.startRunning() }
    }

    fun setFrame(frame: CValue<CGRect>) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        cameraPreview.setFrame(frame)
        cameraPreview.orientation = currentVideoOrientation()
        cameraPreview.connection?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.videoOrientation = cameraPreview.orientation
            }
        }
        CATransaction.commit()
    }

    fun dispose() {
        if (::captureSession.isInitialized) {
            scope.launch {
                captureSession.dispose()
                scope.cancel()
            }
        } else {
            scope.cancel()
        }
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        scope.launch {
            didOutputMetadataObjects.firstOrNull()
                ?.let { it as? AVMetadataMachineReadableCodeObject }
                ?.takeIf { it.type == AVMetadataObjectTypeQRCode && !it.stringValue.isNullOrBlank() }
                ?.stringValue
                ?.run(onDataScanned)
        }
    }

    /** Remove all session configuration and stop it. */
    private fun AVCaptureSession.dispose() {
        beginConfiguration()
        inputs.filterIsInstance<AVCaptureInput>().forEach(::removeInput)
        outputs.filterIsInstance<AVCaptureOutput>().forEach(::removeOutput)
        commitConfiguration()
        stopRunning()
    }

    private fun currentVideoOrientation(): AVCaptureVideoOrientation {
        return when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
            UIDeviceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            else -> AVCaptureVideoOrientationPortrait
        }
    }
}
