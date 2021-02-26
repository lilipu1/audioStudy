package com.frank.audiostudy.slice;

import com.frank.audiostudy.ResourceTable;
import com.frank.audiostudy.utils.PCM2WAV;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.ability.DataAbilityHelper;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Button;
import ohos.agp.components.Component;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.data.rdb.ValuesBucket;
import ohos.global.resource.NotExistException;
import ohos.global.resource.RawFileDescriptor;
import ohos.global.resource.Resource;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import ohos.javax.xml.parsers.SAXParser;
import ohos.media.audio.*;
import ohos.media.common.Source;
import ohos.media.photokit.metadata.AVStorage;
import ohos.media.player.Player;
import ohos.utils.net.Uri;

import java.io.*;

/**
 * @author : frank
 * @date : 2021/2/24 16:30
 */
public class AudioRecordSlice extends AbilitySlice implements Runnable {

    private Button mBtnStartRecord, mBtnStopRecord;
    //指定音频源
    private static final AudioDeviceDescriptor.DeviceType mAudioSource = AudioDeviceDescriptor.DeviceType.MIC;
    //指定采样率 （AAC的通常是44100Hz。 设置采样率为44100，目前为常用的采样率）
    private static final int mSampleRateInHz = 44100;
    //指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
    private static final AudioStreamInfo.ChannelMask mChannelConfig = AudioStreamInfo.ChannelMask.CHANNEL_IN_MONO; //单声道，如果是录音，必须用in的，否则会崩溃
    //指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
    //因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
    private static final AudioStreamInfo.EncodingFormat mAudioFormat = AudioStreamInfo.EncodingFormat.ENCODING_PCM_16BIT;
    //指定缓冲区大小。调用AudioRecord类的getMinBufferSize方法可以获得。
    private int mBufferSizeInBytes;

    private File mRecordingFile = null;//储存AudioRecord录下来的文件
    private boolean isRecording = false; //true表示正在录音
    private AudioCapturer mAudioRecord = null;
    //保存的音频文件名
    private static final String mFileName = "audio_test.pcm";
    private static final String mWAVFileName = "audio_test.wav";
    //缓冲区中数据写入到数据，因为需要使用IO操作，因此读取数据的过程应该在子线程中执行。
    private Thread mThread;
    private DataOutputStream mDataOutputStream;
    private AudioRenderer audioRenderer;
    private AudioRenderer staticAudioRenderer;

    private Player player;

    private int currentPosition = 0;

