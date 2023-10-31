package com.google.flutter.plugins.audiofileplayer;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;

import java.lang.ref.WeakReference;

/** Base class for wrapping a MediaPlayer for use by AudiofileplayerPlugin. */
abstract class ManagedMediaPlayer implements Player.Listener {
  private static final String TAG = ManagedMediaPlayer.class.getSimpleName();
  public static final int PLAY_TO_END = -1;

  interface OnSeekCompleteListener {
    /** Called when asynchronous seeking has completed. */
    void onSeekComplete();
  }

  protected final AudiofileplayerPlugin parentAudioPlugin;
  protected final String audioId;
  protected final boolean playInBackground;
  protected final ExoPlayer player;
  final Handler handler;
  final Runnable pauseAtEndpointRunnable;
  private OnSeekCompleteListener onSeekCompleteListener;

  /** Runnable which repeatedly sends the player's position. */
  private final Runnable updatePositionData =
      new Runnable() {
        @Override
        public void run() {
          try {
            if (player.isPlaying()) {
              double positionSeconds = (double) player.getCurrentPosition() / 1000.0;
              parentAudioPlugin.handlePosition(audioId, positionSeconds);
            }
            handler.postDelayed(this, 250);
          } catch (Exception e) {
            Log.e(TAG, "Could not schedule position update for player", e);
          }
        }
      };

  protected ManagedMediaPlayer(
      String audioId,
      AudiofileplayerPlugin parentAudioPlugin,
      boolean looping,
      boolean playInBackground, Context context) {
    this.parentAudioPlugin = parentAudioPlugin;
    this.audioId = audioId;
    this.playInBackground = playInBackground;
    player = new ExoPlayer.Builder(context).build();
    //player.setLooping(looping);
    player.addListener(this);

    pauseAtEndpointRunnable = new PauseAtEndpointRunnable(this);

    handler = new Handler();
    handler.post(updatePositionData);
  }

  public void setOnSeekCompleteListener(OnSeekCompleteListener onSeekCompleteListener) {
    this.onSeekCompleteListener = onSeekCompleteListener;
  }

  public String getAudioId() {
    return audioId;
  }

  public double getDurationSeconds() {
    return (double) player.getDuration() / 1000.0; // Convert ms to seconds.
  }

  /**
   * Plays the audio.
   *
   * @param endpointMs the time, in milleseconds, to play to. To play until the end, pass {@link
   *     #PLAY_TO_END}.
   */
  public void play(boolean playFromStart, int endpointMs) {
    if (playFromStart) {
      player.seekTo(0);
    }
    if (endpointMs == PLAY_TO_END) {
      handler.removeCallbacks(pauseAtEndpointRunnable);
      player.play();
    } else {
      // If there is an endpoint, check that it is in the future, then start playback and schedule
      // the pausing after a duration.
      long positionMs = player.getCurrentPosition();
      long durationMs = endpointMs - positionMs;
      Log.i(TAG, "Called play() at " + positionMs + " ms, to play for " + durationMs + " ms.");
      if (durationMs <= 0) {
        Log.w(TAG, "Called play() at position after endpoint. No playback occurred.");
        return;
      }
      handler.removeCallbacks(pauseAtEndpointRunnable);
      player.play();
      handler.postDelayed(pauseAtEndpointRunnable, durationMs);
    }
  }

  /** Releases the underlying MediaPlayer. */
  public void release() {
    player.stop();
    //player.reset();
    player.release();
    /*
    * player.setOnErrorListener(null);
    * player.setOnCompletionListener(null);
    * player.setOnPreparedListener(null);
    * player.setOnSeekCompleteListener(null);
    * */
    handler.removeCallbacksAndMessages(null);
  }

  public void seek(double positionSeconds) {
    int positionMilliseconds = (int) (positionSeconds * 1000.0);
    player.seekTo(positionMilliseconds);
  }

  public void setVolume(double volume) {
    player.setVolume((float) volume);
  }

  public void pause() {
    player.pause();
  }

  @Override
  public void onPlaybackStateChanged(int playbackState) {
    if(playbackState == Player.STATE_ENDED){
      player.seekTo(0);
      parentAudioPlugin.handleCompletion(this.audioId);
    }
  }

  @Override
  public void onPlayerError(PlaybackException error) {
    Player.Listener.super.onPlayerError(error);
    Log.e(TAG, "onError:" + error.errorCode);
  }

  @Override
  public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
    Player.Listener.super.onPositionDiscontinuity(oldPosition, newPosition, reason);
    if(reason == Player.DISCONTINUITY_REASON_SEEK){
      if (onSeekCompleteListener != null) {
        onSeekCompleteListener.onSeekComplete();
      }
    }
  }

  /** Pauses the player and notifies of completion. */
  private static class PauseAtEndpointRunnable implements Runnable {

    final WeakReference<ManagedMediaPlayer> managedMediaPlayerRef;

    PauseAtEndpointRunnable(ManagedMediaPlayer managedMediaPlayer) {
      managedMediaPlayerRef = new WeakReference<>(managedMediaPlayer);
    }

    @Override
    public void run() {
      Log.d(TAG, "Running scheduled PauseAtEndpointRunnable");

      ManagedMediaPlayer managedMediaPlayer = managedMediaPlayerRef.get();
      if (managedMediaPlayer == null) {
        Log.w(TAG, "ManagedMediaPlayer no longer active.");
        return;
      }
      managedMediaPlayer.player.pause();
      managedMediaPlayer.parentAudioPlugin.handleCompletion(managedMediaPlayer.audioId);
    }
  }
}
