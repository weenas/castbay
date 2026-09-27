#include "alac_decoder.h"

#include "file_log.h"

#include "ALACBitUtilities.h"
#include "ALACDecoder.h"

namespace castbay {
namespace {
constexpr uint32_t kFrameLength = 352;
constexpr uint32_t kChannels = 2;
/* Zeros after each frame: more than the decoder can read past the end (a word or two per
 * read, and every read first checks it hasn't passed the end). */
constexpr size_t kReadPadding = 256;

/*
 * ALACSpecificConfig (big-endian) for AirPlay's fixed ALAC format, the same magic cookie
 * UxPlay hands GStreamer: frameLength 352, version 0, bitDepth 16, pb 40, mb 10, kb 14,
 * 2 channels, maxRun 255, maxFrameBytes 0, avgBitRate 0, sampleRate 44100.
 */
uint8_t kMagicCookie[] = {
    0x00, 0x00, 0x01, 0x60, 0x00, 0x10, 0x28, 0x0a, 0x0e, 0x02, 0x00, 0xff,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xac, 0x44,
};
}  // namespace

AlacDecoder::AlacDecoder() : decoder_(new ALACDecoder()) {
    ready_ = decoder_->Init(kMagicCookie, sizeof(kMagicCookie)) == ALAC_noErr;
    if (!ready_) castbay_logf(ANDROID_LOG_ERROR, "CastBayAlac", "ALAC decoder init failed");
}

AlacDecoder::~AlacDecoder() = default;

bool AlacDecoder::decode(const uint8_t *frame, int length, std::vector<int16_t> &pcm) {
    if (!ready_ || !frame || length <= 0) return false;
    pcm.resize(kFrameLength * kChannels);
    // Apple's decoder reads a few bytes past the data it is given (dyn_get_32bit loads whole
    // words, and a corrupt frame ends mid-code). Straight from the network buffer that read
    // crashed the app when the frame ended at an unmapped page (SIGSEGV in dyn_decomp, seen
    // on a TCL TV with lossy Wi-Fi), so the decoder gets a copy followed by zeros.
    input_.assign(frame, frame + length);
    input_.resize(static_cast<size_t>(length) + kReadPadding, 0);
    BitBuffer bits;
    BitBufferInit(&bits, input_.data(), static_cast<uint32_t>(length));
    uint32_t samples = 0;
    int32_t status = decoder_->Decode(&bits, reinterpret_cast<uint8_t *>(pcm.data()), kFrameLength,
                                      kChannels, &samples);
    if (status != ALAC_noErr) return false;
    pcm.resize(samples * kChannels);
    return true;
}

}  // namespace castbay
