#import "ManagedPlayer.h"

#import <AVFoundation/AVFoundation.h>
@import MUXSDKStats;

NSTimeInterval const FLTManagedPlayerPlayToEnd = -1.0;

static NSString *const kKeyPathStatus = @"status";
static float const kTimerUpdateIntervalSeconds = 0.25;

@interface FLTManagedPlayer ()<AVAudioPlayerDelegate>
@end

@implementation FLTManagedPlayer {
  __weak id<FLTManagedPlayerDelegate> _delegate;
  AVAudioPlayer *globalAudioPlayer;
  NSTimer *_positionTimer;

  AVPlayer *globalAvPlayer;
  id _completionObserver;            // Registered on NSNotificationCenter.
  id _timeObserver;                  // Registered on the AVPlayer.
  void (^_remoteLoadHandler)(BOOL);  // Called on AVPlayer loading status change observed.
  NSTimer *_endpointTimer;
}

// Private common initializer. [audioPlayer] or [avPlayer], but not both, must be set.
- (instancetype)initWithAudioId:(NSString *)audioId
                    audioPlayer:(AVAudioPlayer *)audioPlayer
                       avPlayer:(AVPlayer *)avPlayer
                       delegate:(id<FLTManagedPlayerDelegate>)delegate
                      isLooping:(bool)isLooping
              remoteLoadHandler:(void (^)(BOOL))remoteLoadHandler {
  // Assert init with either AVAudioPlayer or AVPlayer.
  if ((audioPlayer == nil) == (avPlayer == nil)) {
    NSLog(@"Must initialize with either audioPlayer or avPlayer");
    return nil;
  }
  self = [super init];
  if (self) {
    _audioId = [audioId copy];
    _delegate = delegate;
    if (audioPlayer) {
      globalAudioPlayer = audioPlayer;
      globalAudioPlayer.delegate = self;
      globalAudioPlayer.numberOfLoops = isLooping ? -1 : 0;
      [globalAudioPlayer prepareToPlay];
      [_delegate managedPlayerDidLoadWithDuration:globalAudioPlayer.duration forAudioId:_audioId];
      __weak FLTManagedPlayer *weakSelf = self;
      _positionTimer = [NSTimer
          scheduledTimerWithTimeInterval:kTimerUpdateIntervalSeconds
                                 repeats:YES
                                   block:^(NSTimer *timer) {
                                     FLTManagedPlayer *strongSelf = weakSelf;
                                     if (strongSelf) {
                                       if (strongSelf->globalAudioPlayer.playing) {
                                         [strongSelf->_delegate
                                             managedPlayerDidUpdatePosition:globalAudioPlayer.currentTime
                                                                 forAudioId:strongSelf->_audioId];
                                       }
                                     }
                                   }];
    } else {
      globalAvPlayer = avPlayer;
      _remoteLoadHandler = remoteLoadHandler;
      CMTime interval = CMTimeMakeWithSeconds(kTimerUpdateIntervalSeconds, NSEC_PER_SEC);
      __weak FLTManagedPlayer *weakSelf = self;
      _timeObserver = [globalAvPlayer
          addPeriodicTimeObserverForInterval:interval
                                       queue:nil
                                  usingBlock:^(CMTime time) {
                                    FLTManagedPlayer *strongSelf = weakSelf;
                                    if (strongSelf) {
                                      NSTimeInterval position =
                                          (NSTimeInterval)CMTimeGetSeconds(time);
                                      [strongSelf->_delegate
                                          managedPlayerDidUpdatePosition:position
                                                              forAudioId:strongSelf->_audioId];
                                    }
                                  }];
      _completionObserver = [[NSNotificationCenter defaultCenter]
          addObserverForName:AVPlayerItemDidPlayToEndTimeNotification
                      object:globalAvPlayer.currentItem
                       queue:nil
                  usingBlock:^(NSNotification *notif) {
                    FLTManagedPlayer *strongSelf = weakSelf;
                    if (strongSelf) {
                      [strongSelf->globalAvPlayer seekToTime:kCMTimeZero];
                      [strongSelf->_delegate managedPlayerDidFinishPlaying:_audioId];
                    }
                  }];
      [globalAvPlayer.currentItem addObserver:self
                              forKeyPath:kKeyPathStatus
                                 options:NSKeyValueObservingOptionNew
                                 context:nil];
    }
  }
  return self;
}

