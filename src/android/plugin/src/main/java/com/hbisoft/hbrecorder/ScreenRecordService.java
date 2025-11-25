package com.hbisoft.hbrecorder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import android.os.Looper;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

import static com.hbisoft.hbrecorder.Constants.ERROR_KEY;
import static com.hbisoft.hbrecorder.Constants.ERROR_REASON_KEY;
import static com.hbisoft.hbrecorder.Constants.MAX_FILE_SIZE_REACHED_ERROR;
import static com.hbisoft.hbrecorder.Constants.MAX_FILE_SIZE_KEY;
import static com.hbisoft.hbrecorder.Constants.NO_SPECIFIED_MAX_SIZE;
import static com.hbisoft.hbrecorder.Constants.ON_COMPLETE;
import static com.hbisoft.hbrecorder.Constants.ON_COMPLETE_KEY;
import static com.hbisoft.hbrecorder.Constants.ON_PAUSE;
import static com.hbisoft.hbrecorder.Constants.ON_PAUSE_KEY;
import static com.hbisoft.hbrecorder.Constants.ON_RESUME;
import static com.hbisoft.hbrecorder.Constants.ON_RESUME_KEY;
import static com.hbisoft.hbrecorder.Constants.ON_START;
import static com.hbisoft.hbrecorder.Constants.ON_START_KEY;
import static com.hbisoft.hbrecorder.Constants.SETTINGS_ERROR;

import plugin.screenRecorder.R;

/**
 * Created by HBiSoft on 13 Aug 2019
 * Copyright (c) 2019 . All rights reserved.
 */

public class ScreenRecordService extends Service {

    private static final String TAG = "ScreenRecordService";
    private long maxFileSize = NO_SPECIFIED_MAX_SIZE;
    private boolean hasMaxFileBeenReached = false;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;
    private int mResultCode;
    private Intent mResultData;
    private boolean isVideoHD;
    private boolean isAudioEnabled;
    private String path;

    private String outputFormat;

    private MediaProjection mMediaProjection;
    private MediaRecorder mMediaRecorder;
    private VirtualDisplay mVirtualDisplay;
    private String name;
    private int audioBitrate;
    private int audioSamplingRate;
    private static String filePath;
    private static String fileName;
    private int audioSourceAsInt;
    private int videoEncoderAsInt;
    private boolean isCustomSettingsEnabled;
    private int videoFrameRate;
    private int videoBitrate;
    private int outputFormatAsInt;
    private int orientationHint;

    public final static String BUNDLED_LISTENER = "listener";
    private Uri returnedUri = null;
    private Intent mIntent;

    private MediaProjectionManager mProjectionManager;
    private volatile boolean mIsRecording = false;
    private volatile boolean mMuxerStarted = false;
    private volatile boolean mMediaRecorderStarted = false;
    private final Object mMuxerLock = new Object();
    private AudioRecord mAudioRecord;
    private Thread mAudioThread;
    private Thread mVideoThread;
    private MediaMuxer mMediaMuxer;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private Surface mInputSurface;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        boolean isAction = false;

