#pragma once

#include <cstdint>
#include <memory>
#include <vector>

class ALACDecoder;

namespace castbay {

/**
 * Decodes the ALAC frames AirPlay audio streaming sends (compression type 2):
 * 44.1 kHz, 16-bit, stereo, 352 samples per frame. Not thread-safe.
 */
class AlacDecoder {
public:
    AlacDecoder();
    ~AlacDecoder();

    /** Decodes one frame into interleaved S16 PCM; returns false on a corrupt frame. */
    bool decode(const uint8_t *frame, int length, std::vector<int16_t> &pcm);

private:
    std::unique_ptr<ALACDecoder> decoder_;
    bool ready_ = false;
    /** The frame being decoded, followed by zeros: see decode(). */
    std::vector<uint8_t> input_;
};

}  // namespace castbay
