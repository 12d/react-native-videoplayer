package com.brentvatne.react;

import android.media.MediaPlayer;

import com.facebook.react.uimanager.ThemedReactContext;

/**
 * Created by chen on 2017/5/27.
 */

public class FullscreenPlayer extends ReactVideoView {
    public ReactVideoView tinyPlayer = null;
    public FullscreenPlayer(ThemedReactContext themedReactContext){
        super(themedReactContext);

    }
    public FullscreenPlayer(ThemedReactContext themedReactContext, ReactVideoView tinyPlayer){
        super(themedReactContext);
        this.tinyPlayer = tinyPlayer;
    }
    @Override
    public void onCompletion(MediaPlayer mp) {
//        this.complete();
        if(this.tinyPlayer!=null){
            tinyPlayer.complete();
            tinyPlayer.exitFullscreen();
        }

//        super.onCompletion(mp);
    }
}
