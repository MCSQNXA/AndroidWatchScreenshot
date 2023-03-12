package mcsq.nxa.screenshot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {


    private MediaProjectionManager mediaProjectionManager;


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 10001 && data != null) {
            MediaProjection mediaProjection = this.mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data);

            new Thread(() -> {
                try {
                    Thread.sleep(6 * 1000);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                super.runOnUiThread(() -> {
                    try {
                        FileOutputStream fileOutputStream = new FileOutputStream("/sdcard/Screenshot.png");

                        this.screenShot(mediaProjection).compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);

                        fileOutputStream.flush();
                        fileOutputStream.close();
                    } catch (Exception e) {
                        try {
                            FileOutputStream fileOutputStream = new FileOutputStream("/sdcard/Screenshot.log");
                            fileOutputStream.write(Log.getStackTraceString(e).getBytes());
                            fileOutputStream.flush();
                            fileOutputStream.close();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                });
            }).start();
        }
    }

    public Bitmap screenShot(MediaProjection mediaProjection) {
        WindowManager wm1 = this.getWindowManager();
        int width = wm1.getDefaultDisplay().getWidth();
        int height = wm1.getDefaultDisplay().getHeight();

        @SuppressLint("WrongConstant")
        ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGB_565, 1);
        VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay("screen", width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
        SystemClock.sleep(1000);

        Image image = imageReader.acquireLatestImage();

        virtualDisplay.release();

        width = image.getWidth();
        height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.RGB_565);
        bitmap.copyPixelsFromBuffer(buffer);

        image.close();

        return bitmap;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {//11
            if (!Environment.isExternalStorageManager()) {
                super.startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).setData(Uri.parse("package:" + super.getPackageName())), 1024);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {//6.0
            String[] uses = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE};

            for (String p : uses) {
                if (super.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    super.requestPermissions(uses, 0XCF);
                    break;
                }
            }
        }

        super.startService(new Intent(this, ForegroundService.class));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.mediaProjectionManager = (MediaProjectionManager) super.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            super.startActivityForResult(this.mediaProjectionManager.createScreenCaptureIntent(), 10001);

        }


    }

}
