package com.google.flutter.plugins.audiofileplayer;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a MediaPlayer for remote asset use by AudiofileplayerPlugin.
 *
 * <p>Used for remote audio data only; loading occurs asynchronously, allowing program to continue
 * while data is received. Callers may call all other methods on {@link ManagedMediaPlayer}
 * immediately (i.e. before loading is complete); these will, if necessary, be delayed and re-called
 * internally upon loading completion.
 *
 * <p>Note that with async loading, errors such as invalid URLs and lack of connectivity are
 * reported asyncly via {@link RemoteManagedMediaPlayer.onError()}, instead of as Exceptions.
 * Unfortunately, this yields inscrutable and/or undifferentiated error codes, instead of discrete
 * Exception subclasses with human-readable error messages.
 */
class RemoteManagedMediaPlayer extends ManagedMediaPlayer{

    interface OnRemoteLoadListener {
        void onRemoteLoadComplete(boolean success);
    }

    private static final String TAG = RemoteManagedMediaPlayer.class.getSimpleName();
    private OnRemoteLoadListener onRemoteLoadListener;
    private boolean isPrepared;
    private List<Runnable> onPreparedRunnables = new ArrayList<>();


    public RemoteManagedMediaPlayer(
            String audioId,
            String remoteUrl,
            AudiofileplayerPlugin parentAudioPlugin,
            boolean looping,
            boolean playInBackground, Context context)
            throws IOException {
        super(audioId, parentAudioPlugin, looping, playInBackground, context);
        player.addMediaSource(new ProgressiveMediaSource.Factory(new DefaultHttpDataSource.Factory()
                .setUserAgent("ExoPlayer")
                .setAllowCrossProtocolRedirects(true))
                .createMediaSource(MediaItem.fromUri(remoteUrl)));
        player.prepare();
    }

    public void setOnRemoteLoadListener(OnRemoteLoadListener onRemoteLoadListener) {
        this.onRemoteLoadListener = onRemoteLoadListener;
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        super.onPlaybackStateChanged(playbackState);
        if(playbackState == Player.STATE_READY){
            Log.i(TAG, "on prepared");
            isPrepared = true;
            onRemoteLoadListener.onRemoteLoadComplete(true);
            for (Runnable r : onPreparedRunnables) {
                r.run();
            }
        }
    }

    @Override
    public void play(boolean playFromStart, int endpointMs) {
        if (!isPrepared) {
            onPreparedRunnables.add(() -> RemoteManagedMediaPlayer.super.play(playFromStart, endpointMs));
        } else {
            super.play(playFromStart, endpointMs);
        }
    }

    @Override
    public void release() {
        if (!isPrepared) {
            onPreparedRunnables.add(RemoteManagedMediaPlayer.super::release);
        } else {
            super.release();
        }
    }

    @Override
    public void seek(double positionSeconds) {
        if (!isPrepared) {
            onPreparedRunnables.add(() -> RemoteManagedMediaPlayer.super.seek(positionSeconds));
        } else {
            super.seek(positionSeconds);
        }
    }

    @Override
    public void pause() {
        if (!isPrepared) {
            onPreparedRunnables.add(RemoteManagedMediaPlayer.super::pause);
        } else {
            super.pause();
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        super.onPlayerError(error);
        onRemoteLoadListener.onRemoteLoadComplete(false);
    }
}
