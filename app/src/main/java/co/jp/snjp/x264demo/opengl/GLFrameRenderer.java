package co.jp.snjp.x264demo.opengl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLFrameRenderer implements Renderer {

    private ISimplePlayer mParentAct;
    private GLSurfaceView mTargetSurface;
    private GLProgram prog = new GLProgram(0);
    //    private int mScreenWidth, mScreenHeight;
    private int mVideoWidth, mVideoHeight;

    private int screenWidth, screenheight;
    private ByteBuffer y;
    private ByteBuffer u;
    private ByteBuffer v;

    GLRGB2YUV glrgb2YUV;

    GLYUV2RGB glyuv2RGB;

    private Rgb2YuvCallback rgb2YuvCallback;

    private Yuv2RgbCallback yuv2RgbCallback;

    public GLFrameRenderer(ISimplePlayer callback, GLSurfaceView surface) {
        mParentAct = callback;
        mTargetSurface = surface;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Utils.LOGD("GLFrameRenderer :: onSurfaceCreated");
        if (glrgb2YUV == null)
            glrgb2YUV = new GLRGB2YUV(mVideoWidth, mVideoHeight);

        if (glyuv2RGB == null)
            glyuv2RGB = new GLYUV2RGB(mVideoWidth, mVideoHeight);

        if (!prog.isProgramBuilt()) {
            prog.buildProgram();
            Utils.LOGD("GLFrameRenderer :: buildProgram done");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Utils.LOGD("GLFrameRenderer :: onSurfaceChanged");
        GLES20.glViewport(0, 0, width, height);
        screenWidth = width;
        screenheight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {

            //回调yuv格式数据
            if (rgb2YuvCallback != null) {
                rgb2YuvCallback.onResult(glrgb2YUV.startCoverImg());
                rgb2YuvCallback = null;
                return;
            }

            //回调rgb格式数据
            if (yuv2RgbCallback != null) {
                yuv2RgbCallback.onResult(glyuv2RGB.startCoverImg());
                yuv2RgbCallback = null;
                return;
            }

            if (y != null) {
                // reset position, have to be done
                y.position(0);
                u.position(0);
                v.position(0);
                prog.buildTextures(y, u, v, mVideoWidth, mVideoHeight);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                if (screenWidth != 0 && screenheight != 0)
                    GLES20.glViewport(0, 0, screenWidth, screenheight);
                prog.drawFrame();
            }
        }
    }

    /**
     * this method will be called from native code, it happens when the video is about to play or
     * the video size changes.
     */
    public void update(int w, int h) {
        Utils.LOGD("INIT E");
        if (w > 0 && h > 0) {
            // 调整比例
            if (1280 > 0 && 720 > 0) {
                float f1 = 1f * 720 / 1280;
                float f2 = 1f * h / w;
                if (f1 == f2) {
                    prog.createBuffers(GLProgram.squareVertices);
                } else if (f1 < f2) {
                    float widScale = f1 / f2;
                    prog.createBuffers(new float[]{-widScale, -1.0f, widScale, -1.0f, -widScale, 1.0f, widScale,
                            1.0f,});
                } else {
                    float heightScale = f2 / f1;
                    prog.createBuffers(new float[]{-1.0f, -heightScale, 1.0f, -heightScale, -1.0f, heightScale, 1.0f,
                            heightScale,});
                }
            }
            // 初始化容器
            if (w != mVideoWidth && h != mVideoHeight) {
                this.mVideoWidth = w;
                this.mVideoHeight = h;
                int yarraySize = w * h;
                int uvarraySize = yarraySize / 4;
                synchronized (this) {
                    y = ByteBuffer.allocate(yarraySize);
                    u = ByteBuffer.allocate(uvarraySize);
                    v = ByteBuffer.allocate(uvarraySize);
                }
            }
        }

        if (mParentAct != null) {
            mParentAct.onPlayStart();
        }
        Utils.LOGD("INIT X");
    }

    /**
     * this method will be called from native code, it's used for passing yuv data to me.
     */
    public void update(byte[] ydata, byte[] udata, byte[] vdata) {
        synchronized (this) {
            y.clear();
            u.clear();
            v.clear();
            y.put(ydata, 0, ydata.length);
            u.put(udata, 0, udata.length);
            v.put(vdata, 0, vdata.length);
        }

        // request to render
        mTargetSurface.requestRender();
    }

    /**
     * this method will be called from native code, it's used for passing play state to activity.
     */
    public void updateState(int state) {
        Utils.LOGD("updateState E = " + state);
        if (mParentAct != null) {
            mParentAct.onReceiveState(state);
        }
        Utils.LOGD("updateState X");
    }

    /**
     * rgb转yuv
     *
     * @param rgb
     * @param width
     * @param height
     * @param callback
     */
    public void rgb2yuv(byte[] rgb, int width, int height, Rgb2YuvCallback callback) {
        if (glrgb2YUV == null)
            return;
        this.rgb2YuvCallback = callback;
        glrgb2YUV.update(width, height);
        glrgb2YUV.putRgbData(rgb);

        mTargetSurface.requestRender();
    }

    public void yuv2rgb(byte[] yuv, int width, int height, Yuv2RgbCallback callback) {
        if (glyuv2RGB == null)
            return;
        this.yuv2RgbCallback = callback;
        glyuv2RGB.update(width, height);
        glyuv2RGB.putYuvData(yuv);

        mTargetSurface.requestRender();
    }


    public interface Rgb2YuvCallback {
        void onResult(byte[] yuvData);
    }

    public interface Yuv2RgbCallback {
        void onResult(byte[] rgbData);
    }
}
