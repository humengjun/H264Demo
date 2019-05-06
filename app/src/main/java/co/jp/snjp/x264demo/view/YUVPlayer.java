package co.jp.snjp.x264demo.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

public class YUVPlayer extends GLSurfaceView implements SurfaceHolder.Callback {
    static {
        System.loadLibrary("native-lib");
    }

    private boolean isCreated;

    public YUVPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isCreated = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isCreated = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

    }

    public void showYuv(String yuvPath, int yuvWidth, int yuvHeight) {
        if (isCreated)
            Open(yuvPath, getHolder().getSurface(), yuvWidth, yuvHeight);
    }

    public native void Open(String url, Object surface, int yuvWidth, int yuvHeight);


}
