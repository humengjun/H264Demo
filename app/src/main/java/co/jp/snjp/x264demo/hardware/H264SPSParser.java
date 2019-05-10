package co.jp.snjp.x264demo.hardware;

import android.util.Log;

/*
 * Author:Vincent Luo
 * Date: 20150615
 * Description:参考H264标准语法实现对SPS参数的解析
 */
public class H264SPSParser {
    private static final String TAG = "H264SPSPaser";
    private static int startBit = 0;

    /*
     * 从数据流data中第StartBit位开始读，读bitCnt位，以无符号整形返回
     */
    private static short u(byte[] data, int bitCnt, int StartBit) {
        short ret = 0;
        int start = StartBit;
        for (int i = 0; i < bitCnt; i++) {
            ret <<= 1;
            if ((data[start / 8] & (0x80 >> (start % 8))) != 0) {
                ret += 1;
            }
            start++;
        }
        startBit += bitCnt;
        return ret;
    }

    /*
     * 无符号指数哥伦布编码
     * leadingZeroBits = −1;
     * for( b = 0; !b; leadingZeroBits++ )
     *    b = read_bits( 1 )
     * 变量codeNum 按照如下方式赋值：
     * codeNum = 2^leadingZeroBits − 1 + read_bits( leadingZeroBits )
     * 这里read_bits( leadingZeroBits )的返回值使用高位在先的二进制无符号整数表示。
     */
    private static short ue(byte[] data, int StartBit) {
        short ret = 0;
        int leadingZeroBits = -1;
        int tempStartBit = (StartBit == -1) ? startBit : StartBit;//如果传入-1，那么就用上次记录的静态变量
        for (int b = 0; b != 1; leadingZeroBits++) {//读到第一个不为0的数，计算前面0的个数
            b = u(data, 1, tempStartBit++);
        }
        Log.d(TAG, "ue leadingZeroBits = " + leadingZeroBits + ",Math.pow(2, leadingZeroBits) = " + Math.pow(2, leadingZeroBits) + ",tempStartBit = " + tempStartBit);
        ret = (short) (Math.pow(2, leadingZeroBits) - 1 + u(data, leadingZeroBits, tempStartBit));
        startBit = tempStartBit + leadingZeroBits;
        Log.d(TAG, "ue startBit = " + startBit);
        return ret;
    }

    /*
     * 有符号指数哥伦布编码
     * 9.1.1 有符号指数哥伦布编码的映射过程
     *按照9.1节规定，本过程的输入是codeNum。
     *本过程的输出是se(v)的值。
     *表9-3中给出了分配给codeNum的语法元素值的规则，语法元素值按照绝对值的升序排列，负值按照其绝对
     *值参与排列，但列在绝对值相等的正值之后。
     *表 9-3－有符号指数哥伦布编码语法元素se(v)值与codeNum的对应
     *codeNum 语法元素值
     *  0       0
     *  1       1
     *  2       −1
     *  3       2
     *  4       −2
     *  5       3
     *  6       −3
     *  k       (−1)^(k+1) Ceil( k÷2 )
     */
    private static int se(byte[] data, int StartBit) {
        int ret = 0;
        short codeNum = ue(data, StartBit);
        ret = (int) (Math.pow(-1, codeNum + 1) * Math.ceil(codeNum / 2));
        return ret;
    }

