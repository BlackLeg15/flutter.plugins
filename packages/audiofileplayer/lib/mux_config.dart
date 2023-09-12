// ignore_for_file: public_member_api_docs, sort_constructors_first
import 'dart:convert';

import 'package:meta/meta.dart';

/// Configuration parameters to send to Mux.
@immutable
class MuxConfig {
  /// constructor
  const MuxConfig({
    required this.envKey,
    required this.playerName,
    this.viewerUserId,
    this.pageType,
    this.experimentName,
    this.subPropertyId,
    this.playerVersion,
    this.playerInitTime,
    this.videoId,
    this.videoTitle,
    this.videoSeries,
    this.videoVariantName,
    this.videoVariantId,
    this.videoLanguageCode,
    this.videoContentType,
    this.videoDuration,
    this.videoStreamType,
    this.videoProducer,
    this.videoEncodingVariant,
    this.videoCdn,
    this.customData1,
    this.customData2,
  });

  /// Your env key from the Mux dashboard. Note this was previously named property_key
  final String envKey;

  /// If you have different configurations or types of players around your
  /// site or application you can use player_name to compare them. This is not the
  /// player software (e.g. Video.js), which is tracked automatically by the SDK.
  final String playerName;

  /// An ID representing the viewer who is watching the stream.
  /// Use this to look up video views for an individual viewer.
  final String? viewerUserId;

  /// Provide the context of the page for more specific analysis.
  /// Values include `watchpage`, `iframe`, or leave empty.
  final MuxPageType? pageType;

  /// You can use this field to separate views into different experiments,
  /// if you would like to filter by this dimension later. This should be a string value,
  /// but your account is limited to a total of 10 unique experiment names,
  /// so be sure that this value is not generated dynamically or randomly.
  final String? experimentName;

  /// A sub property is an optional way to group data within a property.
  /// For example, sub properties may be used by a video platform to group data by its own
  /// customers, or a media company might use them to distinguish between its many websites.
  final String? subPropertyId;

  /// As you make changes to your player you can compare how new versions of your player
  /// perform by updating this value. This is not the player software version
  /// (e.g. Video.js 5.0.0), which is tracked automatically by the SDK.
  final String? playerVersion;

  /// If you are explicitly loading your player in page (perhaps as a response to a user interaction),
  /// include the timestamp (milliseconds since Jan 1 1970) when you initialize the player
  /// in order to accurately track page load time and player startup time.
  final DateTime? playerInitTime;

  /// Your internal ID for the video
  final String? videoId;

  /// example: 'Awesome Show: Pilot'
  final String? videoTitle;

  /// example: 'Season 1'
  final String? videoSeries;

  /// An optional detail that allows you to monitor issues with the files of specific versions of the content,
  /// for example different audio translations or versions with hard-coded/burned-in subtitles.
  final String? videoVariantName;

  /// Your internal ID for a video variant
  final String? videoVariantId;

  /// The audio language of the video, assuming it's unchangeable after playing.
  final String? videoLanguageCode;

  /// 'short', 'movie', 'episode', 'clip', 'trailer', or 'event'
  final String? videoContentType;

  /// The length of the video in milliseconds
  final Duration? videoDuration;

  /// 'live' or 'onDemand'
  final MuxVideoStreamType? videoStreamType;

  /// The producer of the video title
  final String? videoProducer;

  /// An optional detail that allows you to compare different encoding settings.
  final String? videoEncodingVariant;

  /// An optional detail that allows you to compare different CDNs (assuming the CDN selection is made at page load time).
  final String? videoCdn;
  
  /// An optional data strongly related to the customers of Mux. #1 Custom Data.
  final String? customData1;

  /// An optional data strongly related to the customers of Mux. #1 Custom Data.
  final String? customData2;

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'envKey': envKey,
      'playerName': playerName,
      'viewerUserId': viewerUserId,
      'pageType': pageType?.name,
      'experimentName': experimentName,
      'subPropertyId': subPropertyId,
      'playerVersion': playerVersion,
      'playerInitTime': playerInitTime?.millisecondsSinceEpoch,
      'videoId': videoId,
      'videoTitle': videoTitle,
      'videoSeries': videoSeries,
      'videoVariantName': videoVariantName,
      'videoVariantId': videoVariantId,
      'videoLanguageCode': videoLanguageCode,
      'videoContentType': videoContentType,
      'videoDuration': videoDuration?.inMilliseconds,
      'videoStreamType': videoStreamType?.name,
      'videoProducer': videoProducer,
      'videoEncodingVariant': videoEncodingVariant,
      'videoCdn': videoCdn,
      'customData1': customData1,
      'customData2': customData2,
    };
  }

  factory MuxConfig.fromMap(Map<String, dynamic> map) {
    return MuxConfig(
      envKey: map['envKey'] as String,
      playerName: map['playerName'] as String,
      viewerUserId: map['viewerUserId'] != null ? map['viewerUserId'] as String : null,
      pageType: map['pageType'] != null ? MuxPageType.values.firstWhere((element) => element.name == map['pageType']): null,
      experimentName: map['experimentName'] != null ? map['experimentName'] as String : null,
      subPropertyId: map['subPropertyId'] != null ? map['subPropertyId'] as String : null,
      playerVersion: map['playerVersion'] != null ? map['playerVersion'] as String : null,
      playerInitTime: map['playerInitTime'] != null ? DateTime.fromMillisecondsSinceEpoch(map['playerInitTime'] as int) : null,
      videoId: map['videoId'] != null ? map['videoId'] as String : null,
      videoTitle: map['videoTitle'] != null ? map['videoTitle'] as String : null,
      videoSeries: map['videoSeries'] != null ? map['videoSeries'] as String : null,
      videoVariantName: map['videoVariantName'] != null ? map['videoVariantName'] as String : null,
      videoVariantId: map['videoVariantId'] != null ? map['videoVariantId'] as String : null,
      videoLanguageCode: map['videoLanguageCode'] != null ? map['videoLanguageCode'] as String : null,
      videoContentType: map['videoContentType'] != null ? map['videoContentType'] as String : null,
      videoDuration: map['videoDuration'] != null ? Duration(milliseconds: map['videoDuration']) : null,
      videoStreamType: map['videoStreamType'] != null ? MuxVideoStreamType.values.firstWhere(map['videoStreamType']) : null,
      videoProducer: map['videoProducer'] != null ? map['videoProducer'] as String : null,
      videoEncodingVariant: map['videoEncodingVariant'] != null ? map['videoEncodingVariant'] as String : null,
      videoCdn: map['videoCdn'] != null ? map['videoCdn'] as String : null,
      customData1: map['customData1'] != null ? map['customData1'] as String : null,
      customData2: map['customData2'] != null ? map['customData2'] as String : null,
    );
  }

  String toJson() => json.encode(toMap());

  factory MuxConfig.fromJson(String source) => MuxConfig.fromMap(json.decode(source) as Map<String, dynamic>);
}

/// Type of stream to Mux Analytics
enum MuxVideoStreamType {
  /// It's a live stream
  livestream,

  /// It's an on-demand video
  onDemand,
}

/// Type of page to Mux Analytics
enum MuxPageType {
  /// A web page that is dedicated to playing a specific video (for example youtube.com/watch/ID or hulu.com/watch/ID)
  watchpage,

  /// An iframe specifically used to embed a player on different sites/pages
  iframe,
}