- (instancetype)initWithAudioId:(NSString *)audioId
                           path:(NSString *)path
                       delegate:(id<FLTManagedPlayerDelegate>)delegate
                      isLooping:(bool)isLooping {
  AVAudioPlayer *audioPlayer =
      [[AVAudioPlayer alloc] initWithContentsOfURL:[NSURL fileURLWithPath:path] error:nil];
  return [self initWithAudioId:audioId
                   audioPlayer:audioPlayer
                      avPlayer:nil
                      delegate:delegate
                     isLooping:isLooping
             remoteLoadHandler:nil];
}

- (instancetype)initWithAudioId:(NSString *)audioId
                           data:(NSData *)data
                       delegate:(id<FLTManagedPlayerDelegate>)delegate
                      isLooping:(bool)isLooping {
  AVAudioPlayer *audioPlayer = [[AVAudioPlayer alloc] initWithData:data error:nil];
  return [self initWithAudioId:audioId
                   audioPlayer:audioPlayer
                      avPlayer:nil
                      delegate:delegate
                     isLooping:isLooping
             remoteLoadHandler:nil];
}

- (instancetype)initWithAudioId:(NSString *)audioId
                      remoteUrl:(NSString *)urlString
                       delegate:(id<FLTManagedPlayerDelegate>)delegate
                      isLooping:(bool)isLooping
              remoteLoadHandler:(void (^)(BOOL))remoteLoadHandler {
  AVPlayerItem *avPlayerItem = [[AVPlayerItem alloc] initWithURL:[NSURL URLWithString:urlString]];
  AVPlayer *avPlayer = [[AVPlayer alloc] initWithPlayerItem:avPlayerItem];
  return [self initWithAudioId:audioId
                   audioPlayer:nil
                      avPlayer:avPlayer
                      delegate:delegate
                     isLooping:isLooping
             remoteLoadHandler:remoteLoadHandler];
}

- (void)dealloc {
  [[NSNotificationCenter defaultCenter] removeObserver:_completionObserver];
  [globalAvPlayer.currentItem removeObserver:self forKeyPath:kKeyPathStatus];
  [globalAvPlayer removeTimeObserver:_timeObserver];
}

- (void)play:(bool)playFromStart endpoint:(NSTimeInterval)endpoint {
  // Maybe seek to start.
  if (globalAudioPlayer) {
    if (playFromStart) {
      globalAudioPlayer.currentTime = 0;
    }
  } else {
    if (playFromStart) {
      [globalAvPlayer seekToTime:kCMTimeZero];
    }
  }
  // Handle endpoint timers and start playback.
  if (endpoint == FLTManagedPlayerPlayToEnd) {
    // No endpoint, clear timer and start playback.
    [_endpointTimer invalidate];
    _endpointTimer = nil;
    if (globalAudioPlayer) {
      [globalAudioPlayer play];
    } else {
      [globalAvPlayer play];
    }
  } else {
    // If there is an endpoint, check that it is in the future, then start playback and schedule
    // the pausing after a duration.
    NSTimeInterval position;
    if (globalAudioPlayer) {
      position = globalAudioPlayer.currentTime;
    } else {
      position = (NSTimeInterval)CMTimeGetSeconds(globalAvPlayer.currentTime);
    }
    NSTimeInterval duration = endpoint - position;
    NSLog(@"Called play() at position %.2f seconds, to play for duration %.2f seconds.", position,
          duration);
    if (duration <= 0) {
      NSLog(@"Called play() at position after endpoint. No playback occurred.");
      return;
    }
    [_endpointTimer invalidate];
    if (globalAudioPlayer) {
      [globalAudioPlayer play];
    } else {
      [globalAvPlayer play];
    }
    __weak FLTManagedPlayer *weakSelf = self;
    _endpointTimer = [NSTimer
        scheduledTimerWithTimeInterval:duration
                               repeats:NO
                                 block:^(NSTimer *timer) {
                                   FLTManagedPlayer *strongSelf = weakSelf;
                                   if (strongSelf) {
                                     [strongSelf pause];
                                     [strongSelf->_delegate managedPlayerDidFinishPlaying:_audioId];
                                   }
                                 }];
  }
}

- (void)releasePlayer {
  if (globalAudioPlayer) {
    [globalAudioPlayer stop];  // Undoes the resource aquisition in [prepareToPlay].
    [_positionTimer invalidate];
    _positionTimer = nil;
  } else {
    [globalAvPlayer pause];
    [globalAvPlayer.currentItem removeObserver:self forKeyPath:kKeyPathStatus];
    [globalAvPlayer removeTimeObserver:_timeObserver];
    globalAvPlayer = nil;
  }
}

- (void)seek:(NSTimeInterval)position completionHandler:(void (^)())completionHandler {
  if (globalAudioPlayer) {
    globalAudioPlayer.currentTime = position;
    completionHandler();
  } else {
    [globalAvPlayer seekToTime:CMTimeMakeWithSeconds(position, NSEC_PER_SEC)
        completionHandler:^(BOOL completed) {
          completionHandler();
        }];
  }
}