    @Override
    protected void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_slice_record);
        initDatas();
        initUI();
    }

    //初始化数据
    private void initDatas() {
        mBufferSizeInBytes = AudioCapturer.getMinBufferSize(mSampleRateInHz, AudioStreamInfo.getChannelCount(mChannelConfig), mAudioFormat.getValue());//计算最小缓冲区
        AudioStreamInfo audioStreamInfo = new AudioStreamInfo.Builder()
                .encodingFormat(mAudioFormat) // 16-bit PCM
                .channelMask(mChannelConfig) // 双声道输入
                .sampleRate(mSampleRateInHz) // 44.1kHz
                .build();
        AudioCapturerInfo audioCapturerInfo = new AudioCapturerInfo.Builder().audioStreamInfo(audioStreamInfo)
                .build();

        mAudioRecord = new AudioCapturer(audioCapturerInfo);

        AudioStreamInfo audioStreamInfoStatic = new AudioStreamInfo.Builder()
                .sampleRate(mSampleRateInHz) // 44.1kHz
                .encodingFormat(mAudioFormat) // 16-bit PCM
                .streamUsage(AudioStreamInfo.StreamUsage.STREAM_USAGE_MEDIA)
                .channelMask(AudioStreamInfo.ChannelMask.CHANNEL_OUT_MONO) // 双声道输出,如果要创建AudioRenderer，必须用OUT的
                .build();

        AudioRendererInfo audioRendererInfoStatic = new AudioRendererInfo.Builder().audioStreamInfo(audioStreamInfoStatic)
                .audioStreamOutputFlag(AudioRendererInfo.AudioStreamOutputFlag.AUDIO_STREAM_OUTPUT_FLAG_DIRECT_PCM) // pcm格式的输出流
                .bufferSizeInBytes(mBufferSizeInBytes)
                .isOffload(true) // false表示分段传输buffer并播放，true表示整个音频流一次性传输到HAL层播放
                .build();

       staticAudioRenderer = new AudioRenderer(audioRendererInfoStatic, AudioRenderer.PlayMode.MODE_STATIC);

        AudioRendererInfo audioRendererInfo = new AudioRendererInfo.Builder().audioStreamInfo(audioStreamInfoStatic)
                .audioStreamOutputFlag(AudioRendererInfo.AudioStreamOutputFlag.AUDIO_STREAM_OUTPUT_FLAG_DIRECT_PCM) // pcm格式的输出流
                .bufferSizeInBytes(mBufferSizeInBytes)
                .isOffload(false) // false表示分段传输buffer并播放，true表示整个音频流一次性传输到HAL层播放
                .build();
        audioRenderer = new AudioRenderer(audioRendererInfo, AudioRenderer.PlayMode.MODE_STREAM);
        player = new Player(this);
    }

    //初始化UI
    private void initUI() {
        mBtnStartRecord = (Button) findComponentById(ResourceTable.Id_button_start_record);
        mBtnStopRecord = (Button) findComponentById(ResourceTable.Id_button_stop_record);
        mBtnStartRecord.setClickedListener(component -> startRecord());

        mBtnStopRecord.setClickedListener(component -> releaseRecoder());

        findComponentById(ResourceTable.Id_play_static_mode).setClickedListener(component -> {
            staticModePlay();
        });

        findComponentById(ResourceTable.Id_play_stream_mode).setClickedListener(component -> {
            streamModePlay();
        });

        findComponentById(ResourceTable.Id_pause_stream_mode).setClickedListener(component -> {
            pauseStreamMode();
        });

        findComponentById(ResourceTable.Id_stop_stream_mode).setClickedListener(component -> {
            stopStreamMode();
        });

        findComponentById(ResourceTable.Id_play_player).setClickedListener(component -> {
            playerPlay();
        });
    }

    private void staticModePlay() {
        getGlobalTaskDispatcher(TaskPriority.DEFAULT).asyncDispatch(() -> {
            try {
                Resource resource = getContext().getResourceManager().getResource(ResourceTable.Media_test);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n = 0;
                while (-1 != (n = resource.read(buffer))) {
                    output.write(buffer, 0, n);
                }
                staticAudioRenderer.write(buffer, 0, buffer.length);
                staticAudioRenderer.start();
            } catch (IOException | NotExistException e) {
                e.printStackTrace();
            }

        });
    }

    private void streamModePlay() {
        audioRenderer.start();
        audioRenderer.setPosition(currentPosition);
        getGlobalTaskDispatcher(TaskPriority.DEFAULT).asyncDispatch(() -> {
            try {
                FileInputStream soundInputStream = new FileInputStream(new File(getCacheDir(), mFileName));
                byte[] buffer = new byte[1024];
                int len;
                try {
                    while ((len = soundInputStream.read(buffer, 0, buffer.length)) != -1) {
                        audioRenderer.write(buffer, 0, buffer.length);
                    }
                    soundInputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void pauseStreamMode() {
        currentPosition = audioRenderer.getPosition();
        audioRenderer.pause();
    }

    private void stopStreamMode() {
        audioRenderer.stop();
    }

    private void playerPlay() {
        getGlobalTaskDispatcher(TaskPriority.DEFAULT).asyncDispatch(new Runnable() {
            @Override
            public void run() {
                try {
                    new PCM2WAV().convertAudioFiles(new File(getCacheDir(), mFileName), new File(getCacheDir(), mWAVFileName));
                    saveAudio();

                } catch (Exception e) {
                    debug("录音文件转换错误");
                }
            }
        });

    }

    //开始录音
    private void startRecord() {
        //AudioRecord.getMinBufferSize的参数是否支持当前的硬件设备
        if (AudioCapturer.ERROR_BAD_VALUE == mBufferSizeInBytes || AudioCapturer.ERROR == mBufferSizeInBytes) {
            throw new RuntimeException("获取最小缓冲区失败");
        } else {
            destroyThread();
            isRecording = true;
            if (mThread == null) {
                mThread = new Thread(this);
                mThread.start();//开启线程
            }
        }
    }

    /**
     * 销毁线程方法
     */
    private void destroyThread() {
        try {
            isRecording = false;
            if (null != mThread && Thread.State.RUNNABLE == mThread.getState()) {
                try {
                    Thread.sleep(500);
                    mThread.interrupt();
                } catch (Exception e) {
                    mThread = null;
                }
            }
            mThread = null;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mThread = null;
        }
    }

    private void releaseRecoder() {
        isRecording = false;
        //停止录音，回收AudioRecord对象，释放内存
        if (mAudioRecord != null) {
            if (mAudioRecord.getState() == AudioCapturer.State.STATE_INITIALIZED) {//初始化成功
                mAudioRecord.stop();
            }
            if (mAudioRecord != null) {
                mAudioRecord.release();
            }
        }
    }

    //停止录音
    public void release() {
        isRecording = false;
        //停止录音，回收AudioRecord对象，释放内存
        if (mAudioRecord != null) {
            if (mAudioRecord.getState() == AudioCapturer.State.STATE_INITIALIZED) {//初始化成功
                mAudioRecord.stop();
            }
            if (mAudioRecord != null) {
                mAudioRecord.release();
            }

            if (audioRenderer != null) {
                if (audioRenderer.getState() == AudioRenderer.State.STATE_INITIALIZED) {
                    audioRenderer.stop();
                }
                if (audioRenderer != null) {
                    audioRenderer.release();
                }
            }
        }
    }

    @Override
    public void run() {

        //标记为开始采集状态
        isRecording = true;
        //创建一个流，存放从AudioRecord读取的数据
        mRecordingFile = new File(getCacheDir(), mFileName);
        if (mRecordingFile.exists()) {//音频文件保存过了删除
            mRecordingFile.delete();
        }
        try {
            mRecordingFile.createNewFile();//创建新文件
        } catch (IOException e) {
            e.printStackTrace();
            debug("创建储存音频文件出错");
        }

        try {
            //获取到文件的数据流
            mDataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mRecordingFile)));
            byte[] buffer = new byte[mBufferSizeInBytes];


            //判断AudioRecord未初始化，停止录音的时候释放了，状态就为STATE_UNINITIALIZED
            if (mAudioRecord.getState() == AudioCapturer.State.STATE_UNINITIALIZED) {
                initDatas();
            }

            mAudioRecord.start();//开始录音
            //getRecordingState获取当前AudioReroding是否正在采集数据的状态
            while (isRecording && mAudioRecord.getState() == AudioCapturer.State.STATE_RECORDING) {
                int bufferReadResult = mAudioRecord.read(buffer, 0, mBufferSizeInBytes);
                for (int i = 0; i < bufferReadResult; i++) {
                    mDataOutputStream.write(buffer[i]);
                }
            }
            mDataOutputStream.close();
        } catch (Throwable t) {
            debug("Recording Failed");
            release();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        destroyThread();
        release();
    }

    private void saveAudio() {
        try {
            ValuesBucket valuesBucket = new ValuesBucket();
            valuesBucket.putString(AVStorage.Audio.Media.DISPLAY_NAME, mWAVFileName);
//            valuesBucket.putString("relative_path", "DCIM/");
            valuesBucket.putString(AVStorage.Audio.Media.MIME_TYPE, "audio/x-wav");
            //应用独占
            valuesBucket.putInteger("is_pending", 1);
            DataAbilityHelper helper = DataAbilityHelper.creator(this);
            int id = helper.insert(AVStorage.Audio.Media.EXTERNAL_DATA_ABILITY_URI, valuesBucket);
            Uri uri = Uri.appendEncodedPathToUri(AVStorage.Audio.Media.EXTERNAL_DATA_ABILITY_URI, String.valueOf(id));
            //这里需要"w"写权限
            FileDescriptor fd = helper.openFile(uri, "w");
            OutputStream outputStream = new FileOutputStream(fd);
            File audioFile = new File(getCacheDir(), mFileName);
            FileInputStream soundInputStream = new FileInputStream(audioFile);
            byte[] buffer = new byte[1024];
            int len;
            try {
                while ((len = soundInputStream.read(buffer, 0, buffer.length)) != -1) {
                    outputStream.write(buffer, 0, buffer.length);
                }
                soundInputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            outputStream.flush();
            outputStream.close();
            valuesBucket.clear();
            //解除独占
            valuesBucket.putInteger("is_pending", 0);
            helper.update(uri, valuesBucket, null);
            Source source = new Source(fd,0,audioFile.length());
            getUITaskDispatcher().asyncDispatch(() -> {
                player.setSource(source);
                player.prepare();
                player.play();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void debug(String message) {
        HiLog.error(new HiLogLabel(HiLog.LOG_APP, 3, "RecordAudio"), message);
    }
}