        //Check if there was an action called
        if (intent != null) {

            if (intent.getAction() != null) {
                isAction = true;
            }

            //If there was an action, check what action it was
            //Called when recording should be paused or resumed
            if (isAction) {
                //Pause Recording
                if (intent.getAction().equals("pause")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        pauseRecording();
                    }
                }
                //Resume Recording
                else if (intent.getAction().equals("resume")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        resumeRecording();
                    }
                }
            }
            //Start Recording
            else {
                //Get intent extras
                hasMaxFileBeenReached = false;
                mIntent = intent;
                maxFileSize = intent.getLongExtra(MAX_FILE_SIZE_KEY, NO_SPECIFIED_MAX_SIZE);
                byte[] notificationSmallIcon = intent.getByteArrayExtra("notificationSmallBitmap");
                int notificationSmallVector = intent.getIntExtra("notificationSmallVector", 0);
                String notificationTitle = intent.getStringExtra("notificationTitle");
                String notificationDescription = intent.getStringExtra("notificationDescription");
                String notificationButtonText = intent.getStringExtra("notificationButtonText");
                orientationHint = intent.getIntExtra("orientation", 400);
                mResultCode = intent.getIntExtra("code", -1);
                mResultData = intent.getParcelableExtra("data");
                mScreenWidth = intent.getIntExtra("width", 0);
                mScreenHeight = intent.getIntExtra("height", 0);

                if (intent.getStringExtra("mUri") != null) {
                    returnedUri = Uri.parse(intent.getStringExtra("mUri"));
                }

                if (mScreenHeight == 0 || mScreenWidth == 0) {
                    HBRecorderCodecInfo hbRecorderCodecInfo = new HBRecorderCodecInfo();
                    hbRecorderCodecInfo.setContext(this);
                    mScreenHeight = hbRecorderCodecInfo.getMaxSupportedHeight();
                    mScreenWidth = hbRecorderCodecInfo.getMaxSupportedWidth();
                }

                mScreenDensity = intent.getIntExtra("density", 1);
                isVideoHD = intent.getBooleanExtra("quality", true);
                isAudioEnabled = intent.getBooleanExtra("audio", true);
                path = intent.getStringExtra("path");
                name = intent.getStringExtra("fileName");
                String audioSource = intent.getStringExtra("audioSource");
                String videoEncoder = intent.getStringExtra("videoEncoder");
                videoFrameRate = intent.getIntExtra("videoFrameRate", 30);
                videoBitrate = intent.getIntExtra("videoBitrate", 40000000);

                if (audioSource != null) {
                    setAudioSourceAsInt(audioSource);
                }
                if (videoEncoder != null) {
                    setvideoEncoderAsInt(videoEncoder);
                }

                filePath = name;
                audioBitrate = intent.getIntExtra("audioBitrate", 128000);
                audioSamplingRate = intent.getIntExtra("audioSamplingRate", 44100);
                outputFormat = intent.getStringExtra("outputFormat");
                if (outputFormat != null) {
                    setOutputFormatAsInt(outputFormat);
                }

                isCustomSettingsEnabled = intent.getBooleanExtra("enableCustomSettings", false);

                //Set notification notification button text if developer did not
                if (notificationButtonText == null) {
                    notificationButtonText = "STOP RECORDING";
                }
                //Set notification bitrate if developer did not
                if (audioBitrate == 0) {
                    audioBitrate = 128000;
                }
                //Set notification sampling rate if developer did not
                if (audioSamplingRate == 0) {
                    audioSamplingRate = 44100;
                }
                //Set notification title if developer did not
                if (notificationTitle == null || notificationTitle.equals("")) {
                    notificationTitle = getString(R.string.stop_recording_notification_title);
                }
                //Set notification description if developer did not
                if (notificationDescription == null || notificationDescription.equals("")) {
                    notificationDescription = getString(R.string.stop_recording_notification_message);
                }

                //Notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String channelId = "001";
                    String channelName = "RecordChannel";
                    NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE);
                    channel.setLightColor(Color.BLUE);
                    channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.createNotificationChannel(channel);
                        Notification notification;

                        Intent myIntent = new Intent(this, NotificationReceiver.class);
                        PendingIntent pendingIntent;

                        if (Build.VERSION.SDK_INT >= 31) {
                            pendingIntent = PendingIntent.getBroadcast(this, 0, myIntent, PendingIntent.FLAG_IMMUTABLE);
                        } else {
                            pendingIntent = PendingIntent.getBroadcast(this, 0, myIntent, 0);

                        }

                        Notification.Action action = new Notification.Action.Builder(
                                Icon.createWithResource(this, android.R.drawable.presence_video_online),
                                notificationButtonText,
                                pendingIntent).build();

                        if (notificationSmallIcon != null) {
                            Bitmap bmp = BitmapFactory.decodeByteArray(notificationSmallIcon, 0, notificationSmallIcon.length);
                            //Modify notification badge
                            notification = new Notification.Builder(getApplicationContext(), channelId).setOngoing(true).setSmallIcon(Icon.createWithBitmap(bmp)).setContentTitle(notificationTitle).setContentText(notificationDescription).addAction(action).build();

                        } else if (notificationSmallVector != 0) {
                            notification = new Notification.Builder(getApplicationContext(), channelId).setOngoing(true).setSmallIcon(notificationSmallVector).setContentTitle(notificationTitle).setContentText(notificationDescription).addAction(action).build();
                        } else {
                            //Modify notification badge
                            notification = new Notification.Builder(getApplicationContext(), channelId).setOngoing(true).setSmallIcon(R.drawable.icon).setContentTitle(notificationTitle).setContentText(notificationDescription).addAction(action).build();
                        }
                        startFgs(101, notification);
                    }
                } else {
                    startFgs(101, new Notification());
                }


                if (returnedUri == null) {
                    if (path == null) {
                        path = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
                    }
                }

                //Init MediaProjection
                try {
                    initMediaProjection();
                } catch (Exception e) {
                    ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                    Bundle bundle = new Bundle();
                    bundle.putString(ERROR_REASON_KEY, Log.getStackTraceString(e));
                    if (receiver != null) {
                        receiver.send(Activity.RESULT_OK, bundle);
                    }
                }

                //Init MediaRecorder
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        initRecorder();
                        ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                        Bundle bundle = new Bundle();
                        bundle.putInt(ON_START_KEY, ON_START);
                        if (receiver != null) {
                            receiver.send(Activity.RESULT_OK, bundle);
                        }
                    } else {
                        initMediaRecorder();
                    }
                } catch (Exception e) {
                    ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                    Bundle bundle = new Bundle();
                    bundle.putString(ERROR_REASON_KEY, Log.getStackTraceString(e));
                    if (receiver != null) {
                        receiver.send(Activity.RESULT_OK, bundle);
                    }
                }

                //Init VirtualDisplay
                try {
                    if (mMediaRecorder != null) {
                        initVirtualDisplay();
                    } else {
                        setupVirtualDisplay();
                    }
                } catch (Exception e) {
                    ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                    Bundle bundle = new Bundle();
                    bundle.putString(ERROR_REASON_KEY, Log.getStackTraceString(e));
                    if (receiver != null) {
                        receiver.send(Activity.RESULT_OK, bundle);
                    }
                }

                if (mMediaRecorder != null) {
                    mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                        @Override
                        public void onError(MediaRecorder mediaRecorder, int what, int extra) {
                            if (what == 268435556 && hasMaxFileBeenReached) {
                                // Benign error b/c recording is too short and has no frames. See SO: https://stackoverflow.com/questions/40616466/mediarecorder-stop-failed-1007
                                return;
                            }
                            ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                            Bundle bundle = new Bundle();
                            bundle.putInt(ERROR_KEY, SETTINGS_ERROR);
                            bundle.putString(ERROR_REASON_KEY, String.valueOf(what));
                            if (receiver != null) {
                                receiver.send(Activity.RESULT_OK, bundle);
                            }
                        }
                    });

                    mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                        @Override
                        public void onInfo(MediaRecorder mr, int what, int extra) {
                            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                                hasMaxFileBeenReached = true;
                                Log.i(TAG, String.format(Locale.US, "onInfoListen what : %d | extra %d", what, extra));
                                ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                                Bundle bundle = new Bundle();
                                bundle.putInt(ERROR_KEY, MAX_FILE_SIZE_REACHED_ERROR);
                                bundle.putString(ERROR_REASON_KEY, getString(R.string.max_file_reached));
                                if (receiver != null) {
                                    receiver.send(Activity.RESULT_OK, bundle);
                                }
                            }
                        }
                    });

                    //Start Recording
                    try {
                        mMediaRecorder.start();
                        mMediaRecorderStarted = true;
                        ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                        Bundle bundle = new Bundle();
                        bundle.putInt(ON_START_KEY, ON_START);
                        if (receiver != null) {
                            receiver.send(Activity.RESULT_OK, bundle);
                        }
                    } catch (Exception e) {
                        mMediaRecorderStarted = false;
                        // From the tests I've done, this can happen if another application is using the mic or if an unsupported video encoder was selected
                        ResultReceiver receiver = intent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                        Bundle bundle = new Bundle();
                        bundle.putInt(ERROR_KEY, SETTINGS_ERROR);
                        bundle.putString(ERROR_REASON_KEY, Log.getStackTraceString(e));
                        if (receiver != null) {
                            receiver.send(Activity.RESULT_OK, bundle);
                        }
                    }
                }
            }
        } else {
            stopSelf(startId);
        }

        return Service.START_STICKY;
    }

    //Pause Recording
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void pauseRecording(){
        if (mMediaRecorder != null) {
            mMediaRecorder.pause();
        }
        ResultReceiver receiver = mIntent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
        Bundle bundle = new Bundle();
        bundle.putString(ON_PAUSE_KEY, ON_PAUSE);
        if (receiver != null) {
            receiver.send(Activity.RESULT_OK, bundle);
        }
    }

    //Resume Recording
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void resumeRecording(){
        if (mMediaRecorder != null) {
            mMediaRecorder.resume();
        }
        ResultReceiver receiver = mIntent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
        Bundle bundle = new Bundle();
        bundle.putString(ON_RESUME_KEY, ON_RESUME);
        if (receiver != null) {
            receiver.send(Activity.RESULT_OK, bundle);
        }
    }

    //Set output format as int based on what developer has provided
    //It is important to provide one of the following and nothing else.
    private void setOutputFormatAsInt(String outputFormat) {
        switch (outputFormat) {
            case "DEFAULT":
                outputFormatAsInt = 0;
                break;
            case "THREE_GPP":
                outputFormatAsInt = 1;
                break;
            case "AMR_NB":
                outputFormatAsInt = 3;
                break;
            case "AMR_WB":
                outputFormatAsInt = 4;
                break;
            case "AAC_ADTS":
                outputFormatAsInt = 6;
                break;
            case "MPEG_2_TS":
                outputFormatAsInt = 8;
                break;
            case "WEBM":
                outputFormatAsInt = 9;
                break;
            case "OGG":
                outputFormatAsInt = 11;
                break;
            case "MPEG_4":
            default:
                outputFormatAsInt = 2;
        }
    }

    private String getExtension(String outputFormat) {
        switch (outputFormat) {
            case "THREE_GPP":
                return ".3gp";
            case "AMR_NB":
                return ".amr";
            case "AMR_WB":
                return ".amr";
            case "AAC_ADTS":
                return ".aac";
            case "MPEG_2_TS":
                return ".ts";
            case "WEBM":
                return ".webm";
            case "OGG":
                return ".ogg";
            default:
                return ".mp4"; // Default to .mp4 for unknown formats
        }
    }

    //Set video encoder as int based on what developer has provided
    //It is important to provide one of the following and nothing else.
    private void setvideoEncoderAsInt(String encoder) {
        switch (encoder) {
            case "DEFAULT":
                videoEncoderAsInt = 0;
                break;
            case "H263":
                videoEncoderAsInt = 1;
                break;
            case "H264":
                videoEncoderAsInt = 2;
                break;
            case "MPEG_4_SP":
                videoEncoderAsInt = 3;
                break;
            case "VP8":
                videoEncoderAsInt = 4;
                break;
            case "HEVC":
                videoEncoderAsInt = 5;
                break;
        }
    }

    //Set audio source as int based on what developer has provided
    //It is important to provide one of the following and nothing else.
    private void setAudioSourceAsInt(String audioSource) {
        switch (audioSource) {
            case "DEFAULT":
                audioSourceAsInt = 0;
                break;
            case "MIC":
                audioSourceAsInt = 1;
                break;
            case "VOICE_UPLINK":
                audioSourceAsInt = 2;
                break;
            case "VOICE_DOWNLINK":
                audioSourceAsInt = 3;
                break;
            case "VOICE_CALL":
                audioSourceAsInt = 4;
                break;
            case "CAMCODER":
                audioSourceAsInt = 5;
                break;
            case "VOICE_RECOGNITION":
                audioSourceAsInt = 6;
                break;
            case "VOICE_COMMUNICATION":
                audioSourceAsInt = 7;
                break;
            case "REMOTE_SUBMIX":
                audioSourceAsInt = 8;
                break;
            case "UNPROCESSED":
                audioSourceAsInt = 9;
                break;
            case "VOICE_PERFORMANCE":
                audioSourceAsInt = 10;
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initMediaProjection() {
        mMediaProjection = ((MediaProjectionManager) Objects.requireNonNull(getSystemService(Context.MEDIA_PROJECTION_SERVICE))).getMediaProjection(mResultCode, mResultData);
        Handler handler = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mMediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                }
            }, handler);
        } else {
            mMediaProjection.registerCallback(new MediaProjection.Callback() {
                // Nothing
                // We don't use it but register it to avoid runtime error from SDK 34+.
            }, handler);
        }
    }

    //Return the output file path as string
    public static String getFilePath() {
        return filePath;
    }

    //Return the name of the output file
    public static String getFileName() {
        return fileName;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initRecorder() throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");
        String videoQuality = "HD";
        if (!isVideoHD) {
            videoQuality = "SD";
        }
        if (name == null) {
            name = videoQuality + curTime;
        }

        filePath = path + "/" + name + ".mp4";

        fileName = name + ".mp4";

        mMediaRecorder = null;
        if (returnedUri != null) {
            try {
                ContentResolver contentResolver = getContentResolver();
                FileDescriptor inputPFD = Objects.requireNonNull(contentResolver.openFileDescriptor(returnedUri, "rw")).getFileDescriptor();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setupMuxer(inputPFD, mScreenWidth, mScreenHeight);
                    mIsRecording = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startAudioCapture();
                    }
                    startVideoEncoding();

                }
            } catch (Exception e) {
                ResultReceiver receiver = mIntent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                Bundle bundle = new Bundle();
                bundle.putString(ERROR_REASON_KEY, Log.getStackTraceString(e));
                if (receiver != null) {
                    receiver.send(Activity.RESULT_OK, bundle);
                }
            }
        }else{
            if (outputFormat!=null){
                filePath = path + "/" + name + getExtension(outputFormat);
                fileName = name + getExtension(outputFormat);
            }else {
                filePath = path + "/" + name + ".mp4";
                fileName = name + ".mp4";
            }
            setupMuxer(filePath, mScreenWidth, mScreenHeight);
            mIsRecording = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startAudioCapture();
            }
            startVideoEncoding();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initMediaRecorder() throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");
        String videoQuality = "HD";
        if (!isVideoHD) {
            videoQuality = "SD";
        }
        if (name == null) {
            name = videoQuality + curTime;
        }

        filePath = path + "/" + name + ".mp4";

        fileName = name + ".mp4";

        mMediaRecorder = new MediaRecorder();

        if (isAudioEnabled) {
            mMediaRecorder.setAudioSource(audioSourceAsInt);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(outputFormatAsInt);

        if (orientationHint != 400){
            mMediaRecorder.setOrientationHint(orientationHint);
        }

        if (isAudioEnabled) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setAudioEncodingBitRate(audioBitrate);
            mMediaRecorder.setAudioSamplingRate(audioSamplingRate);
        }

        mMediaRecorder.setVideoEncoder(videoEncoderAsInt);

        if (returnedUri != null) {
            try {
                ContentResolver contentResolver = getContentResolver();
                FileDescriptor inputPFD = Objects.requireNonNull(contentResolver.openFileDescriptor(returnedUri, "rw")).getFileDescriptor();
                mMediaRecorder.setOutputFile(inputPFD);
            } catch (Exception e) {
                ResultReceiver receiver = mIntent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
                Bundle bundle = new Bundle();
                bundle.putString(ERROR_REASON_KEY, Log.getStackTraceString(e));
                if (receiver != null) {
                    receiver.send(Activity.RESULT_OK, bundle);
                }
            }
        }else{
            if (outputFormat!=null){
                filePath = path + "/" + name + getExtension(outputFormat);
                fileName = name + getExtension(outputFormat);
            }else {
                filePath = path + "/" + name + ".mp4";
                fileName = name + ".mp4";
            }
            mMediaRecorder.setOutputFile(filePath);
        }
        mMediaRecorder.setVideoSize(mScreenWidth, mScreenHeight);

        if (!isCustomSettingsEnabled) {
            if (!isVideoHD) {
                mMediaRecorder.setVideoEncodingBitRate(12000000);
                mMediaRecorder.setVideoFrameRate(30);
            } else {
                mMediaRecorder.setVideoEncodingBitRate(5 * mScreenWidth * mScreenHeight);
                mMediaRecorder.setVideoFrameRate(60); //after setVideoSource(), setOutFormat()
            }
        } else {
            mMediaRecorder.setVideoEncodingBitRate(videoBitrate);
            mMediaRecorder.setVideoFrameRate(videoFrameRate);
        }

        // Catch approaching file limit
        if ( maxFileSize > NO_SPECIFIED_MAX_SIZE) {
            mMediaRecorder.setMaxFileSize(maxFileSize); // in bytes
        }

        mMediaRecorder.prepare();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupMuxer(String outputPath, int width, int height) throws IOException {
        mMediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupMuxer(FileDescriptor outputPath, int width, int height) throws IOException {
        mMediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        setupEncoder(width, height);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupEncoder(int width, int height) throws IOException {
        // 设置视频编码器
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        mVideoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();

        // 设置音频编码器
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        mAudioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupVirtualDisplay() {
        if (mMediaProjection == null) {
            Log.d(TAG, "setupVirtualDisplay: " + " Media projection is not initialized properly.");
            return;
        }
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG,
                mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mInputSurface, null, null);
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startAudioCapture() {
        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();

        mAudioRecord = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2)
                .build();

        mAudioThread = new Thread(() -> {
            ByteBuffer inputBuffer;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean audioTrackAdded = false;
            long startTime = System.nanoTime();

            mAudioRecord.startRecording();
            while (mIsRecording) {
                // 检查编码器有效性
                if (mAudioEncoder == null || mAudioRecord == null) {
                    break;
                }
                
                try {
                    int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            int bytesRead = mAudioRecord.read(inputBuffer, inputBuffer.capacity());
                            if (bytesRead > 0) {
                                mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, (System.nanoTime() - startTime) / 1000, 0);
                            }
                        }
                    }

                    int outputBufferIndex = mAudioEncoder.dequeueOutputBuffer(bufferInfo, 0);
                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!audioTrackAdded) {
                            mAudioTrackIndex = mMediaMuxer.addTrack(mAudioEncoder.getOutputFormat());
                            audioTrackAdded = true;
                            checkStartMuxer();
                        }
                    } else if (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = mAudioEncoder.getOutputBuffer(outputBufferIndex);
                        if (mMuxerStarted && outputBuffer != null) {
                            synchronized (mMuxerLock) {
                                if (mMuxerStarted && mMediaMuxer != null) {
                                    try {
                                        mMediaMuxer.writeSampleData(mAudioTrackIndex, outputBuffer, bufferInfo);
                                    } catch (IllegalStateException | IllegalArgumentException e) {
                                        Log.e(TAG, "Error writing audio sample", e);
                                    }
                                }
                            }
                        }
                        mAudioEncoder.releaseOutputBuffer(outputBufferIndex, false);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "AudioEncoder operation failed, likely stopped", e);
                    break;
                }
            }

            // 安全停止和释放音频资源
            if (mAudioRecord != null) {
                try {
                    mAudioRecord.stop();
                } catch (IllegalStateException e) {
                    Log.w(TAG, "AudioRecord already stopped", e);
                }
                try {
                    mAudioRecord.release();
                } catch (Exception e) {
                    Log.w(TAG, "AudioRecord release error", e);
                }
            }
            if (mAudioEncoder != null) {
                try {
                    mAudioEncoder.stop();
                } catch (IllegalStateException e) {
                    Log.w(TAG, "AudioEncoder already stopped", e);
                }
                try {
                    mAudioEncoder.release();
                } catch (Exception e) {
                    Log.w(TAG, "AudioEncoder release error", e);
                }
            }
            // Log.d(TAG, "AudioEncoder stop");
        });

        mAudioThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startVideoEncoding() {
        mVideoThread = new Thread(() -> {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean videoTrackAdded = false;
            long startTime = System.nanoTime();

            while (mIsRecording) {
                // 检查编码器有效性，避免在停止过程中崩溃
                if (mVideoEncoder == null) {
                    break;
                }
                
                try {
                    int outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!videoTrackAdded) {
                            mVideoTrackIndex = mMediaMuxer.addTrack(mVideoEncoder.getOutputFormat());
                            videoTrackAdded = true;
                            checkStartMuxer();
                        }
                    } else if (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = mVideoEncoder.getOutputBuffer(outputBufferIndex);
                        bufferInfo.presentationTimeUs = (System.nanoTime() - startTime) / 1000;
                        if (mMuxerStarted && outputBuffer != null) {
                            synchronized (mMuxerLock) {
                                if (mMuxerStarted && mMediaMuxer != null) {
                                    try {
                                        mMediaMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, bufferInfo);
                                    } catch (IllegalStateException | IllegalArgumentException e) {
                                        Log.e(TAG, "Error writing video sample", e);
                                    }
                                }
                            }
                        }
                        mVideoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "VideoEncoder dequeueOutputBuffer failed, likely stopped", e);
                    break;
                }
            }

            // 安全停止和释放编码器
            if (mVideoEncoder != null) {
                try {
                    mVideoEncoder.stop();
                } catch (IllegalStateException e) {
                    Log.w(TAG, "VideoEncoder already stopped", e);
                }
                try {
                    mVideoEncoder.release();
                } catch (Exception e) {
                    Log.w(TAG, "VideoEncoder release error", e);
                }
            }
            // Log.d(TAG, "VideoEncoder stop");
        });

        mVideoThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private synchronized void checkStartMuxer() {
        synchronized (mMuxerLock) {
            if (!mMuxerStarted && mVideoTrackIndex != -1 && mAudioTrackIndex != -1 && mMediaMuxer != null) {
                try {
                    mMediaMuxer.start();
                    mMuxerStarted = true;
                    Log.d(TAG, "MediaMuxer started");
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Failed to start MediaMuxer", e);
                    mMuxerStarted = false;
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initVirtualDisplay() {
        if (mMediaProjection == null) {
            Log.d(TAG, "initVirtualDisplay: " + " Media projection is not initialized properly.");
            return;
        }
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG, mScreenWidth, mScreenHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder.getSurface(), null, null);
    }

    private void startFgs(int notificationId, Notification notificaton) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            startForeground(notificationId, notificaton, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notificaton, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(notificationId, notificaton);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDestroy() {
        super.onDestroy();
        resetAll();
        callOnComplete();

    }

    private void callOnComplete() {
        if ( mIntent != null ) {
            ResultReceiver receiver = mIntent.getParcelableExtra(ScreenRecordService.BUNDLED_LISTENER);
            Bundle bundle = new Bundle();
            bundle.putString(ON_COMPLETE_KEY, ON_COMPLETE);
            if (receiver != null) {
                receiver.send(Activity.RESULT_OK, bundle);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void resetAll() {
        mIsRecording = false;

        if (mAudioThread != null) {
            try {
                mAudioThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error joining audio thread", e);
            }
            mAudioThread = null;
        }

        if (mVideoThread != null) {
            try {
                mVideoThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error joining video thread", e);
            }
            mVideoThread = null;
        }

        // 停止和释放 MediaMuxer
        synchronized (mMuxerLock) {
            if (mMediaMuxer != null) {
                try {
                    if (mMuxerStarted) {
                        mMediaMuxer.stop();
                        Log.d(TAG, "MediaMuxer stopped");
                    }
                    mMediaMuxer.release();
                    Log.d(TAG, "MediaMuxer released");
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error stopping media muxer", e);
                } finally {
                    mMediaMuxer = null;
                    mMuxerStarted = false;
                }
            }
        }

        // 清理编码器资源（由线程内部安全释放，这里只清空引用）
        mVideoEncoder = null;
        mAudioEncoder = null;
        mAudioRecord = null;

        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }

        if (mMediaRecorder != null) {
            mMediaRecorder.setOnErrorListener(null);
            try {
                if (mMediaRecorderStarted) {
                    mMediaRecorder.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Skipping MediaRecorder.stop() due to state error", e);
            } finally {
                mMediaRecorder.release();
                mMediaRecorder = null;
                mMediaRecorderStarted = false;
            }
        }

        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }

        mMuxerStarted = false;
        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;

        stopForeground(true);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
