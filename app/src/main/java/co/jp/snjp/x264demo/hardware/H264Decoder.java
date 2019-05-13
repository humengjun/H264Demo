package co.jp.snjp.x264demo.hardware;

import android.media.Image;
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

import co.jp.snjp.x264demo.utils.YuvConvertTools;

public class H264Decoder {

    private final static String TAG = "H264Decoder";

    private final int SDK_VERSION_CODES = Build.VERSION_CODES.M;

    private byte[] dataSources;

    private MediaCodec mMediaCodec;

    private MediaFormat mMediaFormat;

    private MediaCodec.Callback mCallback;

    private Handler mH264DecoderHandler;

    private DecoderRunnable decoderRunnable;

    private IResponse iResponse;

    private ExecutorService singleService;

    private int generateIndex = 0;

    private final int DEFAULT_FRAMERATE = 1;

    private LinkedList<byte[]> mInputDataList = new LinkedList<>();

    private boolean isNew;

    private boolean isI420;

    private int width;

    private int height;

    private int color_format;

    public void addDataSource(byte[] dataSource) {
        mInputDataList.add(dataSource);
    }

    public H264Decoder(String mimeType, int width, int height, boolean isNew, IResponse iResponse) {
        this.isNew = isNew;
        this.iResponse = iResponse;
        this.width = width;
        this.height = height;
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mimeType);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            mMediaCodec = null;
            return;
        }

        color_format = getSupportColorFormat();

        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
//        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 3);
//        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
//        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color_format);

        if (Build.VERSION.SDK_INT >= SDK_VERSION_CODES) {
            HandlerThread mH264DecoderHandlerThread = new HandlerThread("H264Decoder");
            mH264DecoderHandlerThread.start();
            mH264DecoderHandler = new Handler(mH264DecoderHandlerThread.getLooper());
            initCallback();
        } else {
            singleService = Executors.newSingleThreadExecutor();
            initRunnable();
            mMediaCodec.configure(mMediaFormat, null, null, 0);
            mMediaCodec.start();
        }
    }

    /**
     * 开启异步解码
     */
    public void startDecoderFromAsync() {
        if (mMediaCodec != null) {
            if (Build.VERSION.SDK_INT >= SDK_VERSION_CODES) {
                mMediaCodec.setCallback(mCallback, mH264DecoderHandler);
                mMediaCodec.configure(mMediaFormat, null, null, 0);
                mMediaCodec.start();
            } else {
                singleService.execute(decoderRunnable);
            }
        } else {
            throw new IllegalArgumentException("startDecoder failed, please check the MediaCodec is init correct");
        }
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

    public void stopDecoder() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
        }
    }

    /**
     * release all resource that used in Decoder
     */
    public void release() {
        if (mMediaCodec != null) {
            mMediaCodec.release();
            mMediaCodec = null;
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
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(id);
                    if (inputBuffer == null)
                        return;

                    inputBuffer.clear();
                    inputBuffer.put(dataSources);
                    int flag = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    if (mInputDataList.size() == 0) {
                        flag = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    }

                    mediaCodec.queueInputBuffer(id, 0, dataSources.length, computePresentationTime(generateIndex), flag);
                    generateIndex++;
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int id, @NonNull MediaCodec.BufferInfo bufferInfo) {
                try {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                            color_format != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                        //COLOR_FormatYUV420Flexible颜色格式不支持此方法解码
                        Image image = mMediaCodec.getOutputImage(id);
                        if (image != null) {
                            byte[] yuvData = ImageHelper.getDataFromImage(image, ImageHelper.COLOR_FormatI420);
                            if (iResponse != null) {
                                iResponse.onResponse(1, yuvData);
                            }
                        }
                    } else {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(id);
                        int yuvSize = width * height * 3 / 2;
                        if (outputBuffer != null && bufferInfo.size >= yuvSize) {
                            byte[] buffer = new byte[yuvSize];
                            outputBuffer.get(buffer);
                            if (iResponse != null) {
                                if (!isI420) {
                                    buffer = YuvConvertTools.NV12toI420(buffer);
                                }
                                iResponse.onResponse(1, buffer);
                            }
                        }
                    }
                    mediaCodec.releaseOutputBuffer(id, false);
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
                Log.d(TAG, "------> onError");
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
                Log.d(TAG, "------> onOutputFormatChanged");
            }
        };
    }

    /**
     * 向上兼容的方法，6.0以前的版本适用
     */
    private void initRunnable() {
        decoderRunnable = new DecoderRunnable();
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / DEFAULT_FRAMERATE;
    }

    public interface IResponse {
        void onResponse(int code, byte[] yuvData);
    }

    class DecoderRunnable implements Runnable {
        @Override
        public void run() {
            int yuvSize = width * height * 3 / 2;
            byte[] yuvData = null;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int count = mInputDataList.size();
            while (count > 0) {
                try {
                    dataSources = mInputDataList.poll();
                    ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                    ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
                    if (dataSources != null) {
                        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                        int flag = 0;
                        if (mInputDataList.size() == 0)
                            flag = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                                color_format != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                            //COLOR_FormatYUV420Flexible颜色格式不支持此方法解码
                            Image image = mMediaCodec.getOutputImage(outputBufferIndex);
                            if (image != null) {
                                yuvData = ImageHelper.getDataFromImage(image, ImageHelper.COLOR_FormatI420);
                                if (iResponse != null) {
                                    iResponse.onResponse(1, yuvData);
                                    count--;
                                }
                            }
                        } else {
                            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                            if (outputBuffer != null && bufferInfo.size >= yuvSize) {
                                yuvData = new byte[yuvSize];
                                outputBuffer.get(yuvData, 0, yuvData.length);
                                if (iResponse != null) {
                                    if (!isI420) {
                                        yuvData = YuvConvertTools.NV12toI420(yuvData);
                                    }
                                    iResponse.onResponse(1, yuvData);
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