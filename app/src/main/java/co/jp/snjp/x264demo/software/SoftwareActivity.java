package co.jp.snjp.x264demo.software;

import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import co.jp.snjp.x264demo.R;
import co.jp.snjp.x264demo.utils.Bmp2YuvTools;
import co.jp.snjp.x264demo.utils.FileUtils;
import co.jp.snjp.x264demo.utils.PermissionUtils;
import example.sszpf.x264.x264sdk;

public class SoftwareActivity extends AppCompatActivity {


    private x264sdk x264;

    private int width = 1280;

    private int height = 720;

    private int fps = 30;

    private int bitrate = 90000;

    private int timespan = 90000 / fps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_software);

        PermissionUtils.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, 1);

        initX264Sdk();
    }

    private void initX264Sdk() {
        x264 = new x264sdk(listener);
        x264.initX264Encode(width, height, fps, bitrate);

        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream inputStream = getAssets().open("orange.bmp");
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    byte[] b = new byte[1024];
                    int len = 0;
                    while ((len = inputStream.read(b)) != -1) {
                        outputStream.write(b, 0, len);
                    }

                    byte[] bmpData = outputStream.toByteArray();

                    inputStream.close();
                    outputStream.close();

                    byte[] yuv420 = Bmp2YuvTools.convertI420(bmpData, width, height);

                    x264.PushOriStream(yuv420, yuv420.length, timespan);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 3000);

    }

    private x264sdk.listener listener = new x264sdk.listener() {

        @Override
        public void h264data(byte[] buffer, int length) {
            File file = new File(Environment.getExternalStorageDirectory().toString() + "/software.264");
            FileUtils.writeFile(file, buffer, false);
        }
    };
}
