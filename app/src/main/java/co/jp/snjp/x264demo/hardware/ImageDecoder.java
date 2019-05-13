package co.jp.snjp.x264demo.hardware;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import co.jp.snjp.x264demo.utils.FileUtils;
import co.jp.snjp.x264demo.utils.YuvConvertTools;

public class ImageDecoder {

    private MediaCodec mMediaCodec;

    private MediaFormat mMediaFormat;

    private int cacheFrameCount;

    private boolean isFirstFrame = true;

    private int generateIndex = 0;

    private final int DEFAULT_FRAMERATE = 1;

    private boolean isI420;

    private boolean isNew;

    private int width;

    private int height;

    private String mimeType;

    private int color_format;


    public ImageDecoder(String mimeType, int width, int height, boolean isNew) {

        this.mimeType = mimeType;
        this.isNew = isNew;
        this.width = width;
        this.height = height;

        try {
            mMediaCodec = MediaCodec.createDecoderByType(mimeType);
        } catch (IOException e) {
            mMediaCodec = null;
            return;
        }


        color_format = getSupportColorFormat();
        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color_format);

        mMediaCodec.configure(mMediaFormat, null, null, 0);
        mMediaCodec.start();
        isFirstFrame = true;
        cacheFrameCount = 0;
        generateIndex = 0;
    }

    /**
     * 获取支持的颜色格式
     *
     * @return
     */
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
     * 解码一张图片
     *
     * @param file
     * @param isReset 是否重置解码器
     * @return
     */
    public byte[] decoderFile(File file, boolean isReset) {
        byte[] h264Data = FileUtils.readFile4Bytes(file);
        if (h264Data == null)
            return null;

        int[] w_h_data = {width, height};
        H264SPSParser.obtainH264ImageSize(h264Data, w_h_data);
        int width = w_h_data[0];
        int height = w_h_data[1];

        if (this.width != width || this.height != height)
            resetDecoder(width, height);
        else if (isReset)
            resetDecoder(width, height);

        int yuvSize = width * height * 3 / 2;
        byte[] yuvData = null;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        //针对某些设备会缓存几帧再解码，因此需要判断是否是第一帧并记录缓存的帧数。
        if (isFirstFrame) {
            while (true) {
                if (mMediaCodec != null) {
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                    int flag = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                        } else {
                            inputBuffer = mMediaCodec.getInputBuffers()[inputBufferIndex];
                        }
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            inputBuffer.put(h264Data);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, h264Data.length, computePresentationTime(generateIndex), flag);
                            cacheFrameCount++;
                            generateIndex++;
                        }
                    }

                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    while (outputBufferIndex >= 0) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP &&
                                color_format != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                            //COLOR_FormatYUV420Flexible颜色格式不支持此方法解码
                            Image image = mMediaCodec.getOutputImage(outputBufferIndex);
                            if (image != null) {
                                yuvData = ImageHelper.getDataFromImage(image, ImageHelper.COLOR_FormatI420);
                                isI420 = true;
                            }
                        } else {
                            ByteBuffer outputBuffer = null;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                            } else {
                                outputBuffer = mMediaCodec.getOutputBuffers()[outputBufferIndex];
                            }
                            if (outputBuffer != null && bufferInfo.size >= yuvSize) {
                                yuvData = new byte[yuvSize];
                                outputBuffer.get(yuvData, 0, yuvData.length);
                            }
                        }

                        cacheFrameCount--;
                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                    if (yuvData != null) {
                        break;
                    }
                }
            }
        } else {
            //根据缓存的帧数决定解码几帧，取最后一帧
            for (int i = 0; i < cacheFrameCount + 1; i++) {
                if (mMediaCodec != null) {
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                    int flag = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                        } else {
                            inputBuffer = mMediaCodec.getInputBuffers()[inputBufferIndex];
                        }
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            inputBuffer.put(h264Data);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, h264Data.length, computePresentationTime(generateIndex), flag);
                            cacheFrameCount++;
                            generateIndex++;
                        }
                    }

                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    while (outputBufferIndex >= 0) {

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP &&
                                color_format != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                            Image image = mMediaCodec.getOutputImage(outputBufferIndex);
                            if (image != null) {
                                yuvData = ImageHelper.getDataFromImage(image, ImageHelper.COLOR_FormatI420);
                                isI420 = true;
                            }
                        } else {
                            ByteBuffer outputBuffer = null;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                            } else {
                                outputBuffer = mMediaCodec.getOutputBuffers()[outputBufferIndex];
                            }
                            if (outputBuffer != null && bufferInfo.size >= yuvSize) {
                                yuvData = new byte[yuvSize];
                                outputBuffer.get(yuvData, 0, yuvData.length);
                            }
                        }

                        cacheFrameCount--;
                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                }
            }
        }

        isFirstFrame = false;
        if (!isI420 && yuvData != null) {
            yuvData = YuvConvertTools.NV12toI420(yuvData);
        }
        return yuvData;
    }

    /**
     * 图像宽高变化，需要重新设置解码器
     *
     * @param width
     * @param height
     */
    private void resetDecoder(int width, int height) {
        release();
        this.width = width;
        this.height = height;
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mimeType);
        } catch (IOException e) {
            mMediaCodec = null;
            return;
        }
        if (mMediaFormat != null) {
            mMediaFormat.setInteger(MediaFormat.KEY_WIDTH, width);
            mMediaFormat.setInteger(MediaFormat.KEY_HEIGHT, height);

            mMediaCodec.configure(mMediaFormat, null, null, 0);
            mMediaCodec.start();
            isFirstFrame = true;
            cacheFrameCount = 0;
            generateIndex = 0;
        } else {
            mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
            mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color_format);

            mMediaCodec.configure(mMediaFormat, null, null, 0);
            mMediaCodec.start();
            isFirstFrame = true;
            cacheFrameCount = 0;
            generateIndex = 0;
        }
    }

    /**
     * 停止硬件编码
     */
    public void stopDecoder() {
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

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / DEFAULT_FRAMERATE;
    }

}
