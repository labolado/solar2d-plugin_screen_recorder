package plugin.screenRecorder;

import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class VideoPreviewActivity extends AppCompatActivity {
    private static final int SHARE_REQUEST_CODE = 77;

    private int mOriginOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        makeFullScreen();

        Intent intent = getIntent();
        // Uri videoUri = (Uri)intent.getSerializableExtra("videoUri");
        String videoFile = intent.getStringExtra("videoFile");
        Uri videoUri = Uri.parse(videoFile);

        mOriginOrientation = intent.getIntExtra("originOrientation", getRequestedOrientation());
        setRequestedOrientation(mOriginOrientation);

        setContentView(R.layout.video_preview);
        CustomVideoView videoView = findViewById(R.id.videoPreview);
        videoView.setVideoURI(videoUri);

        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        final boolean[] firstStart = {false};
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                calculateView(videoView, mp.getVideoWidth(), mp.getVideoHeight());
                firstStart[0] = true;
                videoView.start();
                videoView.pause();
                videoView.requestFocus();
                mediaController.show();
            }
        });


        ImageButton shareButton = findViewById(R.id.shareButton);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("video/mp4");
                shareIntent.putExtra(Intent.EXTRA_STREAM, videoUri);
                // shareIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mFilename);
                // shareIntent.putExtra(Intent.EXTRA_TITLE, mFilename);
                // shareIntent.putExtra(Intent.EXTRA_TITLE, mFilename);
                // activity.setRequestedOrientation(originOrientation);
                startActivityForResult(
                        Intent.createChooser(shareIntent, getResources().getText(R.string.share_chooser_title)), SHARE_REQUEST_CODE);
                // Intent chooserIntent = Intent.createChooser(shareIntent, getResources().getText(R.string.share_chooser_title));
                // ActivityResultLauncher<Intent> startActivityIntent = registerForActivityResult(
                //         new ActivityResultContracts.StartActivityForResult(),
                //         new ActivityResultCallback<ActivityResult>() {
                //             @Override
                //             public void onActivityResult(ActivityResult result) {
                //                 if (result.getResultCode() == Activity.RESULT_OK) {
                //                     setRequestedOrientation(originOrientation);
                //                 }
                //             }
                //         });
                // startActivityIntent.launch(chooserIntent);
            }
        });

        ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        RelativeLayout titleBar = findViewById(R.id.titleBar);
        // videoView.setPlayPauseListener(new CustomVideoView.PlayPauseListener() {
        // 	@Override
        // 	public void onPlay() {
        // 		if (!firstStart[0]) {
        // 			titleBar.setVisibility(View.GONE);
        // 		} else {
        // 			firstStart[0] = false;
        // 		}
        // 	}
        // 	@Override
        // 	public void onPause() {
        // 		titleBar.setVisibility(View.VISIBLE);
        // 	}
        // });

        // videoView.setOnTouchListener(new View.OnTouchListener() {
        // 	@Override
        // 	public boolean onTouch(View view, MotionEvent event) {
        // 		if (event.getAction() == MotionEvent.ACTION_DOWN) {
        // 			if (titleBar.getVisibility() == View.VISIBLE) {
        // 				titleBar.setVisibility(View.GONE);
        // 				// mediaController.hide();
        // 			} else {
        // 				titleBar.setVisibility(View.VISIBLE);
        // 				// mediaController.show();
        // 			}
        // 		}
        // 		return false;
        // 	}
        // });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                titleBar.setVisibility(View.VISIBLE);
                videoView.pause();
            }
        });

        // popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
        //     @Override
        //     public void onDismiss() {
        //         videoView.stopPlayback();
        //         activity.setRequestedOrientation(activity.getOrientationFromManifest());
        //     }
        // });

        TextView title = findViewById(R.id.previewTitle);
        title.setText(R.string.preview_title);
    }

    private void makeFullScreen() {
        View decorView = getWindow().getDecorView();
        int uiOptions = 0;
        uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            uiOptions |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decorView.setSystemUiVisibility(uiOptions);

        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        final int finalVis = uiOptions;
        decorView.setOnSystemUiVisibilityChangeListener(new android.view.View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibilityInt)
            {
                if((finalVis & android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0)
                {
                    decorView.setSystemUiVisibility(finalVis);
                }
            }
        });

    }

    private void calculateView(VideoView videoView, int videoWidth, int videoHeight) {
        // int videoViewWidth = videoView.getWidth();
        int videoViewHeight = videoView.getHeight();

        float videoAspectRatio = (float) videoWidth / videoHeight;
        float newVideoWidth = videoViewHeight * videoAspectRatio;
        reSetVideoViewWidth(videoView, (int) newVideoWidth);
    }

    private void reSetVideoViewWidth(VideoView videoView, int newWidth) {
        ViewGroup.LayoutParams lp = videoView.getLayoutParams();
        lp.width = newWidth;
        videoView.setLayoutParams(lp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SHARE_REQUEST_CODE) {
            if (mOriginOrientation > 0) {
                setRequestedOrientation(mOriginOrientation);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}