#import <AVFoundation/AVFoundation.h>
#include "speech_audio.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <vector>

namespace {
struct Capture {
  AVAudioEngine *engine = nil;
  std::mutex mutex;
  std::vector<float> samples;
  double rate = 0;
  bool running = false;
};
struct Player { AVAudioPlayer *audio = nil; };
void put16(uint8_t *p, uint16_t n) { p[0] = n; p[1] = n >> 8; }
void put32(uint8_t *p, uint32_t n) { for (int i = 0; i < 4; ++i) p[i] = n >> (8 * i); }
}

extern "C" {
void speech_request_permission(SpeechPermissionCallback callback, void *context) {
  [AVAudioApplication requestRecordPermissionWithCompletionHandler:^(BOOL granted) {
    callback(granted ? 1 : 0, context);
  }];
}

void *speech_capture_create(void) { return new Capture(); }

int32_t speech_capture_start(void *opaque) {
  auto *capture = static_cast<Capture *>(opaque);
  if (!capture || capture->running) return 0;
  AVAudioSession *session = AVAudioSession.sharedInstance;
  NSError *error = nil;
  if (![session setCategory:AVAudioSessionCategoryRecord mode:AVAudioSessionModeMeasurement
                    options:0 error:&error] || ![session setActive:YES error:&error]) return 0;
  AVAudioEngine *engine = [[AVAudioEngine alloc] init];
  AVAudioInputNode *input = engine.inputNode;
  AVAudioFormat *format = [input outputFormatForBus:0];
  if (format.sampleRate <= 0 || format.channelCount == 0 ||
      format.commonFormat != AVAudioPCMFormatFloat32 || format.isInterleaved) return 0;
  capture->rate = format.sampleRate;
  capture->samples.clear();
  // Hard bound in native capture too: a delayed UI timer cannot grow audio indefinitely.
  capture->samples.reserve(static_cast<size_t>(capture->rate * 20));
  capture->engine = engine;
  [input installTapOnBus:0 bufferSize:2048 format:format block:^(AVAudioPCMBuffer *buffer, AVAudioTime *) {
    if (!buffer.floatChannelData || !buffer.frameLength) return;
    std::lock_guard<std::mutex> lock(capture->mutex);
    const size_t maximum = static_cast<size_t>(capture->rate * 20);
    const size_t count = std::min<size_t>(buffer.frameLength, maximum - capture->samples.size());
    for (size_t i = 0; i < count; ++i) {
      float sample = 0;
      for (uint32_t channel = 0; channel < buffer.format.channelCount; ++channel)
        sample += buffer.floatChannelData[channel][i];
      capture->samples.push_back(std::clamp(sample / buffer.format.channelCount, -1.0f, 1.0f));
    }
  }];
  [engine prepare];
  if (![engine startAndReturnError:&error]) {
    [input removeTapOnBus:0];
    capture->engine = nil;
    return 0;
  }
  capture->running = true;
  return 1;
}

float *speech_capture_stop(void *opaque, int32_t *count, int32_t *rate) {
  auto *capture = static_cast<Capture *>(opaque);
  *count = 0;
  *rate = 16000;
  if (!capture || !capture->running) return nullptr;
  [capture->engine stop];
  [capture->engine.inputNode removeTapOnBus:0];
  capture->engine = nil;
  capture->running = false;
  std::lock_guard<std::mutex> lock(capture->mutex);
  *rate = static_cast<int32_t>(std::lround(capture->rate));
  if (capture->samples.empty()) return nullptr;
  auto *copy = static_cast<float *>(std::malloc(capture->samples.size() * sizeof(float)));
  if (!copy) return nullptr;
  std::memcpy(copy, capture->samples.data(), capture->samples.size() * sizeof(float));
  *count = static_cast<int32_t>(capture->samples.size());
  capture->samples.clear();
  return copy;
}

void speech_capture_destroy(void *opaque) {
  auto *capture = static_cast<Capture *>(opaque);
  if (!capture) return;
  int32_t count, rate;
  std::free(speech_capture_stop(capture, &count, &rate));
  delete capture;
}
void speech_free_samples(float *samples) { std::free(samples); }

void *speech_player_start(const float *samples, int32_t count, int32_t rate) {
  if (!samples || count <= 0 || rate <= 0 || count > INT32_MAX / 2 - 44) return nullptr;
  const uint32_t bytes = count * 2;
  std::vector<uint8_t> wave(44 + bytes, 0);
  auto *p = wave.data();
  std::memcpy(p, "RIFF", 4); put32(p + 4, 36 + bytes);
  std::memcpy(p + 8, "WAVEfmt ", 8); put32(p + 16, 16);
  put16(p + 20, 1); put16(p + 22, 1); put32(p + 24, rate);
  put32(p + 28, rate * 2); put16(p + 32, 2); put16(p + 34, 16);
  std::memcpy(p + 36, "data", 4); put32(p + 40, bytes);
  for (int32_t i = 0; i < count; ++i)
    put16(p + 44 + i * 2, static_cast<int16_t>(std::lround(std::clamp(samples[i], -1.0f, 1.0f) * 32767)));
  NSError *error = nil;
  AVAudioSession *session = AVAudioSession.sharedInstance;
  if (![session setCategory:AVAudioSessionCategoryPlayback mode:AVAudioSessionModeSpokenAudio
                    options:0 error:&error] || ![session setActive:YES error:&error]) return nullptr;
  AVAudioPlayer *audio = [[AVAudioPlayer alloc]
      initWithData:[NSData dataWithBytes:p length:wave.size()] error:&error];
  if (!audio || ![audio prepareToPlay] || ![audio play]) return nullptr;
  auto *player = new Player();
  player->audio = audio;
  return player;
}
int32_t speech_player_is_playing(void *opaque) {
  auto *player = static_cast<Player *>(opaque);
  return player && player->audio.isPlaying;
}
void speech_player_destroy(void *opaque) {
  auto *player = static_cast<Player *>(opaque);
  if (!player) return;
  [player->audio stop];
  delete player;
}
void speech_audio_deactivate(void) {
  [AVAudioSession.sharedInstance setActive:NO
      withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
}
}
