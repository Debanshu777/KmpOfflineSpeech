#pragma once
#include <stdint.h>
#include "sherpa-onnx/c-api/c-api.h"
#ifdef __cplusplus
extern "C" {
#endif
typedef void (*SpeechPermissionCallback)(int32_t granted, void *context);
void speech_request_permission(SpeechPermissionCallback callback, void *context);
void *speech_capture_create(void);
int32_t speech_capture_start(void *capture);
float *speech_capture_stop(void *capture, int32_t *count, int32_t *rate);
void speech_capture_destroy(void *capture);
void speech_free_samples(float *samples);
void *speech_player_start(const float *samples, int32_t count, int32_t rate);
int32_t speech_player_is_playing(void *player);
void speech_player_destroy(void *player);
void speech_audio_deactivate(void);
#ifdef __cplusplus
}
#endif
