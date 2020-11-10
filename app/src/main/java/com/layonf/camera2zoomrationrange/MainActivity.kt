package com.layonf.camera2zoomrationrange

import android.Manifest
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class MainActivity : AppCompatActivity() {

    private val MAX_PREVIEW_WIDTH = 1280
    private val MAX_PREVIEW_HEIGHT = 720
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraDevice: CameraDevice

    private val deviceStateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "camera device opened")
            if (camera != null) {
                cameraDevice = camera
                previewSession()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            TODO("Not yet implemented")
        }

        override fun onError(camera: CameraDevice, p1: Int) {
            Log.d(TAG, "camera device error: $camera")
            //this@PreviewFragment.activity?.finish()
        }
    }

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private val cameraManager by lazy {
        this?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private fun previewSession() {

        val surfaceTexture = previewTextureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
        val surface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(
            Arrays.asList(surface),
            object: CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (session != null) {
                        captureSession = session
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "creating capture session failed!")
                }

            }, null)
    }

    private fun closeCamera() {
        if (this::captureSession.isInitialized)
            captureSession.close()
        if (this::cameraDevice.isInitialized)
            cameraDevice.close()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera2 Kotlin").also {it.start()}
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>) : T? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return when (key) {
            CameraCharacteristics.LENS_FACING -> characteristics.get(key)
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(key)
            else -> throw IllegalArgumentException("Key not recognized")
        }
    }

    private fun cameraId(lens: Int) : String {
        var deviceId = listOf<String>()
        try {
            val cameraIdList = cameraManager.cameraIdList
            deviceId = cameraIdList.filter { lens == cameraCharacteristics(it, CameraCharacteristics.LENS_FACING)}
        } catch (e: CameraAccessException) {
            Log.e(TAG, toString())
        }
        return deviceId[0]
    }

    private fun connectCamera() {
        val deviceId = cameraId(CameraCharacteristics.LENS_FACING_BACK)
        //val deviceId = "1"
        Log.d(TAG, "connectCamera() - deviceId: $deviceId ")
        try {
            cameraManager.openCamera(deviceId, deviceStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            Log.e(TAG, "Open camera device interrupted while opened")
        }

    }

    private val surfaceListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(p0: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureAvailable width: $width height: $height")
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged width: $width height: $height")
        }

        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(p0: SurfaceTexture) = Unit

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    private fun checkCameraPermission() {
        if(EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
            Log.d(TAG, "apps has camera permission!!")
            connectCamera()
        } else {
            Log.d(TAG, "apps has NO camera permission, so I'll request this :)")
            EasyPermissions.requestPermissions(this,
                getString(R.string.camera_request_rationale),
                REQUEST_CAMERA_PERMISSION,
                Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (previewTextureView.isAvailable)
            openCamera()
        else
            previewTextureView.surfaceTextureListener = surfaceListener

        //apply zoom listener
        btn_zoom.setOnClickListener {
            //try apply zoom here
            Log.d(TAG, "apply zoom")
            val zoom = 4.0F //I can get the max zoom from the cameraId CameraCharacteristics#CONTROL_ZOOM_RATIO_RANGE
            captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun openCamera() {
        checkCameraPermission()
        Log.d(TAG, "openCamera()")
        //exercies
        //TODOone 1: print all cameraId on the three devices
        printAllCameraId()
        //TODO 2: print the Characateristics of this cameras one by one
        //TODO 3: open all cameraID to see the diferences
        //TODO 4: Try apply some zoom



    }



    //print all cameraId
    private fun printAllCameraId() {
        Log.d(TAG, "printAllCameraId():")
        try {
            val cameraIdList = cameraManager.cameraIdList
            for (cameraId in cameraIdList){
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val characteristic = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                //val characteristic2 = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val zoomRationRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                val getPhysicalCameraIds = characteristics.getPhysicalCameraIds()
                Log.d(TAG, "getPhysicalCameraIds: $getPhysicalCameraIds")
                Log.d(TAG, "cameraId: $cameraId SCALER_AVAILABLE_MAX_DIGITAL_ZOOM2 = $characteristic CONTROL_ZOOM_RATIO_RANGE = $zoomRationRange")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    companion object {
        const val TAG = "ZoomTest"
        const val REQUEST_CAMERA_PERMISSION = 100
    }
}