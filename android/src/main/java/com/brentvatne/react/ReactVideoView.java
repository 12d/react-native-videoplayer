package com.brentvatne.react;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.MediaController;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.yqritc.scalablevideoview.ScalableType;
import com.yqritc.scalablevideoview.ScalableVideoView;
import com.yqritc.scalablevideoview.ScaleManager;
import com.yqritc.scalablevideoview.Size;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ReactVideoView extends ScalableVideoView implements MediaPlayer.OnPreparedListener, MediaPlayer
        .OnErrorListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnInfoListener, LifecycleEventListener, MediaController.MediaPlayerControl {

    public enum Events {
        EVENT_LOAD_START("onVideoLoadStart"),
        EVENT_LOAD("onVideoLoad"),
        EVENT_ERROR("onVideoError"),
        EVENT_PROGRESS("onVideoProgress"),
        EVENT_SEEK("onVideoSeek"),
        EVENT_END("onVideoEnd"),
        EVENT_STALLED("onPlaybackStalled"),
        EVENT_RESUME("onPlaybackResume"),
        EVENT_READY_FOR_DISPLAY("onReadyForDisplay");

        private final String mName;

        Events(final String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    public static final String EVENT_PROP_FAST_FORWARD = "canPlayFastForward";
    public static final String EVENT_PROP_SLOW_FORWARD = "canPlaySlowForward";
    public static final String EVENT_PROP_SLOW_REVERSE = "canPlaySlowReverse";
    public static final String EVENT_PROP_REVERSE = "canPlayReverse";
    public static final String EVENT_PROP_STEP_FORWARD = "canStepForward";
    public static final String EVENT_PROP_STEP_BACKWARD = "canStepBackward";

    public static final String EVENT_PROP_DURATION = "duration";
    public static final String EVENT_PROP_PLAYABLE_DURATION = "playableDuration";
    public static final String EVENT_PROP_CURRENT_TIME = "currentTime";
    public static final String EVENT_PROP_SEEK_TIME = "seekTime";
    public static final String EVENT_PROP_NATURALSIZE = "naturalSize";
    public static final String EVENT_PROP_WIDTH = "width";
    public static final String EVENT_PROP_HEIGHT = "height";
    public static final String EVENT_PROP_ORIENTATION = "orientation";

    public static final String EVENT_PROP_ERROR = "error";
    public static final String EVENT_PROP_WHAT = "what";
    public static final String EVENT_PROP_EXTRA = "extra";

    private ThemedReactContext mThemedReactContext;
    private RCTEventEmitter mEventEmitter;

    private Handler mProgressUpdateHandler = new Handler();
    private Runnable mProgressUpdateRunnable = null;
    private Handler videoControlHandler = new Handler();
    private MediaController mediaController;


    private String mSrcUriString = null;
    private String mSrcType = "mp4";
    private boolean mSrcIsNetwork = false;
    private boolean mSrcIsAsset = false;
    private ScalableType mResizeMode = ScalableType.LEFT_TOP;
    private boolean mRepeat = false;
    private boolean mPaused = false;
    private boolean mMuted = false;
    private float mVolume = 1.0f;
    private float mRate = 1.0f;
    private boolean mPlayInBackground = false;
    private boolean mActiveStatePauseStatus = false;

    private int mMainVer = 0;
    private int mPatchVer = 0;

    private boolean mMediaPlayerValid = false; // True if mMediaPlayer is in prepared, started, paused or completed state.

    private int mVideoDuration = 0;
    private int mVideoBufferedDuration = 0;
    private boolean isCompleted = false;
    private boolean mUseNativeControls = false;
    private boolean mFullscreen = false;
    private ViewGroup viewGroup;
    private ReactVideoView fullscreenPlayer = null;
    private FrameLayout maskLayout;
    private int lastLayout;
    private boolean isPlaying = false;
    public ReactVideoView(ThemedReactContext themedReactContext) {
        super(themedReactContext);

        mThemedReactContext = themedReactContext;
        mEventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
        themedReactContext.addLifecycleEventListener(this);

        initializeMediaPlayerIfNeeded();
        setSurfaceTextureListener(this);

        mProgressUpdateRunnable = new Runnable() {
            @Override
            public void run() {

                if (mMediaPlayerValid && !isCompleted &&!mPaused) {
                    WritableMap event = Arguments.createMap();
                    event.putDouble(EVENT_PROP_CURRENT_TIME, mMediaPlayer.getCurrentPosition() / 1000.0);
                    event.putDouble(EVENT_PROP_PLAYABLE_DURATION, mVideoBufferedDuration / 1000.0); //TODO:mBufferUpdateRunnable
                    mEventEmitter.receiveEvent(getId(), Events.EVENT_PROGRESS.toString(), event);

                    // Check for update after an interval
                    // TODO: The update interval is fixed at 250. There is a property in React component that defines this value. Totally ignored !!!
                    mProgressUpdateHandler.postDelayed(mProgressUpdateRunnable, 250);
                }
            }
        };
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mUseNativeControls) {
            initializeMediaControllerIfNeeded();
            mediaController.show();
        }

        return super.onTouchEvent(event);
    }
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d("xxxxxx", "onSurfaceTextureSizeChanged");
    }
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        Log.d("xxxxxx","onLayout");
        super.onLayout(changed, left, top, right, bottom);

        if (!changed || !mMediaPlayerValid) {
            return;
        }

        int videoWidth = getVideoWidth();
        int videoHeight = getVideoHeight();

        if (videoWidth == 0 || videoHeight == 0) {
            return;
        }
        Log.d("xxxxx",  String.valueOf(getHeight())+"/"+String.valueOf(getWidth())+videoWidth+"/"+videoHeight);
//        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(getHeight()*3, getWidth()*4);
//        lp.setMargins(0,0,0,0);
//        this.setLayoutParams(lp);
        Size viewSize = new Size(getWidth(), getHeight());
        Size videoSize = new Size(videoWidth, videoHeight);
        ScaleManager scaleManager = new ScaleManager(viewSize, videoSize);
        Matrix matrix = scaleManager.getScaleMatrix(mScalableType);
        if (matrix != null) {
            setTransform(matrix);
        }
    }

    private void initializeMediaPlayerIfNeeded() {
        if (mMediaPlayer == null) {
            mMediaPlayerValid = false;
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.setOnVideoSizeChangedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnBufferingUpdateListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnInfoListener(this);
        }
    }

    private void initializeMediaControllerIfNeeded() {
        if (mediaController == null) {
            mediaController = new MediaController(this.getContext());
        }
    }

    public void cleanupMediaPlayerResources() {
        if ( mediaController != null ) {
            mediaController.hide();
        }
        if ( mMediaPlayer != null ) {
            mMediaPlayerValid = false;
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
    }
    public void complete(){
//        this.release();
//        this.cleanupMediaPlayerResources();
//        this.onDetachedFromWindow();
        this.onCompletion(mMediaPlayer);
    }
    public void setSrc(final String uriString, final String type, final boolean isNetwork, final boolean isAsset) {
        setSrc(uriString,type,isNetwork,isAsset,0,0);
    }

    public void setSrc(final String uriString, final String type, final boolean isNetwork, final boolean isAsset, final int expansionMainVersion, final int expansionPatchVersion) {

        mSrcUriString = uriString;
        mSrcType = type;
        mSrcIsNetwork = isNetwork;
        mSrcIsAsset = isAsset;
        mMainVer = expansionMainVersion;
        mPatchVer = expansionPatchVersion;


        mMediaPlayerValid = false;
        mVideoDuration = 0;
        mVideoBufferedDuration = 0;

        initializeMediaPlayerIfNeeded();
        mMediaPlayer.reset();

        try {
            if (isNetwork) {
                // Use the shared CookieManager to access the cookies
                // set by WebViews inside the same app
                CookieManager cookieManager = CookieManager.getInstance();

                Uri parsedUrl = Uri.parse(uriString);
                Uri.Builder builtUrl = parsedUrl.buildUpon();

                String cookie = cookieManager.getCookie(builtUrl.build().toString());

                Map<String, String> headers = new HashMap<String, String>();

                if (cookie != null) {
                    headers.put("Cookie", cookie);
                }

                setDataSource(mThemedReactContext, parsedUrl, headers);
            } else if (isAsset) {
                if (uriString.startsWith("content://")) {
                    Uri parsedUrl = Uri.parse(uriString);
                    setDataSource(mThemedReactContext, parsedUrl);
                } else {
                    setDataSource(uriString);
                }
            } else {
                ZipResourceFile expansionFile= null;
                AssetFileDescriptor fd= null;
                if(mMainVer>0) {
                    try {
                        expansionFile = APKExpansionSupport.getAPKExpansionZipFile(mThemedReactContext, mMainVer, mPatchVer);
                        fd = expansionFile.getAssetFileDescriptor(uriString.replace(".mp4","") + ".mp4");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
                if(fd==null) {
                    int identifier = mThemedReactContext.getResources().getIdentifier(
                            uriString,
                            "drawable",
                            mThemedReactContext.getPackageName()
                    );
                    if (identifier == 0) {
                        identifier = mThemedReactContext.getResources().getIdentifier(
                                uriString,
                                "raw",
                                mThemedReactContext.getPackageName()
                        );
                    }
                    setRawData(identifier);
                }
                else {
                    setDataSource(fd.getFileDescriptor(), fd.getStartOffset(),fd.getLength());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        WritableMap src = Arguments.createMap();
        src.putString(ReactVideoViewManager.PROP_SRC_URI, uriString);
        src.putString(ReactVideoViewManager.PROP_SRC_TYPE, type);
        src.putBoolean(ReactVideoViewManager.PROP_SRC_IS_NETWORK, isNetwork);
        if(mMainVer>0) {
            src.putInt(ReactVideoViewManager.PROP_SRC_MAINVER, mMainVer);
            if(mPatchVer>0) {
                src.putInt(ReactVideoViewManager.PROP_SRC_PATCHVER, mPatchVer);
            }
        }
        WritableMap event = Arguments.createMap();
        event.putMap(ReactVideoViewManager.PROP_SRC, src);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD_START.toString(), event);

        // not async to prevent random crashes on Android playback from local resource due to race conditions
        try {
            prepare(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setResizeModeModifier(final ScalableType resizeMode) {
        mResizeMode = resizeMode;

        if (mMediaPlayerValid) {
            setScalableType(resizeMode);
            invalidate();
        }
    }

    public void setRepeatModifier(final boolean repeat) {

        mRepeat = repeat;

        if (mMediaPlayerValid) {
            setLooping(repeat);
        }
    }

    public void setPausedModifier(final boolean paused) {

        mPaused = paused;

        if (!mMediaPlayerValid) {
            return;
        }

        if (mPaused) {
            if (mMediaPlayer.isPlaying()) {
                pause();
            }
        } else {
            if (!mMediaPlayer.isPlaying()) {
                start();

                // Also Start the Progress Update Handler
                mProgressUpdateHandler.post(mProgressUpdateRunnable);
            }
        }
    }

    public void setMutedModifier(final boolean muted) {
        mMuted = muted;

        if (!mMediaPlayerValid) {
            return;
        }

        if (mMuted) {
            setVolume(0, 0);
        } else {
            setVolume(mVolume, mVolume);
        }
    }

    public void setVolumeModifier(final float volume) {
        mVolume = volume;
        setMutedModifier(mMuted);
    }

    public void setRateModifier(final float rate) {
        mRate = rate;

        if (mMediaPlayerValid) {
            // TODO: Implement this.
            Log.e(ReactVideoViewManager.REACT_CLASS, "Setting playback rate is not yet supported on Android");
        }
    }

    public void applyModifiers() {
        setResizeModeModifier(mResizeMode);
        setRepeatModifier(mRepeat);
        setPausedModifier(mPaused);
        setMutedModifier(mMuted);
//        setRateModifier(mRate);
    }

    public void setPlayInBackground(final boolean playInBackground) {

        mPlayInBackground = playInBackground;
    }

    public void setControls(boolean controls) {
        this.mUseNativeControls = controls;
    }
    //TODO modified
    public void startWindowFullscreen() {
//        hideSupportActionBar(getContext());
//        JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(FULLSCREEN_ORIENTATION);

//        ThemedReactContext themedReactContext = (ThemedReactContext) getContext();
//        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();

//        this.setLayoutParams(params);
        Log.d("xxxxxx","fullscreen");
        mMediaPlayerValid = true;
//        this.setTop(0);
//        this.setBottom(0);
//        this.setRight(0);
//        this.setLeft(0);

        lastLayout = Window.ID_ANDROID_CONTENT;
        viewGroup = (ViewGroup) mThemedReactContext.getCurrentActivity().findViewById(Window.ID_ANDROID_CONTENT);
        maskLayout = (FrameLayout) LayoutInflater.from(mThemedReactContext).inflate(R.layout.fullscreen,viewGroup);
        fullscreenPlayer = new FullscreenPlayer(mThemedReactContext, this);
        fullscreenPlayer.setControls(true);
//        viewGroup.removeView(this);
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        int w = wm.getDefaultDisplay().getWidth();
        int h = wm.getDefaultDisplay().getHeight();
        Log.d("xxxxx", h+"/"+w);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h, w);
        lp.gravity = FrameLayout.TEXT_ALIGNMENT_GRAVITY;
//        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
//        SurfaceTexture mask = new SurfaceTexture();
        fullscreenPlayer.setResizeModeModifier(ScalableType.FIT_CENTER);
        this.isPlaying = mMediaPlayer.isPlaying();
        Log.d("xxxxxx","isPlaying"+(this.isPlaying()?"playing":"not playing"));
        this.pause();
        lp.setMargins(0, 0, 0, 0);
        this.hideHeaderBar();

        maskLayout.addView(fullscreenPlayer,w,h);
//        viewGroup.addView(maskLayout);
        fullscreenPlayer.setLayoutParams(lp);

        fullscreenPlayer.setSrc(mSrcUriString,mSrcType,mSrcIsNetwork,mSrcIsAsset);
        fullscreenPlayer.mMediaPlayerValid = true;
        fullscreenPlayer.seekTo(this.getCurrentPosition());
        //播放状态同步
        if(!this.isPlaying){
            fullscreenPlayer.pause();
        }
        mThemedReactContext.getCurrentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    private void hideHeaderBar(){

//        mThemedReactContext.getCurrentActivity().requestWindowFeature(Window.FEATURE_NO_TITLE);
//        ActionBar actionBar = mThemedReactContext.getCurrentActivity().getActionBar();
//        actionBar.hide();
//        mThemedReactContext.getCurrentActivity().getActionBar().hide();
        // 隐藏状态栏
//        mThemedReactContext.getCurrentActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mThemedReactContext.getCurrentActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
//        viewGroup.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    private void showHeaderBar(){
//        viewGroup.setSystemUiVisibility(View.EX);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 显示状态栏
        mThemedReactContext.getCurrentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    public void exitFullscreen(){
        if(fullscreenPlayer!=null) {
            this.showHeaderBar();
            this.seekTo(fullscreenPlayer.getCurrentPosition());
            if(this.isPlaying){
                Log.d("xxxxxx","fullscreen played"+fullscreenPlayer.getCurrentPosition());
                this.start();
            }
            maskLayout.removeView(fullscreenPlayer);
            viewGroup.removeView(maskLayout.findViewById(R.id.maskLayer));
//            maskLayout.setBackgroundColor(0xffffffff);
//            LayoutInflater.from(mThemedReactContext).inflate(lastLayout,viewGroup);
            fullscreenPlayer = null;
            maskLayout = null;
            mThemedReactContext.getCurrentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

    }
    public void setFullscreen(boolean fullscreen){
        mFullscreen = fullscreen;
        if(fullscreen ) {
            this.startWindowFullscreen();
        }else{
            if(fullscreenPlayer!=null) this.exitFullscreen();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {

        mMediaPlayerValid = true;
        mVideoDuration = mp.getDuration();

        WritableMap naturalSize = Arguments.createMap();
        naturalSize.putInt(EVENT_PROP_WIDTH, mp.getVideoWidth()*2);
        naturalSize.putInt(EVENT_PROP_HEIGHT, mp.getVideoHeight()*2);
//        if (mp.getVideoWidth() > mp.getVideoHeight())
//            naturalSize.putString(EVENT_PROP_ORIENTATION, "landscape");
//        else
//            naturalSize.putString(EVENT_PROP_ORIENTATION, "portrait");

        naturalSize.putString(EVENT_PROP_ORIENTATION, "landscape");
        WritableMap event = Arguments.createMap();
        event.putDouble(EVENT_PROP_DURATION, mVideoDuration / 1000.0);
        event.putDouble(EVENT_PROP_CURRENT_TIME, mp.getCurrentPosition() / 1000.0);
        event.putMap(EVENT_PROP_NATURALSIZE, naturalSize);
        // TODO: Actually check if you can.
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_REVERSE, true);
        event.putBoolean(EVENT_PROP_REVERSE, true);
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_STEP_BACKWARD, true);
        event.putBoolean(EVENT_PROP_STEP_FORWARD, true);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD.toString(), event);

        applyModifiers();

        if (mUseNativeControls) {
            initializeMediaControllerIfNeeded();
            mediaController.setMediaPlayer(this);
            mediaController.setAnchorView(this);

            videoControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    mediaController.setEnabled(true);
                    mediaController.show();
                }
            });
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {

        WritableMap error = Arguments.createMap();
        error.putInt(EVENT_PROP_WHAT, what);
        error.putInt(EVENT_PROP_EXTRA, extra);
        WritableMap event = Arguments.createMap();
        event.putMap(EVENT_PROP_ERROR, error);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_ERROR.toString(), event);
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_STALLED.toString(), Arguments.createMap());
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_RESUME.toString(), Arguments.createMap());
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_READY_FOR_DISPLAY.toString(), Arguments.createMap());
                break;

            default:
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        mVideoBufferedDuration = (int) Math.round((double) (mVideoDuration * percent) / 100.0);
    }

    @Override
    public void seekTo(int msec) {

        if (mMediaPlayerValid) {
            WritableMap event = Arguments.createMap();
            event.putDouble(EVENT_PROP_CURRENT_TIME, getCurrentPosition() / 1000.0);
            event.putDouble(EVENT_PROP_SEEK_TIME, msec / 1000.0);
            mEventEmitter.receiveEvent(getId(), Events.EVENT_SEEK.toString(), event);

            super.seekTo(msec);
            if (isCompleted && mVideoDuration != 0 && msec < mVideoDuration) {
                isCompleted = false;
            }
        }
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

        isCompleted = true;
        mFullscreen = false;
        mEventEmitter.receiveEvent(getId(), Events.EVENT_END.toString(), null);
    }

    @Override
    protected void onDetachedFromWindow() {
        mMediaPlayerValid = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {

        super.onAttachedToWindow();

        if(mMainVer>0) {
            setSrc(mSrcUriString, mSrcType, mSrcIsNetwork,mSrcIsAsset,mMainVer,mPatchVer);
        }
        else {
            setSrc(mSrcUriString, mSrcType, mSrcIsNetwork,mSrcIsAsset);
        }

    }

    @Override
    public void onHostPause() {
        if (mMediaPlayer != null && !mPlayInBackground) {
            mActiveStatePauseStatus = mPaused;

            // Pause the video in background
            setPausedModifier(true);
        }
    }

    @Override
    public void onHostResume() {
        if (mMediaPlayer != null && !mPlayInBackground) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    // Restore original state
                    setPausedModifier(mActiveStatePauseStatus);
                }
            });

        }
    }

    @Override
    public void onHostDestroy() {
    }
}
