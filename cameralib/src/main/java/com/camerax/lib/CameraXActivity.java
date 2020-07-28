package com.camerax.lib;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.camerax.lib.core.CameraOption;
import com.camerax.lib.core.ExAspectRatio;
import com.camerax.lib.core.OnCameraListener;
import com.camerax.lib.core.OnImgAnalysisListener;
import com.qw.soul.permission.SoulPermission;
import com.qw.soul.permission.bean.Permission;
import com.qw.soul.permission.bean.Permissions;
import com.qw.soul.permission.callbcak.CheckRequestPermissionsListener;

/**
 * Copyright (C) 2017
 * 版权所有
 * <p>
 * 功能描述：
 * <p>
 * 作者：yijiebuyi
 * 创建时间：2020-07-21
 * <p>
 * 修改人：
 * 修改描述：
 * 修改日期
 */

public class CameraXActivity extends AppCompatActivity {
    private final static String CAMERA_FRAGMENT = "camera_fragment";
    private QrCodeParser mQrCodeParser;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        requestPermission();
    }

    public void enterCameraFragment() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        final CameraFragment cfg = new CameraFragment();

        CameraOption option = new CameraOption.Builder(ExAspectRatio.RATIO_16_9)
                //.outPath(Environment.getExternalStorageDirectory() + "/AAAA.JPEG")
                .faceFront(false)
                .build();

        Bundle data = new Bundle();
        data.putSerializable(CameraFragment.KEY_CAMERA_OPTION, option);
        cfg.setArguments(data);
        cfg.setOnCameraListener(new OnCameraListener() {
            @Override
            public void onTaken(Uri uri) {
                cfg.hidePanel(false);
                enterPhotoFragment(uri);
            }

            @Override
            public void onCancel() {
                finish();
            }
        });

        cfg.setOnImgAnalysisListener(new OnImgAnalysisListener() {
            @Override
            public void onImageAnalysis(@NonNull ImageProxy image, long elapseTime) {
                if (mQrCodeParser == null) {
                    mQrCodeParser = new QrCodeParser();
                    mQrCodeParser.setQRCallback(new QrCodeParser.QRCallback() {
                        @Override
                        public void onSucc(String result) {
                            Toast.makeText(CameraXActivity.this, result, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFail() {
                            Toast.makeText(CameraXActivity.this, R.string.qr_code_fail, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                mQrCodeParser.execute(image, elapseTime);
            }
        });
        ft.replace(R.id.container, cfg, CAMERA_FRAGMENT);
        ft.commit();
    }


    public void enterPhotoFragment(Uri fileUri) {
        FragmentManager fm = getSupportFragmentManager();
        final FragmentTransaction ft = fm.beginTransaction();

        final PhotoFragment fg = new PhotoFragment();
        Bundle data = new Bundle();
        data.putParcelable(PhotoFragment.KEY_PHOTO_URI, fileUri);
        fg.setArguments(data);
        fg.setOnPhotoListener(new OnPhotoListener() {
            @Override
            public void onPhotoSelect(Uri uri) {
                finishWithData(uri);
            }

            @Override
            public void onCancel() {
                enterCameraFragment();
            }

            @Override
            public void onPhotoLoad(boolean succ) {

            }
        });

        ft.replace(R.id.container, fg);
        ft.commit();
    }

    private void requestPermission() {
        SoulPermission.getInstance().checkAndRequestPermissions(
                Permissions.build(Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE),
                //if you want do noting or no need all the callbacks you may use SimplePermissionsAdapter instead
                new CheckRequestPermissionsListener() {
                    @Override
                    public void onAllPermissionOk(Permission[] allPermissions) {
                        //Toast.makeText(CameraXActivity.this, "授权成功", Toast.LENGTH_SHORT).show();
                        enterCameraFragment();
                    }

                    @Override
                    public void onPermissionDenied(Permission[] refusedPermissions) {
                        Toast.makeText(CameraXActivity.this, R.string.permission_grant_fail, Toast.LENGTH_SHORT).show();
                    }
                });

    }

    private void finishWithData(Uri uri) {
        Intent intent = new Intent();
        intent.setData(uri);
        setResult(RESULT_OK, intent);
        finish();
    }
}
