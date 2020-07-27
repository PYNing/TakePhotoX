package com.camerax.lib.core;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraXConfig;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.camerax.lib.CameraUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Copyright (C) 2017
 * 版权所有
 * <p>
 * 功能描述：
 * <p>
 * 作者：yijiebuyi
 * 创建时间：2020/7/27
 * <p>
 * 修改人：
 * 修改描述：
 * 修改日期
 */

public class CameraView extends CameraPreview implements ICamera, IFlashLight,
        CameraPreview.CameraGestureListener, CameraXConfig.Provider{
    private final static String TAG = "CameraView";

    /**
     * 当前相机预览参数
     */
    private CameraParam mCameraParam;

    private ListenableFuture<ProcessCameraProvider> mCameraProviderFuture;
    private CameraSelector mCameraSelector;
    private Camera mCamera;

    private ImageCapture mImageCapture;
    private ImageAnalysis mImageAnalysis;
    private CameraInfo mCameraInfo;
    private CameraControl mCameraControl;
    private Preview mPreview;

    private LifecycleOwner mLifecycleOwner;
    private Executor mExecutor;

    /**
     * 是否需要图片分析
     */
    private boolean mIsImgAnalysis;
    /**
     * 分析开始时间
     */
    private long mAnalysisStartTime;
    /**
     * 拍照保存的图片路径
     */
    private String mOutFilePath;

    private int SCREEN_WIDTH = 0;
    private int SCREEN_HEIGHT = 0;

    private Context mContext;

    public CameraView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    /**
     * 初始化
     * @param context
     * @param attrs
     */
    private void init(Context context, AttributeSet attrs) {
        mContext = context;
        mCameraParam = new CameraParam();
        mCameraParam.asRatio = ExAspectRatio.RATIO_16_9;
        mCameraParam.faceFront = false;
        mCameraParam.scale = 1.0f;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        SCREEN_WIDTH = dm.widthPixels;
        SCREEN_HEIGHT = dm.heightPixels;

        setCameraGestureListener(this);
    }

    private OnCameraListener mOnCameraListener;
    private OnImgAnalysisListener mOnImgAnalysisListener;
    private OnFocusListener mOnFocusListener;
    private OnCameraFaceListener mOnCameraFaceListener;

    public void setOnCameraListener(OnCameraListener listener) {
        mOnCameraListener = listener;
    }

    public void setOnImgAnalysisListener(OnImgAnalysisListener listener) {
        mOnImgAnalysisListener = listener;
    }

    public void setOnFocusListener(OnFocusListener listener) {
        mOnFocusListener = listener;
    }

    public void setOnCameraFaceListener(OnCameraFaceListener listener) {
        mOnCameraFaceListener = listener;
    }

    /**
     * 初始化相机
     */
    public void initCamera(CameraOption option, LifecycleOwner lifecycleOwner) {
        mLifecycleOwner = lifecycleOwner;
        setOption(option);

        reset();
    }

    /**
     * 设置参数
     * @param option
     */
    private void setOption(CameraOption option) {
        if (option == null) {
            return;
        }

        if (option.getRatio() == ExAspectRatio.RATIO_16_9
                || option.getRatio() == ExAspectRatio.RATIO_4_3
                || option.getRatio() == ExAspectRatio.RATIO_1_1) {
            mCameraParam.asRatio = option.getRatio();
        } else {
            throw new IllegalArgumentException("ratio param error!");
        }

        mCameraParam.faceFront = option.isFaceFront();
        mIsImgAnalysis = option.isAnalysisImg();
        mOutFilePath = option.getOutPath();

        //如果是分析图片，默认后置
        if (mIsImgAnalysis) {
            mCameraParam.faceFront = false;
        }
    }

    /**
     * 初始化配置信息
     */
    private void initUseCases() {
        initImageAnalysis();
        initImageCapture();
        initPreview();
        initCameraSelector();
    }

    /**
     * 图像分析
     */
    private void initImageAnalysis() {
        mImageAnalysis = new ImageAnalysis.Builder()
                // 分辨率
                .setTargetResolution(CameraUtil.computeSize(mCameraParam.asRatio, SCREEN_WIDTH))
                // 非阻塞模式
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        if (mIsImgAnalysis) {
            startImgAnalysis();
        }
    }


    /**
     * 构建图像捕获用例
     */
    private void initImageCapture() {
        // 构建图像捕获用例
        mImageCapture = new ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setTargetAspectRatio(mCameraParam.asRatio)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build();

        // 旋转监听
        OrientationEventListener orientationEventListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                int rotation;
                // Monitors orientation values to determine the target rotation value
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                } else {
                    rotation = Surface.ROTATION_0;
                }

                mImageCapture.setTargetRotation(rotation);
            }
        };

        orientationEventListener.enable();
    }

    /**
     * 构建图像预览
     */
    private void initPreview() {
        mPreview = new Preview.Builder()
                .setTargetAspectRatio(mCameraParam.asRatio)
                .build();
    }

    /**
     * 选择摄像头
     */
    private void initCameraSelector() {
        mCameraSelector = new CameraSelector.Builder()
                .requireLensFacing(mCameraParam.faceFront ?
                        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();
    }

    /**
     * 分析图片
     */
    public void startImgAnalysis() {
        mAnalysisStartTime = System.currentTimeMillis();
        mImageAnalysis.setAnalyzer(mExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (mOnImgAnalysisListener != null) {
                    mOnImgAnalysisListener.onImageAnalysis(image, System.currentTimeMillis() - mAnalysisStartTime);
                }
            }
        });
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        mPreview.setSurfaceProvider(createSurfaceProvider());
        mCamera = cameraProvider.bindToLifecycle(mLifecycleOwner, mCameraSelector,
                mImageCapture, mImageAnalysis, mPreview);

        mCameraInfo = mCamera.getCameraInfo();
        mCameraControl = mCamera.getCameraControl();
    }

    @Override
    public void take() {
        final File file = !TextUtils.isEmpty(mOutFilePath) ? new File(mOutFilePath) : CameraUtil.getOutFile(mContext);
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        metadata.setReversedHorizontal(mCameraParam.faceFront);
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build();
        mImageCapture.takePicture(outputFileOptions, mExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri result = outputFileResults.getSavedUri();
                        if (result == null) {
                            result = Uri.fromFile(file);
                        }

                        if (mOnCameraListener != null) {
                            mOnCameraListener.onTaken(result);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, exception.getMessage());
                        //Toast.makeText(mContext, exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void focus(float x, float y) {
        if (mOnFocusListener != null) {
            mOnFocusListener.onStartFocus(x, y);
        }

        MeteringPointFactory factory = createMeteringPointFactory(mCameraSelector);
        //MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                // auto calling cancelFocusAndMetering in 3 seconds
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build();
        final ListenableFuture future = mCameraControl.startFocusAndMetering(action);
        future.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    FocusMeteringResult result = (FocusMeteringResult) future.get();
                    if (mOnFocusListener != null) {
                        mOnFocusListener.onEndFocus(result.isFocusSuccessful());
                    }
                } catch (Exception e) {
                    if (mOnFocusListener != null) {
                        mOnFocusListener.onEndFocus(false);
                    }
                }
            }
        }, mExecutor);
    }

    @Override
    public void switchFace() {
        if (mCameraParam.faceFront) {
            mCameraParam.faceFront = false;
        } else {
            mCameraParam.faceFront = true;
        }

        if (mOnCameraFaceListener != null) {
            mOnCameraFaceListener.onSwitchCamera(mCameraParam.faceFront);
        }
        reset();
    }

    @Override
    public void reset() {
        mExecutor = ContextCompat.getMainExecutor(getContext());
        mCameraProviderFuture = ProcessCameraProvider.getInstance(getContext());

        initUseCases();

        mCameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = mCameraProviderFuture.get();
                    cameraProvider.unbindAll();
                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, mExecutor);
    }

    @Override
    public void cancel() {
        if (mOnCameraListener != null) {
            mOnCameraListener.onCancel();
        }
    }

    @Override
    public CameraParam getCameraParam() {
        return mCameraParam;
    }

    @Override
    public void closeFlashLight() {
        mImageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
        mCameraControl.enableTorch(false);
        mCameraParam.lightState = IFlashLight.CLOSE;
    }

    @Override
    public void openFlashLight() {
        mImageCapture.setFlashMode(ImageCapture.FLASH_MODE_ON);
        mCameraParam.lightState = IFlashLight.OPEN;
    }

    @Override
    public void autoFlashLight() {
        mImageCapture.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
        mCameraParam.lightState = IFlashLight.AUTO;
    }

    @Override
    public void fillLight() {
        mCameraControl.enableTorch(true);
        mCameraParam.lightState = IFlashLight.FILL;
    }

    @Override
    public void onClick(float x, float y) {
        focus(x, y);
    }

    @Override
    public void onZoom(float scale) {
        mCameraControl.setZoomRatio(scale);
    }

    @NonNull
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }
}