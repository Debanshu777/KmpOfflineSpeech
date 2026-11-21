#include "sherpa-onnx/c-api/c-api.h"
#include <algorithm>
#include <cctype>
#include <iomanip>
#include <iostream>
#include <string>

int main(int argc, char **argv) {
  const std::string root = argc > 1 ? argv[1] : "models";
  const std::string kitten = root + "/kitten/", whisper = root + "/whisper/";
  const std::string model = kitten + "model.int8.onnx", voices = kitten + "voices.bin";
  const std::string tokens = kitten + "tokens.txt", lexicon = kitten + "lexicon-en-us.txt";
  SherpaOnnxOfflineTtsConfig tts_config{};
  tts_config.model.kitten.model = model.c_str();
  tts_config.model.kitten.voices = voices.c_str();
  tts_config.model.kitten.tokens = tokens.c_str();
  tts_config.model.kitten.lexicon = lexicon.c_str();
  tts_config.model.kitten.length_scale = 1.0f;
  tts_config.model.num_threads = 2;
  tts_config.model.provider = "cpu";
  tts_config.max_num_sentences = 1;
  tts_config.silence_scale = 0.2f;
  const auto *tts = SherpaOnnxCreateOfflineTts(&tts_config);
  if (!tts) { std::cerr << "Kitten load failed\n"; return 3; }
  SherpaOnnxGenerationConfig generation{};
  generation.sid = 0;
  generation.speed = 1.0f;
  generation.silence_scale = 0.2f;
  const char *text = "The quick brown fox jumps over the lazy dog.";
  const auto *audio = SherpaOnnxOfflineTtsGenerateWithConfig(tts, text, &generation, nullptr, nullptr);
  if (!audio || audio->n <= 0 || !audio->samples) {
    std::cerr << "Kitten generation failed\n";
    if (audio) SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
    SherpaOnnxDestroyOfflineTts(tts); return 4;
  }
  const std::string encoder = whisper + "tiny-encoder.int8.onnx";
  const std::string decoder = whisper + "tiny-decoder.int8.onnx";
  const std::string asr_tokens = whisper + "tiny-tokens.txt";
  SherpaOnnxOfflineRecognizerConfig config{};
  config.feat_config.sample_rate = 16000;
  config.feat_config.feature_dim = 80;
  config.model_config.whisper.encoder = encoder.c_str();
  config.model_config.whisper.decoder = decoder.c_str();
  config.model_config.whisper.language = "en";
  config.model_config.whisper.task = "transcribe";
  config.model_config.tokens = asr_tokens.c_str();
  config.model_config.num_threads = 2;
  config.model_config.provider = "cpu";
  config.decoding_method = "greedy_search";
  const auto *recognizer = SherpaOnnxCreateOfflineRecognizer(&config);
  const auto *stream = recognizer ? SherpaOnnxCreateOfflineStream(recognizer) : nullptr;
  if (!stream) {
    std::cerr << "Whisper load failed\n";
    if (recognizer) SherpaOnnxDestroyOfflineRecognizer(recognizer);
    SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio); SherpaOnnxDestroyOfflineTts(tts); return 5;
  }
  SherpaOnnxAcceptWaveformOffline(stream, audio->sample_rate, audio->samples, audio->n);
  SherpaOnnxDecodeOfflineStream(recognizer, stream);
  const auto *result = SherpaOnnxGetOfflineStreamResult(stream);
  std::string transcript = result && result->text ? result->text : "";
  std::string lower = transcript;
  std::transform(lower.begin(), lower.end(), lower.begin(), [](unsigned char c) { return std::tolower(c); });
  const bool passed = lower.find("quick") != std::string::npos && lower.find("brown") != std::string::npos && lower.find("fox") != std::string::npos;
  std::cout << "{\"passed\":" << (passed ? "true" : "false") << ",\"input\":" << std::quoted(text)
            << ",\"transcript\":" << std::quoted(transcript) << ",\"sample_rate\":" << audio->sample_rate
            << ",\"samples\":" << audio->n << ",\"scope\":\"iOS simulator C API TTS-to-STT; no microphone or speaker\"}\n";
  if (result) SherpaOnnxDestroyOfflineRecognizerResult(result);
  SherpaOnnxDestroyOfflineStream(stream); SherpaOnnxDestroyOfflineRecognizer(recognizer);
  SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio); SherpaOnnxDestroyOfflineTts(tts);
  return passed ? 0 : 6;
}