    /**
     * 从SPS中提取图像的分辨率
     *
     * @param h264Data
     * @param w_h_data
     */
    public static void obtainH264ImageSize(byte[] h264Data, int[] w_h_data) {
        if (w_h_data.length < 2)
            return;
        startBit = 0;
        byte[] sps = getSPS(h264Data);
        if (sps == null || sps.length == 0)
            return;
        int profile_idc = u(sps, 8, startBit);
        int constraint_set0_flag = u(sps, 1, startBit);
        int constraint_set1_flag = u(sps, 1, startBit);
        int constraint_set2_flag = u(sps, 1, startBit);
        int constraint_set3_flag = u(sps, 1, startBit);
        int constraint_set4_flag = u(sps, 1, startBit);
        int constraint_set5_flag = u(sps, 1, startBit);
        int reserved_zero_2bits = u(sps, 2, startBit);
        int level_idc = u(sps, 8, startBit);
        int seq_parameter_set_id = ue(sps, -1);

        if (profile_idc == 44 || profile_idc == 100 || profile_idc == 110 || profile_idc == 122
                || profile_idc == 244 || profile_idc == 83 || profile_idc == 86 || profile_idc == 118 || profile_idc == 128) {
            int chroma_format_idc = ue(sps, -1);
            if (chroma_format_idc == 3) {
                int separate_colour_plane_flag = u(sps, 1, startBit);
            }
            int bit_depth_luma_minus8 = ue(sps, -1);
            int bit_depth_chroma_minus8 = ue(sps, -1);
            int qpprime_y_zero_transform_bypass_flag = u(sps, 1, startBit);
            int seq_scaling_matrix_present_flag = u(sps, 1, startBit);
            if (seq_scaling_matrix_present_flag == 1) {

            }
        }
        int log2_max_frame_num_minus4 = ue(sps, -1);
        int pic_order_cnt_type = ue(sps, -1);
        if (pic_order_cnt_type == 0) {
            int log2_max_pic_order_cnt_lsb_minus4 = ue(sps, -1);
        } else if (pic_order_cnt_type == 1) {
            int delta_pic_order_always_zero_flag = u(sps, 1, startBit);
            int offset_for_non_ref_pic = se(sps, -1);
            int offset_for_top_to_bottom_field = se(sps, -1);
            int num_ref_frames_in_pic_order_cnt_cycle = ue(sps, -1);

            if (num_ref_frames_in_pic_order_cnt_cycle > 0) {
                int[] offset_for_ref_frame = new int[num_ref_frames_in_pic_order_cnt_cycle];

                for (int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++) {
                    offset_for_ref_frame[i] = se(sps, -1);
                }
            }
        }
        int max_num_ref_frames = ue(sps, -1);
        int gaps_in_frame_num_value_allowed_flag = u(sps, 1, startBit);


        int pic_width_in_mbs_minus1 = ue(sps, -1);
        int pic_height_in_map_units_minus1 = ue(sps, -1);
        int frame_mbs_only_flag = u(sps, 1, startBit);
        if (frame_mbs_only_flag == 0) {
            int mb_adaptive_frame_field_flag = u(sps, 1, startBit);
        }
        int direct_8x8_inference_flag = u(sps, 1, startBit);
        int frame_cropping_flag = u(sps, 1, startBit);
        if (frame_cropping_flag == 1) {
            int frame_crop_left_offset = ue(sps, -1);
            int frame_crop_right_offset = ue(sps, -1);
            int frame_crop_top_offset = ue(sps, -1);
            int frame_crop_bottom_offset = ue(sps, -1);
            //如果有偏移量，则需要加上偏移量（不是16的倍数的情况）
            w_h_data[0] = (pic_width_in_mbs_minus1 + 1) * 16 + (frame_crop_left_offset - frame_crop_right_offset) * 2;
            w_h_data[1] = (pic_height_in_map_units_minus1 + 1) * 16 + (frame_crop_top_offset - frame_crop_bottom_offset) * 2;
            return;
        }
//        int vui_parameters_present_flag = u(sps, 1, startBit);

        w_h_data[0] = (pic_width_in_mbs_minus1 + 1) * 16;
        w_h_data[1] = (pic_height_in_map_units_minus1 + 1) * 16;
    }

    /**
     * 从H264文件中提取sps数据
     *
     * @param h264Data
     * @return
     */
    private static byte[] getSPS(byte[] h264Data) {
        byte[] startPos1 = new byte[]{0x00, 0x00, 0x00, 0x01};
        byte[] startPos2 = new byte[]{0x00, 0x00, 0x01};

        // sps的起始位置
        int spsPos = 0;
        for (int ix = 0; ix < h264Data.length; ++ix) {
            if (h264Data[ix] == startPos1[0]
                    && h264Data[ix + 1] == startPos1[1]
                    && h264Data[ix + 2] == startPos1[2]
                    && h264Data[ix + 3] == startPos1[3]) {
                // 找到Sps位
                String binary = Integer.toBinaryString(h264Data[ix + 4]);
                if (binary.endsWith("00111")) {
                    spsPos = ix + 5;
                    break;
                }
            } else if (h264Data[ix] == startPos2[0]
                    && h264Data[ix + 1] == startPos2[1]
                    && h264Data[ix + 2] == startPos2[2]) {
                // 找到Sps位
                String binary = Integer.toBinaryString(h264Data[ix + 3]);
                if (binary.endsWith("00111")) {
                    spsPos = ix + 4;
                    break;
                }
            }
        }
        if (spsPos == 0) {
            return null;
        }
        // sps的结束位置
        int endPos = 0;
        for (int i = spsPos; i < h264Data.length; i++) {
            if ((h264Data[i] == startPos1[0]
                    && h264Data[i + 1] == startPos1[1]
                    && h264Data[i + 2] == startPos1[2]
                    && h264Data[i + 3] == startPos1[3]) ||
                    (h264Data[i] == startPos2[0]
                            && h264Data[i + 1] == startPos2[1]
                            && h264Data[i + 2] == startPos2[2])) {
                endPos = i;
                break;
            }
        }
        if (endPos == 0) {
            return null;
        }

        byte[] spsData = new byte[endPos - spsPos];
        System.arraycopy(h264Data, spsPos, spsData, 0, spsData.length);

        return spsData;
    }

}