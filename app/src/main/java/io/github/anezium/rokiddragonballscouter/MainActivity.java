package io.github.anezium.rokiddragonballscouter;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.SizeF;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private static final long LOST_TARGET_GRACE_MS = 450L;
    private static final long TARGET_PREDICTION_GRACE_MS = 900L;
    private static final long FRESH_TRACK_WINDOW_MS = 180L;
    private static final long POWER_RESEED_MS = 7_000L;
    private static final long SCAN_SESSION_TIMEOUT_MS = 20_000L;
    private static final long SENSOR_RENDER_INTERVAL_MS = 33L;

    private static final float DEFAULT_SENSOR_HORIZONTAL_FOV_RAD = (float) Math.toRadians(68.0);
    private static final float DEFAULT_SENSOR_VERTICAL_FOV_RAD = (float) Math.toRadians(52.0);
    private static final float DISPLAY_YAW_GAIN = 1.12f;
    private static final float DISPLAY_PITCH_GAIN = 1.18f;
    private static final float DISPLAY_MARGIN_RATIO = 0.10f;

    private PreviewView previewView;
    private ScouterOverlayView hudView;

    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    private SensorManager sensorManager;
    private Sensor gameRotationVectorSensor;

    private final AtomicBoolean analyzerBusy = new AtomicBoolean(false);
    private final Handler sessionHandler = new Handler(Looper.getMainLooper());
    private volatile boolean scanSessionActive = false;
    private final Runnable scanTimeoutRunnable = () -> {
        if (scanSessionActive) {
            stopScanSession();
        }
    };

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private final SensorEventListener headPoseListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_GAME_ROTATION_VECTOR) {
                return;
            }

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            currentHeadYawAbsRad = orientationAngles[0];
            currentHeadPitchAbsRad = orientationAngles[1];
            hasHeadPose = true;

            if (scanSessionActive && !hasHeadReference) {
                captureHeadReference();
            }

            long now = SystemClock.elapsedRealtime();
            if (scanSessionActive
                    && canProjectTarget(now)
                    && now - lastSensorRenderAt >= SENSOR_RENDER_INTERVAL_MS) {
                renderTrackedHud(now);
                lastSensorRenderAt = now;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private Integer currentTargetId = null;
    private int currentPowerLevel = PowerLevelGenerator.next();
    private long lastFaceSeenAt = 0L;
    private long lastPowerSeedAt = 0L;
    private long lastSensorRenderAt = 0L;

    private boolean pendingScanRequest = false;
    private String activeLensLabel = "AUTO CAM";

    private boolean hasHeadPose = false;
    private boolean hasHeadReference = false;
    private float currentHeadYawAbsRad = 0f;
    private float currentHeadPitchAbsRad = 0f;
    private float headReferenceYawAbsRad = 0f;
    private float headReferencePitchAbsRad = 0f;
    private float displayYawOffsetRad = 0f;
    private float displayPitchOffsetRad = 0f;
    private float lastCameraYawRad = 0f;
    private float lastCameraPitchRad = 0f;
    private float lastTargetWorldYawRad = Float.NaN;
    private float lastTargetWorldPitchRad = Float.NaN;
    private float lastLockScale = 0.18f;

    private float sensorHorizontalFovRad = DEFAULT_SENSOR_HORIZONTAL_FOV_RAD;
    private float sensorVerticalFovRad = DEFAULT_SENSOR_VERTICAL_FOV_RAD;
    private float activeHorizontalFovRad = DEFAULT_SENSOR_HORIZONTAL_FOV_RAD;
    private float activeVerticalFovRad = DEFAULT_SENSOR_VERTICAL_FOV_RAD;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    if (pendingScanRequest) {
                        pendingScanRequest = false;
                        beginScanSession();
                    }
                } else {
                    pendingScanRequest = false;
                    renderStandbyState(
                            getString(R.string.status_permission),
                            getString(R.string.prompt_permission)
                    );
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        hudView = findViewById(R.id.hudView);

        previewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewView.setVisibility(View.INVISIBLE);

        hudView.setClickable(true);
        hudView.setFocusable(true);
        hudView.setOnClickListener(view -> onPrimaryAction());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gameRotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        }

        faceDetector = FaceDetection.getClient(
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .enableTracking()
                        .setMinFaceSize(0.15f)
                        .build()
        );
        cameraExecutor = Executors.newSingleThreadExecutor();

        hideSystemUi();
        renderStandbyState();
    }

    private void onPrimaryAction() {
        if (scanSessionActive) {
            recenterProjection();
        } else {
            onScanRequested();
        }
    }

    private void onScanRequested() {
        if (scanSessionActive) {
            return;
        }

        pendingScanRequest = true;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            pendingScanRequest = false;
            beginScanSession();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void beginScanSession() {
        scanSessionActive = true;
        currentTargetId = null;
        currentPowerLevel = PowerLevelGenerator.next();
        lastFaceSeenAt = 0L;
        lastPowerSeedAt = 0L;
        activeLensLabel = getString(R.string.lens_booting);

        resetAngularState();
        startHeadTracking();
        renderScanningState(getString(R.string.status_scanning), activeLensLabel);
        restartScanTimeout();
        startCamera();
    }

    private void restartScanTimeout() {
        sessionHandler.removeCallbacks(scanTimeoutRunnable);
        if (scanSessionActive) {
            sessionHandler.postDelayed(scanTimeoutRunnable, SCAN_SESSION_TIMEOUT_MS);
        }
    }

    private void startCamera() {
        if (!scanSessionActive) {
            return;
        }

        if (cameraProvider != null) {
            bindCameraUseCases();
            return;
        }

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                if (scanSessionActive) {
                    bindCameraUseCases();
                }
            } catch (Exception exception) {
                stopScanSession(
                        getString(R.string.status_camera_error),
                        getString(R.string.prompt_tap_to_scan)
                );
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (!scanSessionActive || cameraProvider == null) {
            return;
        }

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetRotation(previewView.getDisplay() != null
                        ? previewView.getDisplay().getRotation()
                        : Surface.ROTATION_0)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, new FaceAnalyzer());

        List<SelectorCandidate> candidates = Arrays.asList(
                new SelectorCandidate("WORLD CAM", CameraSelector.DEFAULT_BACK_CAMERA),
                new SelectorCandidate("ALT CAM", CameraSelector.DEFAULT_FRONT_CAMERA),
                new SelectorCandidate(
                        "AUTO CAM",
                        new CameraSelector.Builder()
                                .addCameraFilter(cameras -> cameras.isEmpty()
                                        ? cameras
                                        : Arrays.asList(cameras.get(0)))
                                .build()
                )
        );

        cameraProvider.unbindAll();

        for (SelectorCandidate candidate : candidates) {
            try {
                Camera camera = cameraProvider.bindToLifecycle(this, candidate.selector, analysis);
                activeLensLabel = candidate.label;
                configureCameraProjection(camera);
                renderScanningState(getString(R.string.status_scanning), activeLensLabel);
                return;
            } catch (Exception ignored) {
                cameraProvider.unbindAll();
            }
        }

        stopScanSession(
                getString(R.string.status_camera_error),
                getString(R.string.prompt_tap_to_scan)
        );
    }

    private void configureCameraProjection(Camera camera) {
        sensorHorizontalFovRad = DEFAULT_SENSOR_HORIZONTAL_FOV_RAD;
        sensorVerticalFovRad = DEFAULT_SENSOR_VERTICAL_FOV_RAD;

        try {
            String cameraId = Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameraManager == null) {
                return;
            }

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (focalLengths == null || focalLengths.length == 0 || physicalSize == null) {
                return;
            }

            float focalLength = focalLengths[0];
            sensorHorizontalFovRad = (float) (2d * Math.atan((physicalSize.getWidth() / 2d) / focalLength));
            sensorVerticalFovRad = (float) (2d * Math.atan((physicalSize.getHeight() / 2d) / focalLength));
            activeHorizontalFovRad = sensorHorizontalFovRad;
            activeVerticalFovRad = sensorVerticalFovRad;
        } catch (Exception ignored) {
        }
    }

    private final class FaceAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            if (!scanSessionActive) {
                imageProxy.close();
                return;
            }

            if (imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }

            if (!analyzerBusy.compareAndSet(false, true)) {
                imageProxy.close();
                return;
            }

            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            int imageWidth = (rotationDegrees == 90 || rotationDegrees == 270)
                    ? imageProxy.getHeight()
                    : imageProxy.getWidth();
            int imageHeight = (rotationDegrees == 90 || rotationDegrees == 270)
                    ? imageProxy.getWidth()
                    : imageProxy.getHeight();

            InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), rotationDegrees);
            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> handleFaces(faces, imageWidth, imageHeight))
                    .addOnFailureListener(error -> {
                        if (!scanSessionActive) {
                            return;
                        }

                        long now = SystemClock.elapsedRealtime();
                        if (canProjectTarget(now)) {
                            renderTrackedHud(now);
                        } else if (now - lastFaceSeenAt > LOST_TARGET_GRACE_MS) {
                            renderScanningState(getString(R.string.status_retrying), activeLensLabel);
                        }
                    })
                    .addOnCompleteListener(task -> {
                        analyzerBusy.set(false);
                        imageProxy.close();
                    });
        }
    }

    private void handleFaces(List<Face> faces, int imageWidth, int imageHeight) {
        if (!scanSessionActive) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        updateActiveFovForImage(imageWidth, imageHeight);

        if (faces.isEmpty()) {
            if (canProjectTarget(now)) {
                renderTrackedHud(now);
            } else {
                clearTrackedTarget();
                renderScanningState(getString(R.string.status_scanning), activeLensLabel);
            }
            return;
        }

        Face bestFace = selectLargestFace(faces);
        if (bestFace == null) {
            return;
        }

        Rect bounds = bestFace.getBoundingBox();
        float faceCenterX = bounds.exactCenterX();
        float faceCenterY = bounds.exactCenterY();
        float cameraYawRad = computeAngularOffset(faceCenterX, imageWidth, activeHorizontalFovRad);
        float cameraPitchRad = -computeAngularOffset(faceCenterY, imageHeight, activeVerticalFovRad);

        lastCameraYawRad = cameraYawRad;
        lastCameraPitchRad = cameraPitchRad;
        lastLockScale = computeLockScale(bounds, imageWidth, imageHeight);

        float headYawRad = getRelativeHeadYaw();
        float headPitchRad = getRelativeHeadPitch();
        lastTargetWorldYawRad = normalizeAngle(headYawRad + cameraYawRad);
        lastTargetWorldPitchRad = normalizeAngle(headPitchRad + cameraPitchRad);

        int detectedTargetId = bestFace.getTrackingId() != null ? bestFace.getTrackingId() : -1;
        boolean shouldReseedPower = currentTargetId == null
                || currentTargetId != detectedTargetId
                || now - lastPowerSeedAt > POWER_RESEED_MS
                || now - lastFaceSeenAt > TARGET_PREDICTION_GRACE_MS;

        if (shouldReseedPower) {
            currentTargetId = detectedTargetId;
            currentPowerLevel = PowerLevelGenerator.next();
            lastPowerSeedAt = now;
        }

        lastFaceSeenAt = now;
        renderTrackedHud(now);
    }

    private void renderTrackedHud(long now) {
        if (!canProjectTarget(now)) {
            renderScanningState(getString(R.string.status_scanning), activeLensLabel);
            return;
        }

        AngularTargetLock projectedLock = projectTrackedTarget();
        if (projectedLock == null) {
            renderScanningState(getString(R.string.status_scanning), activeLensLabel);
            return;
        }

        boolean predictive = now - lastFaceSeenAt > FRESH_TRACK_WINDOW_MS;
        String statusLabel;
        if (currentPowerLevel > 9000) {
            statusLabel = getString(R.string.status_over_9000);
        } else if (predictive) {
            statusLabel = getString(R.string.status_track_hold);
        } else {
            statusLabel = getString(R.string.status_target_lock);
        }

        hudView.render(HudState.activeAngular(
                statusLabel,
                activeLensLabel,
                currentPowerLevel,
                currentTargetId != null && currentTargetId >= 0 ? currentTargetId : 1,
                currentPowerLevel > 9000,
                projectedLock.centerX,
                projectedLock.centerY,
                projectedLock.lockScale,
                predictive,
                getString(R.string.prompt_tap_to_recenter)
        ));
    }

    private AngularTargetLock projectTrackedTarget() {
        if (Float.isNaN(lastTargetWorldYawRad) || Float.isNaN(lastTargetWorldPitchRad)) {
            return null;
        }

        int hudWidth = hudView.getWidth() > 0 ? hudView.getWidth() : 480;
        int hudHeight = hudView.getHeight() > 0 ? hudView.getHeight() : 640;

        float relativeYawRad = normalizeAngle(lastTargetWorldYawRad - getRelativeHeadYaw()) + displayYawOffsetRad;
        float relativePitchRad = normalizeAngle(lastTargetWorldPitchRad - getRelativeHeadPitch()) + displayPitchOffsetRad;

        float halfWidth = hudWidth / 2f;
        float halfHeight = hudHeight / 2f;
        float maxHorizontal = Math.max(activeHorizontalFovRad / 2f, 0.01f);
        float maxVertical = Math.max(activeVerticalFovRad / 2f, 0.01f);

        float normalizedX = clamp(relativeYawRad / maxHorizontal, -1.35f, 1.35f);
        float normalizedY = clamp(relativePitchRad / maxVertical, -1.35f, 1.35f);

        float screenX = halfWidth + normalizedX * halfWidth * DISPLAY_YAW_GAIN;
        float screenY = halfHeight - normalizedY * halfHeight * DISPLAY_PITCH_GAIN;

        float horizontalMargin = hudWidth * DISPLAY_MARGIN_RATIO;
        float verticalMargin = hudHeight * DISPLAY_MARGIN_RATIO;

        screenX = clamp(screenX, horizontalMargin, hudWidth - horizontalMargin);
        screenY = clamp(screenY, verticalMargin, hudHeight - verticalMargin);

        return new AngularTargetLock(screenX, screenY, lastLockScale);
    }

    private void recenterProjection() {
        long now = SystemClock.elapsedRealtime();
        restartScanTimeout();

        if (hasHeadPose) {
            captureHeadReference();
        } else {
            hasHeadReference = false;
        }

        if (canProjectTarget(now)) {
            displayYawOffsetRad = -lastCameraYawRad;
            displayPitchOffsetRad = -lastCameraPitchRad;
            lastTargetWorldYawRad = lastCameraYawRad;
            lastTargetWorldPitchRad = lastCameraPitchRad;
            renderTrackedHud(now);
        } else {
            displayYawOffsetRad = 0f;
            displayPitchOffsetRad = 0f;
            renderScanningState(getString(R.string.status_recentered), activeLensLabel);
        }
    }

    private void startHeadTracking() {
        if (sensorManager != null && gameRotationVectorSensor != null) {
            sensorManager.registerListener(
                    headPoseListener,
                    gameRotationVectorSensor,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }
    }

    private void stopHeadTracking() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(headPoseListener);
        }
    }

    private void captureHeadReference() {
        headReferenceYawAbsRad = currentHeadYawAbsRad;
        headReferencePitchAbsRad = currentHeadPitchAbsRad;
        hasHeadReference = true;
    }

    private float getRelativeHeadYaw() {
        if (!hasHeadPose || !hasHeadReference) {
            return 0f;
        }
        return normalizeAngle(currentHeadYawAbsRad - headReferenceYawAbsRad);
    }

    private float getRelativeHeadPitch() {
        if (!hasHeadPose || !hasHeadReference) {
            return 0f;
        }
        return normalizeAngle(currentHeadPitchAbsRad - headReferencePitchAbsRad);
    }

    private void resetAngularState() {
        displayYawOffsetRad = 0f;
        displayPitchOffsetRad = 0f;
        lastCameraYawRad = 0f;
        lastCameraPitchRad = 0f;
        lastTargetWorldYawRad = Float.NaN;
        lastTargetWorldPitchRad = Float.NaN;
        lastLockScale = 0.18f;
        sensorHorizontalFovRad = DEFAULT_SENSOR_HORIZONTAL_FOV_RAD;
        sensorVerticalFovRad = DEFAULT_SENSOR_VERTICAL_FOV_RAD;
        activeHorizontalFovRad = DEFAULT_SENSOR_HORIZONTAL_FOV_RAD;
        activeVerticalFovRad = DEFAULT_SENSOR_VERTICAL_FOV_RAD;
        lastSensorRenderAt = 0L;

        if (hasHeadPose) {
            captureHeadReference();
        } else {
            hasHeadReference = false;
        }
    }

    private void updateActiveFovForImage(int imageWidth, int imageHeight) {
        boolean portraitImage = imageHeight > imageWidth;
        activeHorizontalFovRad = portraitImage ? sensorVerticalFovRad : sensorHorizontalFovRad;
        activeVerticalFovRad = portraitImage ? sensorHorizontalFovRad : sensorVerticalFovRad;
    }

    private Face selectLargestFace(List<Face> faces) {
        Face bestFace = null;
        int bestArea = -1;

        for (Face face : faces) {
            Rect bounds = face.getBoundingBox();
            int area = bounds.width() * bounds.height();
            if (area > bestArea) {
                bestArea = area;
                bestFace = face;
            }
        }

        return bestFace;
    }

    private float computeAngularOffset(float center, int size, float fieldOfViewRad) {
        if (size <= 0 || fieldOfViewRad <= 0f) {
            return 0f;
        }

        float normalized = ((center / size) - 0.5f) * 2f;
        normalized = clamp(normalized, -1f, 1f);
        return (float) Math.atan(normalized * Math.tan(fieldOfViewRad / 2f));
    }

    private float computeLockScale(Rect bounds, int imageWidth, int imageHeight) {
        float widthRatio = imageWidth > 0 ? bounds.width() / (float) imageWidth : 0f;
        float heightRatio = imageHeight > 0 ? bounds.height() / (float) imageHeight : 0f;
        float sizeRatio = Math.max(widthRatio, heightRatio);
        return clamp(0.16f + (sizeRatio * 0.65f), 0.18f, 0.30f);
    }

    private boolean canProjectTarget(long now) {
        return currentTargetId != null
                && !Float.isNaN(lastTargetWorldYawRad)
                && !Float.isNaN(lastTargetWorldPitchRad)
                && now - lastFaceSeenAt <= TARGET_PREDICTION_GRACE_MS;
    }

    private void clearTrackedTarget() {
        currentTargetId = null;
        lastTargetWorldYawRad = Float.NaN;
        lastTargetWorldPitchRad = Float.NaN;
    }

    private float clamp(float value, float minValue, float maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private float normalizeAngle(float value) {
        while (value > Math.PI) {
            value -= (float) (Math.PI * 2d);
        }
        while (value < -Math.PI) {
            value += (float) (Math.PI * 2d);
        }
        return value;
    }

    private void stopScanSession() {
        stopScanSession(getString(R.string.status_press_to_scan), getString(R.string.prompt_tap_to_scan));
    }

    private void stopScanSession(String status, String prompt) {
        scanSessionActive = false;
        pendingScanRequest = false;
        currentTargetId = null;
        lastFaceSeenAt = 0L;
        lastPowerSeedAt = 0L;
        analyzerBusy.set(false);
        sessionHandler.removeCallbacks(scanTimeoutRunnable);

        stopHeadTracking();
        resetAngularState();

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        previewView.setVisibility(View.INVISIBLE);
        renderStandbyState(status, prompt);
    }

    private void renderStandbyState() {
        renderStandbyState(getString(R.string.status_press_to_scan), getString(R.string.prompt_tap_to_scan));
    }

    private void renderStandbyState(String status, String prompt) {
        hudView.render(HudState.idle(status, getString(R.string.lens_standby), prompt));
    }

    private void renderScanningState(String status, String lensLabel) {
        hudView.render(HudState.active(status, lensLabel, getString(R.string.prompt_tap_to_recenter)));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && isScanTriggerKey(event.getKeyCode())) {
            onPrimaryAction();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isScanTriggerKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KeyEvent.KEYCODE_SPACE;
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            @SuppressWarnings("deprecation")
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onPause() {
        if (scanSessionActive) {
            stopScanSession();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sessionHandler.removeCallbacks(scanTimeoutRunnable);
        stopHeadTracking();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        faceDetector.close();
        cameraExecutor.shutdown();
        super.onDestroy();
    }

    private static final class AngularTargetLock {
        final float centerX;
        final float centerY;
        final float lockScale;

        AngularTargetLock(float centerX, float centerY, float lockScale) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.lockScale = lockScale;
        }
    }

    private static final class SelectorCandidate {
        final String label;
        final CameraSelector selector;

        SelectorCandidate(String label, CameraSelector selector) {
            this.label = label;
            this.selector = selector;
        }
    }
}
