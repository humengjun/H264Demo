package co.jp.snjp.x264demo.hardware;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import co.jp.snjp.x264demo.R;
import co.jp.snjp.x264demo.YuvPlayActivity;
import co.jp.snjp.x264demo.utils.FileUtils;
import co.jp.snjp.x264demo.utils.PermissionUtils;
import jp.co.tmath.tmch264dec.TMCH264Dec;

public class MainActivity extends AppCompatActivity implements FileSelectionDialog.OnFileSelectListener {

    //原始图片目录
    private final String BMP_PATH = Environment.getExternalStorageDirectory().toString() + "/bmp/";

    //TMC编码，TMC解码
    private final String TMC_TMC_YUV_PATH = Environment.getExternalStorageDirectory().toString() + "/tmc_tmc_yuv/";

    //HYWAY编码，TMC解码
    private final String HYWAY_TMC_YUV_PATH = Environment.getExternalStorageDirectory().toString() + "/hyway_tmc_yuv/";

    //TMC编码，HYWAY解码
    private final String TMC_HYWAY_YUV_PATH = Environment.getExternalStorageDirectory().toString() + "/tmc_hyway_yuv/";

    //HYWAY编码，HYWAY解码
    private final String HYWAY_HYWAY_YUV_PATH = Environment.getExternalStorageDirectory().toString() + "/hyway_hyway_yuv/";

    //HYWAY编码原数据
    private final String HYWAY_H264_PATH = Environment.getExternalStorageDirectory().toString() + "/hyway_h264/";

    //TMC编码原数据
    private final String TMC_H264_PATH = Environment.getExternalStorageDirectory().toString() + "/tmc_h264/";

    //TMC解码后得到的bmp数据
    private final String TMC_BMP_PATH = Environment.getExternalStorageDirectory().toString() + "/tmc_bmp/";

    //HYWAY解码后得到的bmp数据
    private final String HYWAY_BMP_PATH = Environment.getExternalStorageDirectory().toString() + "/hyway_bmp/";

    H264Decoder decoder;

    H264Encoder encoder;

    TMCH264Dec tmcdec;

    int tmc2hywayDecIndex, hyway2hywayDecIndex, hywayEncIndex;

    long tmc2hywayBeforeTime, hyway2hywayBeforeTime, hywayEncBeforeTime;

    private final static String MIME_FORMAT = "video/avc"; //support h.264

    private Spinner spinner, api_spinner;

    private TextView state;

    private int listSize;

    private long startTime;

    private int code;

    private final int HYWAY_ENCODER_CODE = 1;

    private final int HYWAY_DECODER_CODE = 2;

    private final int TMC_ENCODER_CODE = 3;

    private final int TMC_DECODER_CODE = 4;

    private final int HYWAY_ENCODER_LIST_CODE = 5;

    private final int HYWAY_DECODER_LIST_CODE = 6;

    ImageDecoder imageDecoder;
    ImageEncoder imageEncoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PermissionUtils.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, 1);

        spinner = findViewById(R.id.spinner);
        api_spinner = findViewById(R.id.api_spinner);
        state = findViewById(R.id.state);

        findViewById(R.id.write_bmp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeBmp();
            }
        });

        findViewById(R.id.showPlayer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, YuvPlayActivity.class));
            }
        });

        findViewById(R.id.hyway_decoder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.onClick(HYWAY_DECODER_CODE);
            }
        });
        findViewById(R.id.hyway_encoder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.onClick(HYWAY_ENCODER_CODE);
            }
        });
        findViewById(R.id.tmc_decoder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.onClick(TMC_DECODER_CODE);
            }
        });
        findViewById(R.id.tmc_encoder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.onClick(TMC_ENCODER_CODE);
            }
        });
        findViewById(R.id.hyway_encoder_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.onClick(HYWAY_ENCODER_LIST_CODE);
            }
        });
        findViewById(R.id.hyway_decoder_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.onClick(HYWAY_DECODER_LIST_CODE);
            }
        });


        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String api_select = api_spinner.getSelectedItem().toString();
                boolean isNew = false;
                if (api_select.contains("new")) {
                    isNew = true;
                }
                if (imageEncoder != null)
                    imageEncoder.release();
                int compressRatio = Integer.parseInt(spinner.getSelectedItem().toString().replace("%", ""));//压缩百分比
                imageEncoder = new ImageEncoder(MIME_FORMAT, compressRatio, 640, 360, isNew);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        api_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String api_select = api_spinner.getSelectedItem().toString();
                boolean isNew = false;
                if (api_select.contains("new")) {
                    isNew = true;
                }
                int compressRatio = Integer.parseInt(spinner.getSelectedItem().toString().replace("%", ""));//压缩百分比
                if (imageDecoder != null)
                    imageDecoder.release();
                if (imageEncoder != null)
                    imageEncoder.release();
                imageEncoder = new ImageEncoder(MIME_FORMAT, compressRatio, 640, 360, isNew);
                imageDecoder = new ImageDecoder(MIME_FORMAT, 640, 360, isNew);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

