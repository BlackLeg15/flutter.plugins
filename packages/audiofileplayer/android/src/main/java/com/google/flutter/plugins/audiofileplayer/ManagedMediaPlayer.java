package com.google.flutter.plugins.audiofileplayer;

import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;

import com.mux.stats.sdk.core.model.CustomData;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;

import java.lang.ref.WeakReference;
import java.util.Map;

/** Base class for wrapping a MediaPlayer for use by AudiofileplayerPlugin. */
abstract class ManagedMediaPlayer
    implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener {
  private static final String TAG = ManagedMediaPlayer.class.getSimpleName();
  public static final int PLAY_TO_END = -1;

  interface OnSeekCompleteListener {
    /** Called when asynchronous seeking has completed. */
    void onSeekComplete();
  }

  protected final AudiofileplayerPlugin parentAudioPlugin;
  protected final String audioId;
  protected final boolean playInBackground;
  protected final MediaPlayer player;
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
      boolean playInBackground) {
    this.parentAudioPlugin = parentAudioPlugin;
    this.audioId = audioId;
    this.playInBackground = playInBackground;
    player = new MediaPlayer();
    player.setLooping(looping);

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
      player.start();
    } else {
      // If there is an endpoint, check that it is in the future, then start playback and schedule
      // the pausing after a duration.
      int positionMs = player.getCurrentPosition();
      int durationMs = endpointMs - positionMs;
      Log.i(TAG, "Called play() at " + positionMs + " ms, to play for " + durationMs + " ms.");
      if (durationMs <= 0) {
        Log.w(TAG, "Called play() at position after endpoint. No playback occurred.");
        return;
      }
      handler.removeCallbacks(pauseAtEndpointRunnable);
      player.start();
      handler.postDelayed(pauseAtEndpointRunnable, durationMs);
    }
  }

  /**
   * Plays the audio.
   *
   * @param args the time, in milleseconds, to play to. To play until the end, pass {@link
   *     #PLAY_TO_END}.
   */
  public void setupMux(Map<String, Object> args) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    CustomerPlayerData playerData = new CustomerPlayerData();
    CustomerVideoData videoData = new CustomerVideoData();
    CustomData customData = new CustomData();

    CustomerData customerData = new CustomerData();

    playerData.setEnvironmentKey(arg.getEnvKey());
    playerData.setPlayerName(arg.getPlayerName());
    videoData.setVideoSourceUrl(videoSource);

    if (arg.getViewerUserId() != null)
      playerData.setViewerUserId(arg.getViewerUserId());

    if (arg.getExperimentName() != null)
      playerData.setExperimentName(arg.getExperimentName());

    if (arg.getPlayerVersion() != null)
      playerData.setPlayerVersion(arg.getPlayerVersion());

    if (arg.getPageType() != null)
      playerData.setPageType(arg.getPageType());

    if (arg.getSubPropertyId() != null)
      playerData.setSubPropertyId(arg.getSubPropertyId());

    if (arg.getPlayerInitTime() != null)
      playerData.setPlayerInitTime(arg.getPlayerInitTime());

    if (arg.getVideoId() != null)
      videoData.setVideoId(arg.getVideoId());

    if (arg.getVideoTitle() != null)
      videoData.setVideoTitle(arg.getVideoTitle());

    if (arg.getVideoSeries() != null)
      videoData.setVideoSeries(arg.getVideoSeries());

    if (arg.getVideoVariantName() != null)
      videoData.setVideoVariantName(arg.getVideoVariantName());

    if (arg.getVideoVariantId() != null)
      videoData.setVideoVariantId(arg.getVideoVariantId());

    if (arg.getVideoLanguageCode() != null)
      videoData.setVideoLanguageCode(arg.getVideoLanguageCode());

    if (arg.getVideoContentType() != null)
      videoData.setVideoContentType(arg.getVideoContentType());

    if (arg.getVideoStreamType() != null)
      videoData.setVideoStreamType(arg.getVideoStreamType());

    if (arg.getVideoProducer() != null)
      videoData.setVideoProducer(arg.getVideoProducer());

    if (arg.getVideoEncodingVariant() != null)
      videoData.setVideoEncodingVariant(arg.getVideoEncodingVariant());

    if (arg.getVideoCdn() != null)
      videoData.setVideoCdn(arg.getVideoCdn());

    if (arg.getVideoDuration() != null) {
      videoData.setVideoDuration(castVideoDuration(arg.getVideoDuration()));
    }

    if (arg.getCustomData1() != null)
      customData.setCustomData1(arg.getCustomData1());

    if (arg.getCustomData2() != null)
      customData.setCustomData2(arg.getCustomData2());

    customerData.setCustomerVideoData(videoData);
    customerData.setCustomerPlayerData(playerData);
    customerData.setCustomData(customData);

    muxStatsExoPlayer = new MuxStatsExoPlayer(flutterState.applicationContext, player.exoPlayer,
            arg.getEnvKey(), customerData);
  }

  /** Releases the underlying MediaPlayer. */
  public void release() {
    player.stop();
    player.reset();
    player.release();
    player.setOnErrorListener(null);
    player.setOnCompletionListener(null);
    player.setOnPreparedListener(null);
    player.setOnSeekCompleteListener(null);
    handler.removeCallbacksAndMessages(null);
  }

  public void seek(double positionSeconds) {
    int positionMilliseconds = (int) (positionSeconds * 1000.0);
    player.seekTo(positionMilliseconds);
  }

  public void setVolume(double volume) {
    player.setVolume((float) volume, (float) volume);
  }

  public void pause() {
    player.pause();
  }

  @Override
  public void onCompletion(MediaPlayer mediaPlayer) {
    player.seekTo(0);
    parentAudioPlugin.handleCompletion(this.audioId);
  }

  /**
   * Callback to indicate an error condition.
   *
   * <p>NOTE: {@link #onError(MediaPlayer, int, int)} must be properly implemented and return {@code
   * true} otherwise errors will repeatedly call {@link #onCompletion(MediaPlayer)}.
   */
  @Override
  public boolean onError(MediaPlayer mp, int what, int extra) {
    Log.e(TAG, "onError: what:" + what + " extra: " + extra);
    return true;
  }

  @Override
  public void onSeekComplete(MediaPlayer mp) {
    if (onSeekCompleteListener != null) {
      onSeekCompleteListener.onSeekComplete();
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
