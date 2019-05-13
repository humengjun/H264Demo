package co.jp.snjp.x264demo.hardware;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.jp.snjp.x264demo.utils.Bmp2YuvTools;


public class H264Encoder {

    private final static String TAG = "H264Encoder";

    private final int SDK_VERSION_CODES = Build.VERSION_CODES.M;

    private byte[] configByte;

    private byte[] dataSources;

    private MediaCodec mMediaCodec;

    private MediaFormat mMediaFormat;

    private MediaCodec.Callback mCallback;

    private Handler mH264EncoderHandler;

    private EncoderRunnable encoderRunnable;

    private IResponse iResponse;

    private ExecutorService cacheService;

    private ExecutorService singleService;

    private int width;

    private int height;

    private int generateIndex = 0;

    private final int DEFAULT_FRAMERATE = 1;

    private final int DEFAULT_I_FRAME_INTERVAL = 1;

    private final int DEFAULT_KEY_BIT_RATE = 2500 * 1000 / 30;//1280*720推荐码率

    private LinkedList<byte[]> mInputDataList = new LinkedList<>();

    private boolean isI420;

    private boolean isNew;

    public void addDataSource(byte[] dataSource) {
        mInputDataList.add(dataSource);
    }

    public H264Encoder(String mimeType, int compressRatio, int width, int height, boolean isNew, IResponse iResponse) {
        this.isNew = isNew;
        this.iResponse = iResponse;
        this.width = width;
        this.height = height;
        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        //码率越低，图片越模糊
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 3 / 2 * compressRatio / 100);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, DEFAULT_FRAMERATE);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL);
        int color_format = getSupportColorFormat();
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color_format);

        try {
            mMediaCodec = MediaCodec.createEncoderByType(mimeType);
        } catch (IOException e) {
            mMediaCodec = null;
            return;
        }

        if (Build.VERSION.SDK_INT >= SDK_VERSION_CODES) {
            /**
             * BITRATE_MODE_CQ：恒定质量
             * BITRATE_MODE_VBR：可变码率
             * BITRATE_MODE_CBR：恒定码率
             */
//            mMediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE,MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            /**
             * 设置H264 Profile
             */
//            mMediaFormat.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
            /**
             * 设置H264 Level
             */
//            mMediaFormat.setInteger(MediaFormat.KEY_LEVEL,MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41);

            HandlerThread mH264EncoderHandlerThread = new HandlerThread("H264Encoder");
            mH264EncoderHandlerThread.start();
            mH264EncoderHandler = new Handler(mH264EncoderHandlerThread.getLooper());
            initCallback();
        } else {
            singleService = Executors.newSingleThreadExecutor();
            initRunnable();
            mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        }
    }

    /**
     * 开始硬件编码
     */
    public void startEncoderFromAsync() {
        if (mMediaCodec != null) {
            if (Build.VERSION.SDK_INT >= SDK_VERSION_CODES) {
                mMediaCodec.setCallback(mCallback, mH264EncoderHandler);
                mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mMediaCodec.start();
            } else {
                singleService.execute(encoderRunnable);
            }
        } else {
            throw new IllegalArgumentException("startEncoder failed, please check the MediaCodec is init correct");
        }
    }


    /**
     * 向下兼容的方法，6.0及以后的版本适用
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initCallback() {
        mCallback = new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int id) {
                try {
                    dataSources = mInputDataList.poll();
                    if (dataSources == null)
                        return;
                    //输入为yuv格式
                    if (isI420) {
                        dataSources = Bmp2YuvTools.convertI420(dataSources, width, height);
                    } else {
                        dataSources = Bmp2YuvTools.convertNV12(dataSources, width, height);
                    }
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(id);
                    if (inputBuffer == null)
                        return;

                    inputBuffer.clear();
                    inputBuffer.put(dataSources);

                    int flag = MediaCodec.BUFFER_FLAG_KEY_FRAME;
//                if (mInputDataList.size() == 0) {
//                    flag = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
//                }
                    mediaCodec.queueInputBuffer(id, 0, dataSources.length, computePresentationTime(generateIndex), flag);
                    generateIndex++;
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int id, @NonNull MediaCodec.BufferInfo bufferInfo) {
                try {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(id);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        byte[] buffer = new byte[bufferInfo.size];
                        outputBuffer.get(buffer);
                        mediaCodec.releaseOutputBuffer(id, false);

                        if (bufferInfo.flags == 2) {
                            configByte = new byte[bufferInfo.size];
                            configByte = buffer;
                        } else { //if (bufferInfo.flags == 1) {
                            byte[] keyframe = new byte[bufferInfo.size + configByte.length];
                            System.arraycopy(configByte, 0, keyframe, 0, configByte.length);
                            System.arraycopy(buffer, 0, keyframe, configByte.length, buffer.length);
                            if (iResponse != null)
                                iResponse.onResponse(2, keyframe);
                        }
                    }
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onError(@NonNull MediaCodec
                                        mediaCodec, @NonNull MediaCodec.CodecException e) {
                Log.d(TAG, "------> onError");
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec
                                                      mediaCodec, @NonNull MediaFormat mediaFormat) {
                Log.d(TAG, "------> onOutputFormatChanged");
            }
        }

        ;
    }

    /**
     * 向上兼容的方法，6.0以前的版本适用
     */
    private void initRunnable() {
        encoderRunnable = new EncoderRunnable();
    }

    private int getSupportColorFormat() {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo codecInfo = null;
        for (int i = 0; i < numCodecs && codecInfo == null; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            boolean found = false;
            for (int j = 0; j < types.length && !found; j++) {
                if (types[j].equals("video/avc")) {
                    System.out.println("found");
                    found = true;
                }
            }
            if (!found)
                continue;
            codecInfo = info;
        }

        // Find a color profile that the codec supports
        if (codecInfo == null)
            return 0;
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");

        if (isNew) {
            //优先使用新版本的API，兼容性差，解码效果更好
            for (int i = 0; i < capabilities.colorFormats.length; i++) {

                switch (capabilities.colorFormats[i]) {
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                        isI420 = true;
                        return capabilities.colorFormats[i];
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                        return capabilities.colorFormats[i];
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                        isI420 = true;
                        return capabilities.colorFormats[i];
                    default:
                        Log.d("AvcEncoder", "other color format " + capabilities.colorFormats[i]);
                        break;
                }
            }
        } else {
            //优先使用旧版本API，兼容性强，解码效果较差(背景是灰色)
            for (int i = capabilities.colorFormats.length - 1; i >= 0; i--) {

                switch (capabilities.colorFormats[i]) {
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                        isI420 = true;
                        return capabilities.colorFormats[i];
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                        return capabilities.colorFormats[i];
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                        isI420 = true;
                        return capabilities.colorFormats[i];
                    default:
                        Log.d("AvcEncoder", "other color format " + capabilities.colorFormats[i]);
                        break;
                }
            }
        }

        return 0;
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / DEFAULT_FRAMERATE;
    }

    /**
     * 停止硬件编码
     */
    public void stopEncoder() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
        }
    }

    /**
     * release all resource that used in Encoder
     */
    public void release() {
        if (mMediaCodec != null) {
            mMediaCodec.release();
            mMediaCodec = null;
        }
    }

    public interface IResponse {
        void onResponse(int code, byte[] yuvData);
    }

    class EncoderRunnable implements Runnable {
        @Override
        public void run() {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int count = mInputDataList.size();
            while (count > 0) {
                dataSources = mInputDataList.poll();
                ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
                try {
                    if (dataSources != null) {
                        //输入为yuv格式
                        if (isI420) {
                            dataSources = Bmp2YuvTools.convertI420(dataSources, width, height);
                        } else {
                            dataSources = Bmp2YuvTools.convertNV12(dataSources, width, height);
                        }
                        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                        int flag = MediaCodec.BUFFER_FLAG_KEY_FRAME;
//                        if (mInputDataList.size() == 0)
//                            flag = MediaCodec.BUFFER_FLAG_END_OF_STREAM;

                        if (inputBufferIndex >= 0) {
                            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            inputBuffer.put(dataSources);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, dataSources.length, computePresentationTime(generateIndex), flag);
                            generateIndex++;
                        }
                    }

                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    while (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);

                            if (bufferInfo.flags == 2) {
                                configByte = new byte[bufferInfo.size];
                                configByte = outData;
                            } else { //if (bufferInfo.flags == 1) {
                                byte[] keyframe = new byte[bufferInfo.size + configByte.length];
                                System.arraycopy(configByte, 0, keyframe, 0, configByte.length);
                                System.arraycopy(outData, 0, keyframe, configByte.length, outData.length);
                                if (iResponse != null) {
                                    iResponse.onResponse(2, keyframe);
                                    count--;
                                }
                            }
                        }

                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);

                    }

                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }


}