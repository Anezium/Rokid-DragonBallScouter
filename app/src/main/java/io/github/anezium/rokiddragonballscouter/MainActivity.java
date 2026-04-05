package io.github.anezium.rokiddragonballscouter;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
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
    private static final long POWER_RESEED_MS = 7_000L;
    private static final long SCAN_SESSION_TIMEOUT_MS = 20_000L;

    private PreviewView previewView;
    private ScouterOverlayView hudView;

    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    private final AtomicBoolean analyzerBusy = new AtomicBoolean(false);
    private final Handler sessionHandler = new Handler(Looper.getMainLooper());
    private volatile boolean scanSessionActive = false;
    private final Runnable scanTimeoutRunnable = () -> {
        if (scanSessionActive) {
            stopScanSession();
        }
    };

    private Integer currentTargetId = null;
    private int currentPowerLevel = PowerLevelGenerator.next();
    private long lastFaceSeenAt = 0L;
    private long lastPowerSeedAt = 0L;
    private boolean pendingScanRequest = false;
    private String activeLensLabel = "AUTO CAM";

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
        hudView.setOnClickListener(view -> onScanRequested());

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

        Preview preview = new Preview.Builder()
                .setTargetRotation(previewView.getDisplay() != null
                        ? previewView.getDisplay().getRotation()
                        : Surface.ROTATION_0)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

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
                cameraProvider.bindToLifecycle(this, candidate.selector, preview, analysis);
                activeLensLabel = candidate.label;
                previewView.setVisibility(View.VISIBLE);
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
                        if (SystemClock.elapsedRealtime() - lastFaceSeenAt > LOST_TARGET_GRACE_MS) {
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

        if (faces.isEmpty()) {
            if (now - lastFaceSeenAt > LOST_TARGET_GRACE_MS) {
                currentTargetId = null;
                renderScanningState(getString(R.string.status_scanning), activeLensLabel);
            }
            return;
        }

        Face bestFace = null;
        int bestArea = -1;
        for (Face face : faces) {
            int area = face.getBoundingBox().width() * face.getBoundingBox().height();
            if (area > bestArea) {
                bestArea = area;
                bestFace = face;
            }
        }

        if (bestFace == null) {
            return;
        }

        int detectedTargetId = bestFace.getTrackingId() != null ? bestFace.getTrackingId() : -1;
        boolean shouldReseedPower = currentTargetId == null
                || currentTargetId != detectedTargetId
                || now - lastPowerSeedAt > POWER_RESEED_MS
                || now - lastFaceSeenAt > LOST_TARGET_GRACE_MS;

        if (shouldReseedPower) {
            currentTargetId = detectedTargetId;
            currentPowerLevel = PowerLevelGenerator.next();
            lastPowerSeedAt = now;
        }

        lastFaceSeenAt = now;

        hudView.render(HudState.active(
                currentPowerLevel > 9000
                        ? getString(R.string.status_over_9000)
                        : getString(R.string.status_target_lock),
                activeLensLabel,
                new RectF(bestFace.getBoundingBox()),
                imageWidth,
                imageHeight,
                currentPowerLevel,
                detectedTargetId >= 0 ? detectedTargetId : 1,
                currentPowerLevel > 9000
        ));
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
        hudView.render(HudState.active(status, lensLabel));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP
                && isScanTriggerKey(event.getKeyCode())
                && !scanSessionActive) {
            onScanRequested();
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
            int flags = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
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
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        faceDetector.close();
        cameraExecutor.shutdown();
        super.onDestroy();
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