//        tmcdec = new TMCH264Dec(this);
//        tmcdec.Init("hq1a00t1");
    }

    private void writeBmp() {
        new Thread() {
            @Override
            public void run() {
                try {
                    String[] listFiles = getAssets().list("bmp");
                    for (int i = 0; i < listFiles.length; i++) {
                        String fileName = listFiles[i];
                        InputStream inputStream = getAssets().open("bmp/" + fileName);
                        FileUtils.writeFile(BMP_PATH + fileName, inputStream);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

    }

    private void initHyway2HywayDecoder() throws IOException {
        String api_select = api_spinner.getSelectedItem().toString();
        boolean isNew = false;
        if (api_select.contains("new")) {
            isNew = true;
        } else {
            isNew = false;
        }
        startTime = System.currentTimeMillis();
        hyway2hywayDecIndex = 0;
        listSize = 0;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                state.setText("解码中...");
            }
        });
        decoder = new H264Decoder(MIME_FORMAT, 1280, 720, isNew, new H264Decoder.IResponse() {
            @Override
            public void onResponse(int code, byte[] yuvData) {
                final long interval = System.currentTimeMillis() - hyway2hywayBeforeTime;
                Log.i("HYWAY_DECODER_TIME", interval + "");
                hyway2hywayBeforeTime = System.currentTimeMillis();
                File file = new File(HYWAY_HYWAY_YUV_PATH + "/hyway_hyway" + hyway2hywayDecIndex + ".yuv");
                FileUtils.writeFile(file, yuvData, false);
                hyway2hywayDecIndex++;
                if (hyway2hywayDecIndex == listSize) {
                    decoder.release();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            state.setText("OK:" + (hyway2hywayBeforeTime - startTime));
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            state.setText("index:" + interval);
                        }
                    });
                }
            }
        });
        File h264Directory = new File(HYWAY_H264_PATH);
        if (!h264Directory.exists())
            return;
        String[] listFiles = h264Directory.list();
        if (listFiles == null)
            return;
        Arrays.sort(listFiles);
        listSize = listFiles.length;
        for (int i = 0; i < listFiles.length; i++) {
            String fileName = listFiles[i];
            InputStream inputStream = new FileInputStream(HYWAY_H264_PATH + fileName);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] b = new byte[1024];
            int len = 0;
            while ((len = inputStream.read(b)) != -1) {
                outputStream.write(b, 0, len);
            }
            byte[] h264_data = outputStream.toByteArray();
            decoder.addDataSource(h264_data);

            outputStream.close();
            inputStream.close();
        }

        decoder.startDecoderFromAsync();
        hyway2hywayBeforeTime = System.currentTimeMillis();
    }

    private void initHywayEncoder() throws IOException {
        String api_select = api_spinner.getSelectedItem().toString();
        boolean isNew = false;
        if (api_select.contains("new")) {
            isNew = true;
        } else {
            isNew = false;
        }
        startTime = System.currentTimeMillis();
        hywayEncIndex = 0;
        listSize = 0;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                state.setText("编码中...");
            }
        });

        int compressRatio = Integer.parseInt(spinner.getSelectedItem().toString().replace("%", ""));//压缩百分比
        encoder = new H264Encoder(MIME_FORMAT, compressRatio, 1280, 720, isNew, new H264Encoder.IResponse() {
            @Override
            public void onResponse(int code, byte[] h264Data) {
                final long interval = System.currentTimeMillis() - hywayEncBeforeTime;
                Log.i("HYWAY_ENCODER_TIME", interval + "");
                hywayEncBeforeTime = System.currentTimeMillis();
                File file = new File(HYWAY_H264_PATH + "/hyway" + hywayEncIndex + ".264");
                FileUtils.writeFile(file, h264Data, false);
                hywayEncIndex++;
                if (hywayEncIndex == listSize) {
                    encoder.release();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            state.setText("OK:" + (hywayEncBeforeTime - startTime));
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            state.setText("index:" + interval);
                        }
                    });
                }
            }
        });
        File bmpDirectory = new File(BMP_PATH);
        if (!bmpDirectory.exists())
            return;
        String[] listFiles = bmpDirectory.list();
        if (listFiles == null)
            return;
        listSize = listFiles.length;
        for (int i = 0; i < listFiles.length; i++) {
            String fileName = listFiles[i];
            InputStream inputStream = new FileInputStream(BMP_PATH + fileName);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] b = new byte[1024];
            int len = 0;
            while ((len = inputStream.read(b)) != -1) {
                outputStream.write(b, 0, len);
            }
            byte[] bmpData = outputStream.toByteArray();
            encoder.addDataSource(bmpData);

            inputStream.close();
            outputStream.close();
        }

        encoder.startEncoderFromAsync();
        hywayEncBeforeTime = System.currentTimeMillis();
    }


    private void onClick(int code) {

        FileSelectionDialog dlg = new FileSelectionDialog(this, this);
        String path = Environment.getExternalStorageDirectory().toString();
        this.code = code;
        switch (code) {
            case HYWAY_ENCODER_CODE:
                state.setText("编码中...");
                path = BMP_PATH;
                break;
            case HYWAY_DECODER_CODE:
                state.setText("解码中...");
                path = HYWAY_H264_PATH;
                break;
            case TMC_ENCODER_CODE:
                state.setText("编码中...");
                path = BMP_PATH;
                FileUtils.deleteFile(HYWAY_H264_PATH);
                FileUtils.deleteFile(HYWAY_HYWAY_YUV_PATH);
                return;
            case TMC_DECODER_CODE:
                state.setText("解码中...");
                path = TMC_H264_PATH;
                break;
            case HYWAY_ENCODER_LIST_CODE:
                try {
                    initHywayEncoder();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            case HYWAY_DECODER_LIST_CODE:
                try {
                    initHyway2HywayDecoder();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;

        }
        dlg.show(new File(path));
    }


    @Override
    public void onFileSelect(File file) {
        switch (code) {
            case HYWAY_ENCODER_CODE:
                startHywayEncoder(file);
                break;
            case HYWAY_DECODER_CODE:
                startHywayDecoder(file);
                break;
            case TMC_DECODER_CODE:
                startTmcDecoder(file);
                break;
            case TMC_ENCODER_CODE:
                startTmcEncoder(file);
                break;
        }

    }

    private void startTmcDecoder(File file) {
        startTime = System.currentTimeMillis();
        try {
            InputStream inputStream = new FileInputStream(file);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int len = 0;
            while ((len = inputStream.read(b)) != -1) {
                outputStream.write(b, 0, len);
            }
            byte[] h264Data = outputStream.toByteArray();

            inputStream.close();
            outputStream.close();

            int retcode = tmcdec.SetStream(h264Data, 0, h264Data.length);
            if (retcode == TMCH264Dec.TMCH264DEC_STATUS_OUT_IMAGE) {
//                    outWidth = tmcdec.GetWidth();
//                    outHeight = tmcdec.GetHeight();
                byte[] yuv_data = tmcdec.GetImage();

                File outFile = new File(TMC_TMC_YUV_PATH + file.getName().replace(".264", "") + ".yuv");
                FileUtils.writeFile(outFile, yuv_data, false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        state.setText("OK:" + (System.currentTimeMillis() - startTime));
                    }
                });
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startTmcEncoder(final File file) {
        String api_select = api_spinner.getSelectedItem().toString();
        boolean isNew = false;
        if (api_select.contains("new")) {
            isNew = true;
        } else {
            isNew = false;
        }
        startTime = System.currentTimeMillis();
        decoder = new H264Decoder(MIME_FORMAT, 1280, 720, isNew, new H264Decoder.IResponse() {
            @Override
            public void onResponse(int code, byte[] yuvData) {
                File outFile = new File(HYWAY_HYWAY_YUV_PATH + file.getName().replace(".264", "") + ".yuv");
                FileUtils.writeFile(outFile, yuvData, false);
                decoder.release();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        state.setText("OK:" + (System.currentTimeMillis() - startTime));
                    }
                });
            }
        });
        try {
            InputStream inputStream = new FileInputStream(file);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int len = 0;
            while ((len = inputStream.read(b)) != -1) {
                outputStream.write(b, 0, len);
            }
            byte[] h264Data = outputStream.toByteArray();
            decoder.addDataSource(h264Data);

            inputStream.close();
            outputStream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        decoder.startDecoderFromAsync();
    }

    private void startHywayDecoder(final File file) {
        startTime = System.currentTimeMillis();

        byte[] yuvData = imageDecoder.decoderFile(file);
        if (yuvData == null)
            return;

        File outFile = new File(HYWAY_HYWAY_YUV_PATH + file.getName().replace(".264", "") + ".yuv");
        FileUtils.writeFile(outFile, yuvData, false);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                state.setText("OK:" + (System.currentTimeMillis() - startTime));
            }
        });

    }

    private void startHywayEncoder(final File file) {
        startTime = System.currentTimeMillis();

        byte[] h264Data = imageEncoder.encoderFile(file);
        if (h264Data == null)
            return;

        File outFile = new File(HYWAY_H264_PATH + file.getName().
                replace(".bmp", "").replace(".yuv", "") + ".264");
        FileUtils.writeFile(outFile, h264Data, false);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                state.setText("OK:" + (System.currentTimeMillis() - startTime));
            }
        });
    }
}
