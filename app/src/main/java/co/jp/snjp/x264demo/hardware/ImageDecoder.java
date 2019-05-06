package co.jp.snjp.x264demo.hardware;

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


    public ImageDecoder(String mimeType, int width, int height, boolean isNew) {

        this.isNew = isNew;
        this.width = width;
        this.height = height;

        try {
            mMediaCodec = MediaCodec.createDecoderByType(mimeType);
        } catch (IOException e) {
            mMediaCodec = null;
            return;
        }


        int color_format = getSupportColorFormat();
        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color_format);

        mMediaCodec.configure(mMediaFormat, null, null, 0);
        mMediaCodec.start();
        isFirstFrame = true;
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

    public byte[] decoderFile(File file) {
        byte[] h264Data = FileUtils.readFile4Bytes(file);
        if (h264Data == null)
            return null;

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
                        ByteBuffer outputBuffer = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                        } else {
                            outputBuffer = mMediaCodec.getOutputBuffers()[outputBufferIndex];
                        }
                        if (outputBuffer != null && bufferInfo.size >= yuvSize) {
                            yuvData = new byte[yuvSize];
                            outputBuffer.get(yuvData, 0, yuvData.length);
                            cacheFrameCount--;
                        }
                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                    if (yuvData != null) {
                        break;
                    }
                }
            }
        } else {
            for (int i = 0; i < cacheFrameCount + 1; i++) {
                if (mMediaCodec != null) {
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                    int flag = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    if (inputBufferIndex >= 0) {

//                        Image image = mMediaCodec.getOutputImage(outputBufferIndex);
//                        yuvData =  ImageHelper.getDataFromImage(image,ImageHelper.COLOR_FormatI420);
//                        cacheFrameCount--;

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

//                        Image image = mMediaCodec.getOutputImage(outputBufferIndex);
//                        yuvData =  ImageHelper.getDataFromImage(image,ImageHelper.COLOR_FormatI420);
//                        cacheFrameCount--;

                        ByteBuffer outputBuffer = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                        } else {
                            outputBuffer = mMediaCodec.getOutputBuffers()[outputBufferIndex];
                        }
                        if (outputBuffer != null && bufferInfo.size >= yuvSize) {
                            yuvData = new byte[yuvSize];
                            outputBuffer.get(yuvData, 0, yuvData.length);
                            cacheFrameCount--;
                        }
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

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / DEFAULT_FRAMERATE;
    }

}
