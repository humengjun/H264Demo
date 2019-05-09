package co.jp.snjp.x264demo.opengl;

import android.opengl.GLES20;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import co.jp.snjp.x264demo.app.H264Application;

/**
 * Created by Administrator on 2017/3/15 0015.
 */

public class GLYUV2RGB {
    //
    private int picWidth = 0;
    private int picHeight = 0;
    //Gl framebuffer 缓冲及对应的缓冲纹理Id
    private int[] FBO = new int[1];
    private int[] FBOTexId = new int[1];
    //着色器程序id
    private int GLProgramId = -1;
    //接收输入的图像的纹理id 及对应的纹理标识
    //yuv纹理ID
    private int[] InYTexId = new int[1];
    private int[] InUTexId = new int[1];
    private int[] InVTexId = new int[1];
    //着色器脚本
    private String FRAGMENT_SHADER = null; //片元着色器
    private String VERTEX_SHADER = null;  //顶点着色器
    //着色器程序中的句柄
    private int Handle_position = -1, Handle_coord = -1, Handle_cmatx = -1;//顶点，纹理，旋转矩阵句柄
    private int Handle_y = -1, Handle_u = -1, Handle_v = -1; //yuv纹理句柄

    //Buffer
    private ByteBuffer Buffer_vertice = null;//顶点坐标BUFFER
    private ByteBuffer Buffer_coord = null; //纹理坐标BUFFER
    private ByteBuffer Bytebuf_ydata = null; //rgb数据buffer
    private ByteBuffer Bytebuf_udata = null; //rgb数据buffer
    private ByteBuffer Bytebuf_vdata = null; //rgb数据buffer
    //顶点坐标
    private float[] VerticesPoint = {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f,}; // 顶点坐标
    private float[] CoordPoint = {0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,};// 纹理坐标 Z
    //翻转矩阵？ 将Y坐标翻转
    private static float[] CMartix = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f}; // 反转Y轴坐标 ，使输出的图像为正向

    //1.初始化 着色器，获取handle，创建缓冲区 传入原图的大小
    public GLYUV2RGB(int width, int height) {
        //0.初始化一些变量
        FBO[0] = -1;
        FBOTexId[0] = -1;
        InYTexId[0] = -1;
        InUTexId[0] = -1;
        InVTexId[0] = -1;

        picWidth = width;
        picHeight = height;
        //1.载入着色器脚本
        buildProgram();
        //2.获取程序的句柄
        getHandles();
        //3.创建缓冲区
        if (Bytebuf_ydata == null)
            Bytebuf_ydata = ByteBuffer.allocate(picWidth * picHeight);
        if (Bytebuf_udata == null)
            Bytebuf_udata = ByteBuffer.allocate(picWidth * picHeight / 4);
        if (Bytebuf_vdata == null)
            Bytebuf_vdata = ByteBuffer.allocate(picWidth * picHeight / 4);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    //载入顶点着色器和片元着色器脚本程序
    public void buildProgram() {
        if (GLProgramId <= 0) {
            byte tmp[] = null;
            /*载入片元着色器脚本*/
            try {
                //得到资源中的asset数据流
                InputStream in = H264Application.getInstance().getAssets().open("yuv2rgb/yuv2rgb.farg");
                tmp = new byte[in.available()];

                in.read(tmp);
                FRAGMENT_SHADER = new String(tmp, "UTF-8");
                in.close();
                //得到资源中的asset数据流
                in = H264Application.getInstance().getResources().getAssets().open("yuv2rgb/yuv2rgb.vert");
                tmp = new byte[in.available()];

                in.read(tmp);
                VERTEX_SHADER = new String(tmp, "UTF-8");
                in.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
            GLProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (GLProgramId == 0) {
                System.out.println("着色器程序编译失败!!");
            }
        }
        Utils.LOGD("_program = " + FBOTexId);
    }

    //从着色器程序拿句柄
    private void getHandles() {
        /*
         * 顶点着色器脚本句柄
         */
        Handle_position = GLES20.glGetAttribLocation(GLProgramId, "vPosition");//顶点
        Handle_coord = GLES20.glGetAttribLocation(GLProgramId, "a_texCoord");//纹理
        Handle_cmatx = GLES20.glGetUniformLocation(GLProgramId, "vMatrixs");//旋转矩阵

        /*
         * 片元着色器脚本句柄
         */
        Handle_y = GLES20.glGetUniformLocation(GLProgramId, "tex_y");
        checkGlError("glGetUniformLocation tex_y");
        if (Handle_y == -1) {
            throw new RuntimeException("Could not get uniform location for tex_y");
        }
        Handle_u = GLES20.glGetUniformLocation(GLProgramId, "tex_u");
        Handle_v = GLES20.glGetUniformLocation(GLProgramId, "tex_v");
    }

    //创建各种缓冲区
    private void createAllBuffer() {
        //1.顶点，纹理坐标buffer
        if (Buffer_vertice == null)
            Buffer_vertice = createBuffer(VerticesPoint);//顶点坐标BUFFER
        if (Buffer_coord == null)
            Buffer_coord = createBuffer(CoordPoint); //纹理坐标BUFFER
        //2.framebuffer
        //建个fbo
        //1个framebuffer
        if (FBO[0] == -1) {
            GLES20.glGenFramebuffers(1, FBO, 0);
        }
        /////////////////////////////////////////////////////
        if (FBOTexId[0] == -1) {
            GLES20.glGenTextures(1, FBOTexId, 0);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, FBOTexId[0]);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, picWidth, picHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);//framebuff对应的纹理图片

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //先将 绑定fb0和fbo纹理
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, FBOTexId[0], 0);//绑定framebuffer和对应的纹理id
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        //3.普通纹理id
        // building texture for yuv data
        if (InYTexId[0] == -1) {
            GLES20.glGenTextures(1, InYTexId, 0);
            checkGlError("glGenTextures");
        }
        if (InUTexId[0] == -1)
            GLES20.glGenTextures(1, InUTexId, 0);

        if (InVTexId[0] == -1)
            GLES20.glGenTextures(1, InVTexId, 0);
        //Y
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InYTexId[0]);
        checkGlError("glBindTexture");
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);//GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); //GLES20.GL_LINEAR
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //U
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InUTexId[0]);
        checkGlError("glBindTexture");
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);//GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //V
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InVTexId[0]);
        checkGlError("glBindTexture");
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);//GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    //获取FBO的纹理ID
    public int getFboTexId() {
        return FBOTexId[0];
    }


    //2.设置一张原图
    public void putYuvData(byte[] yuvdata) {
        //转化byte数组为buffer
        Bytebuf_ydata.clear();
        Bytebuf_ydata.position(0);
        Bytebuf_ydata.put(yuvdata, 0, picWidth * picHeight);

        Bytebuf_udata.clear();
        Bytebuf_udata.position(0);
        Bytebuf_udata.put(yuvdata, picWidth * picHeight, picWidth * picHeight / 4);

        Bytebuf_vdata.clear();
        Bytebuf_vdata.position(0);
        Bytebuf_vdata.put(yuvdata, picWidth * picHeight * 5 / 4, picWidth * picHeight / 4);
    }

    //3.执行转换一次,输出一张转换后的图
    public byte[] startCoverImg() {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glViewport(0, 0, picWidth, picHeight);//需要是实际图的大小
        createAllBuffer();
        Bytebuf_ydata.position(0);//必须，将rgb数据指针置为0，否则图像会出现循环偏移
        Bytebuf_udata.position(0);
        Bytebuf_vdata.position(0);
        //将图片写入到gl纹理中
        //Y
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InYTexId[0]);
        checkGlError("glBindTexture");
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, picWidth, picHeight, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, Bytebuf_ydata);
        checkGlError("glTexImage2D");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //U
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InUTexId[0]);
        checkGlError("glBindTexture");
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, picWidth / 2, picHeight / 2, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, Bytebuf_udata);
        checkGlError("glTexImage2D");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //V
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InVTexId[0]);
        checkGlError("glBindTexture");
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, picWidth / 2, picHeight / 2, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, Bytebuf_vdata);
        checkGlError("glTexImage2D");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);


        //////////////////////////////////////////////////////////////
        //将数据画到FBO0
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[0]);
        //2.使用此着色器程序
        GLES20.glUseProgram(GLProgramId);
        checkGlError("glUseProgram");
        //3激活纹理 同时传入3个纹理，注意 GLES20.glActiveTexture(GLES20.GL_TEXTURE0);的区别
        //Y
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InYTexId[0]);
        GLES20.glUniform1i(Handle_y, 0);
        //U
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InUTexId[0]);
        GLES20.glUniform1i(Handle_u, 1);
        //V
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, InVTexId[0]);
        GLES20.glUniform1i(Handle_v, 2);
        //4.传参画开画
        //关联内存数据到着色器程序   顶点数据
        GLES20.glVertexAttribPointer(Handle_position, 2, GLES20.GL_FLOAT, false, 8, Buffer_vertice);
        checkGlError("顶点");
        GLES20.glEnableVertexAttribArray(Handle_position);
        //关联内存数据到着色器程序  //坐标(coordinate)
        GLES20.glVertexAttribPointer(Handle_coord, 2, GLES20.GL_FLOAT, false, 8, Buffer_coord);
        checkGlError("纹理顶点");
        GLES20.glEnableVertexAttribArray(Handle_coord);

        //设置旋转矩阵
        GLES20.glUniformMatrix4fv(Handle_cmatx, 1, false, CMartix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(Handle_position);
        GLES20.glDisableVertexAttribArray(Handle_coord);

        // GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0); //恢复初始状态
        //绘制结束，开始取图
        ByteBuffer pixel = ByteBuffer.allocate(picWidth * picHeight * 4);
        //depends on the resolution of screen, about 20-50ms (1280x720)

        //YUV420只有原图rgba的1/4 + 1/8
        //读处理后fb0的图
        GLES20.glReadPixels(0, 0, picWidth, picHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                pixel);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0); //恢复初始状态 防止对纹理误操作
        byte[] byteArray = pixel.array();

        /////////////////////
        return byteArray;
    }


    public void update(int width, int height) {
        picWidth = width;
        picHeight = height;
        if (Bytebuf_ydata == null || Bytebuf_ydata.limit() != (picWidth * picHeight * 4))
            Bytebuf_ydata = ByteBuffer.allocate(picWidth * picHeight * 4);
    }

    //////////////////////////////////////////////////////////////////
    //着色器脚本载入相关
    //返回 0失败
    public int createProgram(String vertexSource, String fragmentSource) {
        // create shaders
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        // just check
        Utils.LOGD("vertexShader = " + vertexShader);
        Utils.LOGD("pixelShader = " + pixelShader);

        int program = GLES20.glCreateProgram();

        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    /**
     * create shader with given source.
     */
    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("Create");
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            checkGlError("Compile");
            if (compiled[0] == 0) {
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    //////////////////////////////////////////////////////
    //其他辅助函数
    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    //float数组变buffer
    private ByteBuffer createBuffer(float[] f1) {
        ByteBuffer tmp = null;
        tmp = ByteBuffer.allocateDirect(f1.length * 4);
        tmp.order(ByteOrder.nativeOrder());
        tmp.asFloatBuffer().put(f1);
        tmp.position(0);
        return tmp;
    }
}
