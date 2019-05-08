package co.jp.snjp.x264demo;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.nio.ByteBuffer;

import co.jp.snjp.x264demo.display.GLES20Support;
import co.jp.snjp.x264demo.display.GLFrameRenderer;
import co.jp.snjp.x264demo.display.GLFrameSurface;
import co.jp.snjp.x264demo.hardware.FileSelectionDialog;
import co.jp.snjp.x264demo.utils.FileUtils;
import co.jp.snjp.x264demo.view.YUVPlayer;

public class YuvPlayActivity extends AppCompatActivity implements FileSelectionDialog.OnFileSelectListener {

    private YUVPlayer player;

    private TextView textView;

    //HYWAY编码，HYWAY解码
    private final String HYWAY_HYWAY_YUV_PATH = Environment.getExternalStorageDirectory().toString() + "/hyway_hyway_yuv/";

    GLFrameSurface surface;

    GLFrameRenderer renderer;

    long startTime;

    EditText edit_width, edit_height;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_yuv_play);

        if (!GLES20Support.detectOpenGLES20(this)) {
            GLES20Support.getNoSupportGLES20Dialog(this).show();
        }

        initView();
    }

    private void initView() {
//        player = findViewById(R.id.play);
        surface = findViewById(R.id.play);

        surface.setEGLContextClientVersion(2);
        //
        renderer = new GLFrameRenderer(null, surface);
        // set our renderer to be the main renderer with
        // the current activity context
        surface.setRenderer(renderer);

        textView = findViewById(R.id.time);

        edit_width = findViewById(R.id.width);
        edit_height = findViewById(R.id.height);

        findViewById(R.id.showYuv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileSelectionDialog dlg = new FileSelectionDialog(YuvPlayActivity.this, YuvPlayActivity.this);
                dlg.show(new File(HYWAY_HYWAY_YUV_PATH));
            }
        });
    }

    @Override
    public void onFileSelect(final File file) {
        if (file.getName().endsWith(".yuv")) {
//            new Thread() {
//                @Override
//                public void run() {
//                    player.showYuv(file.getAbsolutePath(), 1280, 720);
//                }
//            }.start();
            startTime = System.currentTimeMillis();

            int height = 720;
            int width = 1280;
            try {
                height = Integer.parseInt(edit_height.getText().toString());
                width = Integer.parseInt(edit_width.getText().toString());
            } catch (Exception ignored) {
            }

            renderer.update(width, height);

            byte[] yuv = FileUtils.readFile4Bytes(file);

            copyFrom(yuv, width, height);

            byte[] y = new byte[yuvPlanes[0].remaining()];
            yuvPlanes[0].get(y, 0, y.length);

            byte[] u = new byte[yuvPlanes[1].remaining()];
            yuvPlanes[1].get(u, 0, u.length);

            byte[] v = new byte[yuvPlanes[2].remaining()];
            yuvPlanes[2].get(v, 0, v.length);

            renderer.update(y, u, v);

            textView.setText("show time:" + (System.currentTimeMillis() - startTime));

        }

    }

    public ByteBuffer[] yuvPlanes;

    public void copyFrom(byte[] yuvData, int width, int height) {

        int[] yuvStrides = {width, width / 2, width / 2};

        if (yuvPlanes == null) {
            yuvPlanes = new ByteBuffer[3];
            yuvPlanes[0] = ByteBuffer.allocateDirect(yuvStrides[0] * height);
            yuvPlanes[1] = ByteBuffer.allocateDirect(yuvStrides[1] * height / 2);
            yuvPlanes[2] = ByteBuffer.allocateDirect(yuvStrides[2] * height / 2);
        }

        if (yuvData.length < width * height * 3 / 2) {
            throw new RuntimeException("Wrong arrays size: " + yuvData.length);
        }

        int planeSize = width * height;

        ByteBuffer[] planes = new ByteBuffer[3];

        planes[0] = ByteBuffer.wrap(yuvData, 0, planeSize);
        planes[1] = ByteBuffer.wrap(yuvData, planeSize, planeSize / 4);
        planes[2] = ByteBuffer.wrap(yuvData, planeSize + planeSize / 4, planeSize / 4);

        for (int i = 0; i < 3; i++) {
            yuvPlanes[i].position(0);
            yuvPlanes[i].put(planes[i]);
            yuvPlanes[i].position(0);
            yuvPlanes[i].limit(yuvPlanes[i].capacity());
        }
    }
}
