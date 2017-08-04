package com.terainsights.a2q2r_android.activity;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.terainsights.a2q2r_android.R;

import github.nisrulz.qreader.QRDataListener;
import github.nisrulz.qreader.QREader;

/**
 * Created by justin on 6/3/17.
 */

public class ScanFragment1 extends Fragment implements SurfaceHolder.Callback{

    OnQRScanListener mCallback;

    public interface OnQRScanListener{
        public void onQRScan(boolean success, Intent data);
    }

    QREader qrReader;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Dexter.initialize(getActivity());
        PermissionListener listener = DialogOnDeniedPermissionListener.Builder
                .withContext(getActivity())
                .withTitle("Camera Permission")
                .withMessage(R.string.camera_required)
                .withButtonText(android.R.string.ok)
                .build();
        Dexter.checkPermission(listener, Manifest.permission.CAMERA);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.scan, container, false);
    }

    @Override
    public void onAttach(Context activity){
        super.onAttach(activity);
        try {
            mCallback = (OnQRScanListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }

    /**
     * Registers this class as the callback for the image surface in the layout XML.
     */
    @Override
    public void onStart() {

        super.onStart();

        ((SurfaceView) getActivity().findViewById(R.id.camera_view)).getHolder().addCallback(this);

    }

    /**
     * Called when the activity is opened and the camera preview surface becomes visible.
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        try {
            SurfaceView cameraView = (SurfaceView) getView().findViewById(R.id.camera_view);

            // Open the camera.
            qrReader = new QREader.Builder(getView().getContext(), cameraView, new QRDataListener() {
                @Override
                public void onDetected(final String data) {
                    onQRScanned(data);
                }
            }).facing(QREader.BACK_CAM)
                    .enableAutofocus(true)
                    .height(cameraView.getHeight())
                    .width(cameraView.getWidth())
                    .build();

            qrReader.initAndStart(cameraView);
            qrReader.start();
            Log.i("MONITOR", qrReader.isCameraRunning() + " ");
        } catch (NullPointerException e) {

            // Didn't find a camera to open.
            e.printStackTrace();
            surfaceDestroyed(holder);
            return;

        } catch (RuntimeException e) {

            // The app was denied permission to camera services.
            e.printStackTrace();
            surfaceDestroyed(holder);
            return;

        }
    }

    /**
     * If the preview surface is altered for some reason, fixes it and refreshes the preview.
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//
//        if (camera == null)
//            return;
//
//        camera.setDisplayOrientation(90);
//        camera.startPreview();

    }

    /**
     * When the activity is closed, release the camera.
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        qrReader.releaseAndCleanup();

    }

    /**
     * Called when a QR code is successfully read from the preview.
     * @param content The text encoded in the QR.
     */
    private void onQRScanned(String content) {

        System.out.println("QRScanned");

        if (content != null) {

            Intent intent = new Intent();
            intent.putExtra("qr_content", content);
            mCallback.onQRScan(true, intent);

        } else {

            Intent intent = new Intent();
            intent.putExtra("canceled", true);
            mCallback.onQRScan(false, intent);

        }

    }
}