- (void)setVolume:(double)volume {
  if (globalAudioPlayer) {
    globalAudioPlayer.volume = volume;
  } else {
    globalAvPlayer.volume = volume;
  }
}

- (void)pause {
  if (globalAudioPlayer) {
    [globalAudioPlayer pause];
  } else {
    [globalAvPlayer pause];
  }
}

- (void)setupMux:(NSDictionary *)muxConfig{
    AVPlayerViewController* playerViewController = [AVPlayerViewController new];
    playerViewController.player = globalAvPlayer;

    // Environment and player data that persists until the player is destroyed
      MUXSDKCustomerPlayerData* playerData = [[MUXSDKCustomerPlayerData alloc] initWithEnvironmentKey:muxConfig[@"envKey"]];
      playerData.playerName = muxConfig[@"playerName"];
      playerData.viewerUserId = muxConfig[@"viewerUserId"];
      playerData.experimentName = muxConfig[@"experimentName"];
      playerData.playerVersion = muxConfig[@"playerVersion"];
      playerData.pageType = muxConfig[@"pageType"];
      playerData.subPropertyId = muxConfig[@"subPropertyId"];
      playerData.playerInitTime = muxConfig[@"playerInitTime"];

      // Video metadata (cleared with videoChangeForPlayer:withVideoData:)
      MUXSDKCustomerVideoData* videoData = [MUXSDKCustomerVideoData new];
      videoData.videoId = muxConfig[@"videoId"];
      videoData.videoTitle = muxConfig[@"videoTitle"];
      videoData.videoSeries = muxConfig[@"videoSeries"];
      videoData.videoVariantName = muxConfig[@"videoVariantName"];
      videoData.videoVariantId = muxConfig[@"videoVariantId"];
      videoData.videoLanguageCode = muxConfig[@"videoLanguageCode"];
      videoData.videoContentType = muxConfig[@"videoContentType"];
      videoData.videoStreamType = muxConfig[@"videoStreamType"];
      videoData.videoProducer = muxConfig[@"videoProducer"];
      videoData.videoEncodingVariant = muxConfig[@"videoEncodingVariant"];
      videoData.videoCdn = muxConfig[@"videoCdn"];
      videoData.videoDuration = muxConfig[@"videoDuration"];
      
      MUXSDKCustomData *customData = [[MUXSDKCustomData alloc] init];
      [customData setCustomData1:muxConfig[@"customData1"]];
      [customData setCustomData2:muxConfig[@"customData2"]];

      MUXSDKCustomerData *customerData = [[MUXSDKCustomerData alloc] initWithCustomerPlayerData:playerData videoData:videoData viewData:nil customData:customData viewerData:nil];
          
      [MUXSDKStats monitorAVPlayerViewController:playerViewController
                                  withPlayerName:muxConfig[@"playerName"]
                                      customerData:customerData];
}


#pragma mark - KVO

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary *)change
                       context:(void *)context {
  if ([keyPath isEqualToString:kKeyPathStatus] && [object isKindOfClass:[AVPlayerItem class]]) {
    AVPlayerItem *item = (AVPlayerItem *)object;
    AVPlayerItemStatus status = [change[NSKeyValueChangeNewKey] integerValue];
    switch (status) {
      case AVPlayerItemStatusReadyToPlay: {
        NSTimeInterval duration = (NSTimeInterval)CMTimeGetSeconds(item.duration);
        _remoteLoadHandler(YES);
        [_delegate managedPlayerDidLoadWithDuration:duration forAudioId:_audioId];
        break;
      }
      case AVPlayerItemStatusFailed: {
        if (item.error.code == -11800) {
          NSLog(
              @"It looks like you are failing to load a remote asset. You probably requested a "
              @"non-https url, and didn't specify arbitrary url loading in your app transport "
              @"securty settings. Add an NSAppTransportSecurity entry to your Info.plist.");
        }
        _remoteLoadHandler(NO);
        break;
      }
      case AVPlayerItemStatusUnknown:
        _remoteLoadHandler(NO);
        break;
    }
  } else {
    [super observeValueForKeyPath:keyPath ofObject:object change:change context:context];
  }
}

#pragma mark - AVAudioPlayerDelegate

- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)audioPlayer successfully:(BOOL)flag {
  globalAudioPlayer.currentTime = 0;
  [_delegate managedPlayerDidFinishPlaying:_audioId];
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)audioPlayer error:(NSError *)error {
}

@end
