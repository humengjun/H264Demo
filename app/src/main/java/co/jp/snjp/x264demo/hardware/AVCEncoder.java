package co.jp.snjp.x264demo.hardware;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class AVCEncoder {

    private MediaCodec mediaCodec;
    int m_width;
    int m_height;
    byte[] m_info = null;

    private byte[] yuv420 = null;

    int m_SupportColorFormat = -1;

    @SuppressLint("NewApi")
    public AVCEncoder(int width, int height, int framerate, int bitrate, int iFrameInterval) {

        m_width = width;
        m_height = height;
        yuv420 = new byte[width * height * 3 / 2];

        m_SupportColorFormat = getSupportColorFormat();

        if (m_SupportColorFormat != -1) {
            try {
                mediaCodec = MediaCodec.createEncoderByType("video/avc");
            } catch (IOException e) {
                e.printStackTrace();
            }
            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, m_SupportColorFormat);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 关键帧间隔时间//
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();

        } else {
            Log.e("AvcEncoder", "mediacodec doesn't work");
            // 硬编码不成功
        }

    }

    @SuppressLint("NewApi")
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

        Log.e("AvcEncoder", "Found " + codecInfo.getName() + " supporting " + "video/avc");

        // Find a color profile that the codec supports
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
        Log.e("AvcEncoder",
                "length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));

        for (int i = 0; i < capabilities.colorFormats.length; i++) {

            switch (capabilities.colorFormats[i]) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:

                Log.e("AvcEncoder", "supported color format::" + capabilities.colorFormats[i]);
                return capabilities.colorFormats[i];
            default:
                Log.e("AvcEncoder", "unsupported color format " + capabilities.colorFormats[i]);
                break;
            }
        }

        return -1;
    }

    @SuppressLint("NewApi")
    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class EncodeResult {
        private static final int FRAME_IVALID = -1;
        private static final int FRAME_IFRAME = 1;
        private static final int FRAME_PFRAME = 2;
        private static final int FRAME_ENCODE_LEN_INVALID = -1;

        private int frameFlag;
        private int encodeLen;
        private int frameIndex;

        public EncodeResult() {
            super();
            // TODO Auto-generated constructor stub
        }

        public EncodeResult(int frameFlag, int encodeLen) {
            super();
            this.frameFlag = frameFlag;
            this.encodeLen = encodeLen;
        }

        public int getFrameIndex() {
            return frameIndex;
        }

        public void setFrameIndex(int frameIndex) {
            this.frameIndex = frameIndex;
        }

        public int getFrameFlag() {
            return frameFlag;
        }

        public void setFrameFlag(int frameFlag) {
            this.frameFlag = frameFlag;
        }

        public int getEncodeLen() {
            return encodeLen;
        }

        public void setEncodeLen(int encodeLen) {
            this.encodeLen = encodeLen;
        }

    }

    private int frameIndex = 0;

    @SuppressLint("NewApi")
    public EncodeResult offerEncoder(byte[] input, byte[] output) {

        EncodeResult result = new EncodeResult();
        int pos = 0;

        switch (m_SupportColorFormat) {
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            YV12toYUV420Planar(input, yuv420, m_width, m_height);
            break;
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            // todo
            break;
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            YV12toYUV420PackedSemiPlanar(input, yuv420, m_width, m_height);
            break;
        case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
            // todo
            break;
        case MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar:
            // todo
            break;
        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            YV12toYUV420SemiPlanar(input, yuv420, m_width, m_height);
            break;
        default:
            break;
        }

        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            //Log.i("lee", "inputBufferIndex:" + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(yuv420);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuv420.length, 0, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            //Log.i("lee", "outputBufferIndex:" + outputBufferIndex);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (m_info != null) {
                    System.arraycopy(outData, 0, output, pos, outData.length);
                    pos += outData.length;
                } else {
                    // 保存pps sps 只有开始时 第一个帧里有， 保存起来后面用
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        m_info = new byte[outData.length];
                        System.arraycopy(outData, 0, m_info, 0, outData.length);
                    } else {
                        return new EncodeResult(EncodeResult.FRAME_IVALID, EncodeResult.FRAME_ENCODE_LEN_INVALID);
                    }
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
            // key frame 编码器生成关键帧时只有 00 00 00 01 65
            // 没有pps
            // sps， 要加上
            if (output[4] == 0x65) {
                System.arraycopy(output, 0, yuv420, 0, pos);
                System.arraycopy(m_info, 0, output, 0, m_info.length);
                System.arraycopy(yuv420, 0, output, m_info.length, pos);
                pos += m_info.length;
                frameIndex = 0;
                result.setFrameFlag(EncodeResult.FRAME_IFRAME);

            } else {
                frameIndex++;
                result.setFrameFlag(EncodeResult.FRAME_PFRAME);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        result.setEncodeLen(pos);
        result.setFrameIndex(frameIndex);
        return result;
    }

    public static void YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int width,
            final int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12 We convert by putting
         * the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb
                                                                            // (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
    }

    public static void YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V
         * reversed. So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr
                                                                                        // (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb
                                                                                        // (U)
    }

    public static void YV12toYUV420SemiPlanar(final byte[] input, final byte[] output, final int width,
            final int height) {
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        int padding = 1024;
        if ((width == 640 && height == 480) || width == 1280 && height == 720)
            padding = 0;

        System.arraycopy(input, 0, output, 0, frameSize);
        for (int i = 0; i < (qFrameSize); i++) {
            output[frameSize + i * 2 + padding] = (input[frameSize + qFrameSize + i]);
            output[frameSize + i * 2 + 1 + padding] = (input[frameSize + i]);
        }
    }

}