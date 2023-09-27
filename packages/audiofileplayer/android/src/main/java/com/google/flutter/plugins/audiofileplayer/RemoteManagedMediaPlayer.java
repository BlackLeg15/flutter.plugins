package com.google.flutter.plugins.audiofileplayer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Util;

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
 * reported asyncly via {@link RemoteManagedMediaPlayer}, instead of as Exceptions.
 * Unfortunately, this yields inscrutable and/or undifferentiated error codes, instead of discrete
 * Exception subclasses with human-readable error messages.
 */
class RemoteManagedMediaPlayer extends ManagedMediaPlayer {
    private static final String FORMAT_SS = "ss";
    private static final String FORMAT_DASH = "dash";
    private static final String FORMAT_HLS = "hls";
    private static final String FORMAT_OTHER = "other";

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
        final DefaultHttpDataSource.Factory httpDataSourceFactory =
                new DefaultHttpDataSource.Factory()
                        .setUserAgent("ExoPlayer")
                        .setAllowCrossProtocolRedirects(true);
//        String formatHint = "other";
//        if(remoteUrl.startsWith("hls")){
//            formatHint = FORMAT_HLS;
//        }
        player.addMediaSource(buildMediaSource(Uri.parse(remoteUrl), httpDataSourceFactory, null, context));
        player.prepare();
    }

    private MediaSource buildMediaSource(
            Uri uri, DataSource.Factory mediaDataSourceFactory, String formatHint, Context context) {
        int type;
        if (formatHint == null) {
            type = Util.inferContentType(uri);
        } else {
            switch (formatHint) {
                case FORMAT_SS:
                    type = C.CONTENT_TYPE_SS;
                    break;
                case FORMAT_DASH:
                    type = C.CONTENT_TYPE_DASH;
                    break;
                case FORMAT_HLS:
                    type = C.CONTENT_TYPE_HLS;
                    break;
                case FORMAT_OTHER:
                    type = C.CONTENT_TYPE_OTHER;
                    break;
                default:
                    type = -1;
                    break;
            }
        }
        switch (type) {
            case C.CONTENT_TYPE_SS:
                return new SsMediaSource.Factory(
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory),
                        new DefaultDataSource.Factory(context, mediaDataSourceFactory))
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_DASH:
                return new DashMediaSource.Factory(
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                        new DefaultDataSource.Factory(context, mediaDataSourceFactory))
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_HLS:
                return new HlsMediaSource.Factory(mediaDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(mediaDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    public void setOnRemoteLoadListener(OnRemoteLoadListener onRemoteLoadListener) {
        this.onRemoteLoadListener = onRemoteLoadListener;
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        super.onPlaybackStateChanged(playbackState);
        if (playbackState == Player.STATE_READY && !isPrepared) {
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
