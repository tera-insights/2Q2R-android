package com.terainsights.a2q2r_android.activity;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.hardware.Camera;
import android.os.AsyncTask;
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
import com.terainsights.a2q2r_android.util.Scanner;

import java.io.IOException;
import java.util.List;

/**
 * Created by justin on 6/3/17.
 */

public class ScanFragment extends Fragment implements SurfaceHolder.Callback{

    OnQRScanListener mCallback;

    public interface OnQRScanListener{
        public void onQRScan(boolean success, Intent data);
    }

    private static int SCAN_ACTION = 0;
    private static int CLEAR_ACTION = 1;

    private Camera camera;
    private Scanner scanner;

    String prevContent = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scanner = new Scanner() {
            @Override
            public void onPostExecute(String result) {
                onQRScanned(result);
            }
        };

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
    public void onAttach(Activity activity){
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

            // Open the camera.
            camera = Camera.open();
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(scanner);
            if(scanner.getStatus() != AsyncTask.Status.RUNNING)
                scanner.execute();

        } catch (NullPointerException e) {

            // Didn't find a camera to open.
            e.printStackTrace();
            surfaceDestroyed(holder);
            return;

        } catch (IOException e) {

            // Camera preview setup failed.
            e.printStackTrace();
            surfaceDestroyed(holder);
            return;

        } catch (RuntimeException e) {

            // The app was denied permission to camera services.
            e.printStackTrace();
            surfaceDestroyed(holder);
            return;

        }

        // Set auto-focus mode
        Camera.Parameters params = camera.getParameters();
        List<String> modes = params.getSupportedFocusModes();
        if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        else if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        else if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        camera.setParameters(params);
    }

    /**
     * If the preview surface is altered for some reason, fixes it and refreshes the preview.
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        if (camera == null)
            return;

        camera.setDisplayOrientation(90);
        camera.startPreview();

    }

    /**
     * When the activity is closed, release the camera.
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        if (camera == null)
            return;

        camera.stopPreview();
        camera.setPreviewCallback(null);
        camera.release();
        camera = null;
        scanner.cancel(true);

    }

    /**
     * Called when a QR code is successfully read from the preview.
     * @param content The text encoded in the QR.
     */
    private void onQRScanned(String content) {
        Log.i("SCANNING", prevContent + " <> " + content);
        if(!content.equals(prevContent)) {
            prevContent = content;
            System.out.println("QRScanned");
            Intent intent = new Intent();
            intent.putExtra("qr_content", content);
            mCallback.onQRScan(true, intent);
        }

        scanner = new Scanner() {
            @Override
            public void onPostExecute(String result) {
                onQRScanned(result);
            }
        };
        camera.setPreviewCallback(scanner);
        if(scanner.getStatus() != AsyncTask.Status.RUNNING)
            scanner.execute();

    }
}
