package com.terainsights.a2q2r_android.util;

import android.hardware.Camera;
import android.os.AsyncTask;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An asynchronous task which scans a camera preview for QR codes.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/28/16
 */
public class Scanner extends AsyncTask<Void, Void, String> implements Camera.PreviewCallback {

    private static class Data {
        public byte[] data;
        Camera.Size   size;
    }

    private final BlockingQueue<Data> mBlockingQueue;
    private final Reader mReader;

    private final long start;
    private final long timeout;

    public Scanner(int secondsUntilTimeout) {

        mBlockingQueue = new LinkedBlockingQueue<>(5);
        mReader = new QRCodeReader();

        start = System.currentTimeMillis();
        timeout = (long) secondsUntilTimeout * 1000;

    }

    @Override
    protected String doInBackground(Void... args) {

        while (System.currentTimeMillis() - start < timeout) {

            try {
                Thread.currentThread().setName("Async Scan Task");
                Data data = mBlockingQueue.take();
                LuminanceSource ls = new PlanarYUVLuminanceSource(
                        data.data, data.size.width, data.size.height,
                        0, 0, data.size.width, data.size.height, false);
                Result r = mReader.decode(new BinaryBitmap(new HybridBinarizer(ls)));
                return r.getText();
            } catch (InterruptedException e) {
                return null;
            } catch (NotFoundException e) {
            } catch (ChecksumException e) {
            } catch (FormatException e) {
            } catch (ArrayIndexOutOfBoundsException e) {
            } finally {
                mReader.reset();
            }

        }

        return null;

    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Data d = new Data();
        d.data = data;
        d.size = camera.getParameters().getPreviewSize();
        mBlockingQueue.offer(d);
    }

}