package co.jp.snjp.x264demo.hardware;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import co.jp.snjp.x264demo.utils.Bmp2YuvTools;
import co.jp.snjp.x264demo.utils.FileUtils;
import co.jp.snjp.x264demo.utils.YuvConvertTools;

public class ImageEncoder {

    private byte[] configByte;

    private MediaCodec mMediaCodec;

    private MediaFormat mMediaFormat;

    private int width;

    private int height;

    private boolean isI420;

    private int generateIndex = 0;

    private final int DEFAULT_FRAMERATE = 1;

    private final int DEFAULT_I_FRAME_INTERVAL = 1;

//    private final int DEFAULT_KEY_BIT_RATE = 2500 * 100;//1280*720推荐码率

    private int cacheFrameCount;

    private boolean isFirstFrame = true;

    private boolean isNew;

    public ImageEncoder(String mimeType, int compressRatio, int width, int height, boolean isNew) {
        this.isNew = isNew;
        this.width = width;
        this.height = height;

        try {
            mMediaCodec = MediaCodec.createEncoderByType(mimeType);
        } catch (IOException e) {
            mMediaCodec = null;
            return;
        }

        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        //码率越低，图片越模糊
        //2.56x10
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 3 / 2 * compressRatio / 100);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, DEFAULT_FRAMERATE);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL);
        int color_format = getSupportColorFormat();
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color_format);

        mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
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

    public byte[] encoderFile(File file) {
        byte[] yuvData;
        byte[] fileData = FileUtils.readFile4Bytes(file);
        if (fileData == null)
            return null;
        if (file.getName().endsWith(".bmp")) {
            if (isI420)
                yuvData = Bmp2YuvTools.convertI420(fileData, width, height);
            else
                yuvData = Bmp2YuvTools.convertNV12(fileData, width, height);
        } else if (file.getName().endsWith(".yuv")) {
            if (!isI420)
                yuvData = YuvConvertTools.I420toNV12(fileData);
            else
                yuvData = fileData;
        } else {
            return null;
        }

        byte[] h264Data = null;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        //针对某些设备会缓存几帧再编码，因此需要判断是否是第一帧并记录缓存的帧数。
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
                        if (inputBuffer != null && yuvData != null) {
                            inputBuffer.clear();
                            inputBuffer.put(yuvData);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, yuvData.length, computePresentationTime(generateIndex), flag);
                            generateIndex++;
                            cacheFrameCount++;
                        }
                    }

                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
//                    mMediaCodec.getOutputImage(outputBufferIndex);
                    while (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                        } else {
                            outputBuffer = mMediaCodec.getOutputBuffers()[outputBufferIndex];
                        }
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] buffer = new byte[bufferInfo.size];
                            outputBuffer.get(buffer, 0, buffer.length);

                            if (bufferInfo.flags == 2) {
                                configByte = new byte[bufferInfo.size];
                                configByte = buffer;
                            } else {// if (bufferInfo.flags == 1)
                                h264Data = new byte[buffer.length + configByte.length];
                                System.arraycopy(configByte, 0, h264Data, 0, configByte.length);
                                System.arraycopy(buffer, 0, h264Data, configByte.length, buffer.length);
                                cacheFrameCount--;
                            }
                        }

                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                    if (h264Data != null) {
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
                        ByteBuffer inputBuffer = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                        } else {
                            inputBuffer = mMediaCodec.getInputBuffers()[inputBufferIndex];
                        }
                        if (inputBuffer != null && yuvData != null) {
                            inputBuffer.clear();
                            inputBuffer.put(yuvData);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, yuvData.length, computePresentationTime(generateIndex), flag);
                            generateIndex++;
                            cacheFrameCount++;
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
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] buffer = new byte[bufferInfo.size];
                            outputBuffer.get(buffer, 0, buffer.length);

                            if (bufferInfo.flags == 2) {
                                configByte = new byte[bufferInfo.size];
                                configByte = buffer;
                            } else if (bufferInfo.flags == 1) {
                                h264Data = new byte[buffer.length + configByte.length];
                                System.arraycopy(configByte, 0, h264Data, 0, configByte.length);
                                System.arraycopy(buffer, 0, h264Data, configByte.length, buffer.length);
                                cacheFrameCount--;
                            } else {
                                h264Data = buffer;
                                cacheFrameCount--;
                            }
                        }

                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                }
            }
        }
        isFirstFrame = false;
        return h264Data;
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
