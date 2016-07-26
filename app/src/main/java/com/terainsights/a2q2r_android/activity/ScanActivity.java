/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 *
 * Copyright (C) 2013  Nathaniel McCallum, Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terainsights.a2q2r_android.activity;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.util.Scanner;

import java.io.IOException;
import java.util.List;

/**
 * Controls a camera preview while asynchronously scanning for a QR code. If a code is
 * detected, the decoded data is fed into static U2F state.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/22/16
 */
public class ScanActivity extends Activity implements SurfaceHolder.Callback {

    private Camera camera;
    private Scanner scanner;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan);

        scanner = new Scanner() {

            @Override
            public void onPostExecute(String result) {
                Log.i("ScanActivity", "Read QR: " + result);
                onQRScanned(result);
            }

        };

    }

    /**
     * Registers this class as the callback for the image surface in the layout XML.
     */
    @Override
    public void onStart() {

        super.onStart();
        ((SurfaceView) findViewById(R.id.camera_preview)).getHolder().addCallback(this);

    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        Log.d("MainActivity", "Surface created.");

        try {

            // Open the camera.
            camera = Camera.open();
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(scanner);
            scanner.execute();
            System.out.println("Connected QR scan task.");

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
        Parameters params = camera.getParameters();
        List<String> modes = params.getSupportedFocusModes();
        if (modes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            params.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        else if (modes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            params.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        else if (modes.contains(Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Parameters.FOCUS_MODE_AUTO);
        }
        camera.setParameters(params);
    }

    /**
     * If the preview surface is altered for some reason, fixes it and refreshes the preview.
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        System.out.println("Surface changed.");

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

        System.out.println("Surface destroyed.");

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

        if (content != null) {

            Intent intent = new Intent();
            intent.putExtra("qr_content", content);
            setResult(RESULT_OK, intent);
            finish();

        } else {

            setResult(RESULT_CANCELED);
            finish();

        }

    }

}