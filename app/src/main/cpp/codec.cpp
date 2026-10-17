// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

// Sticker codec for WhatsApp: GIF, WebP or raw pixels to a 512×512 WebP that fits within the limit.
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <functional>
#include <memory>
#include <stdexcept>
#include <vector>

#include "gif_lib.h"
#include "webp/demux.h"
#include "webp/encode.h"
#include "webp/mux.h"

namespace {

constexpr int kCanvas = 512;
constexpr int kMaxDurationMs = 10000;
constexpr int kMinFrameMs = 8;
// Like Fresco, which TikTok displays them with: under 11 ms plays as 100; MediaProbe applies the same rule.
constexpr int kShortFrameMs = 11;
constexpr int kShortFrameShownMs = 100;

int shownDelay(int ms) { return ms < kShortFrameMs ? kShortFrameShownMs : ms; }
constexpr int kMaxKeptFrames = 80;
constexpr int kAttempts = 6;
constexpr int kMaxSide = 4096;
constexpr int64_t kMaxPixels = 4 * 1024 * 1024;
constexpr size_t kMaxInput = 16 * 1024 * 1024;

void require(bool ok, const char* why) {
    if (!ok) throw std::runtime_error(why);
}

void checkBounds(int w, int h) {
    require(w > 0 && h > 0 && w <= kMaxSide && h <= kMaxSide && int64_t(w) * h <= kMaxPixels, "image too large");
}

// Non-premultiplied RGBA of the full canvas; returning false stops reading.
using FrameFn = std::function<bool(const uint8_t* rgba, int w, int h, int delayMs)>;
using Source = std::function<void(const FrameFn&)>;

struct AnimDecoderFree { void operator()(WebPAnimDecoder* d) const { WebPAnimDecoderDelete(d); } };
struct AnimEncoderFree { void operator()(WebPAnimEncoder* e) const { WebPAnimEncoderDelete(e); } };
struct GifClose { void operator()(GifFileType* g) const { int err = 0; DGifCloseFile(g, &err); } };
struct WebPMemoryFree { void operator()(uint8_t* p) const { WebPFree(p); } };
struct MuxFree { void operator()(WebPMux* m) const { WebPMuxDelete(m); } };

struct Picture {
    WebPPicture pic{};
    Picture() { require(WebPPictureInit(&pic), "codec init"); }
    ~Picture() { WebPPictureFree(&pic); }
};

struct Data {
    WebPData data{};
    Data() { WebPDataInit(&data); }
    ~Data() { WebPDataClear(&data); }
};

// Premultiplied alpha so edges do not darken; box averaging when shrinking a lot, to avoid jaggies.
void fitInto(const uint8_t* src, int w, int h, int extent, std::vector<uint8_t>& out) {
    out.assign(size_t(kCanvas) * kCanvas * 4, 0);
    const double scale = std::min(double(extent) / w, double(extent) / h);
    const int nw = std::max(1, int(std::lround(w * scale)));
    const int nh = std::max(1, int(std::lround(h * scale)));
    const int ox = (kCanvas - nw) / 2, oy = (kCanvas - nh) / 2;
    const double sxStep = double(w) / nw, syStep = double(h) / nh;
    const bool box = sxStep > 2.0 || syStep > 2.0;
    for (int y = 0; y < nh; y++) {
        for (int x = 0; x < nw; x++) {
            double a = 0, rgb[3] = {0, 0, 0};
            if (box) {
                const int x0 = int(x * sxStep), x1 = std::min(w, std::max(x0 + 1, int((x + 1) * sxStep)));
                const int y0 = int(y * syStep), y1 = std::min(h, std::max(y0 + 1, int((y + 1) * syStep)));
                double count = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        const uint8_t* p = src + (size_t(yy) * w + xx) * 4;
                        a += p[3];
                        for (int c = 0; c < 3; c++) rgb[c] += double(p[3]) * p[c];
                        count += 1;
                    }
                }
                a /= count;
                for (double& c : rgb) c /= count;
            } else {
                const double sx = std::clamp((x + .5) * sxStep - .5, 0., double(w - 1));
                const double sy = std::clamp((y + .5) * syStep - .5, 0., double(h - 1));
                const int x0 = int(sx), y0 = int(sy), x1 = std::min(x0 + 1, w - 1), y1 = std::min(y0 + 1, h - 1);
                const double fx = sx - x0, fy = sy - y0;
                const double weights[4] = {(1 - fx) * (1 - fy), fx * (1 - fy), (1 - fx) * fy, fx * fy};
                const size_t at[4] = {(size_t(y0) * w + x0) * 4, (size_t(y0) * w + x1) * 4,
                                      (size_t(y1) * w + x0) * 4, (size_t(y1) * w + x1) * 4};
                for (int i = 0; i < 4; i++) {
                    const double wa = weights[i] * src[at[i] + 3];
                    a += wa;
                    for (int c = 0; c < 3; c++) rgb[c] += wa * src[at[i] + c];
                }
            }
            uint8_t* d = &out[(size_t(y + oy) * kCanvas + x + ox) * 4];
            d[3] = uint8_t(std::lround(std::clamp(a, 0., 255.)));
            for (int c = 0; c < 3; c++) d[c] = a > 0 ? uint8_t(std::clamp(std::round(rgb[c] / a), 0., 255.)) : 0;
        }
    }
}

void webpFrames(const uint8_t* data, size_t size, const FrameFn& fn) {
    const WebPData input = {data, size};
    WebPAnimDecoderOptions options;
    require(WebPAnimDecoderOptionsInit(&options), "codec init");
    options.color_mode = MODE_RGBA;
    options.use_threads = 1;
    std::unique_ptr<WebPAnimDecoder, AnimDecoderFree> decoder(WebPAnimDecoderNew(&input, &options));
    require(decoder != nullptr, "invalid webp");
    WebPAnimInfo info;
    require(WebPAnimDecoderGetInfo(decoder.get(), &info), "invalid webp");
    const int w = int(info.canvas_width), h = int(info.canvas_height);
    checkBounds(w, h);
    // The decoder gives the timestamp of the end of each frame.
    int previous = 0;
    while (WebPAnimDecoderHasMoreFrames(decoder.get())) {
        uint8_t* rgba = nullptr;
        int timestamp = 0;
        require(WebPAnimDecoderGetNext(decoder.get(), &rgba, &timestamp), "invalid webp frame");
        const int delay = shownDelay(timestamp - previous);
        previous = timestamp;
        if (!fn(rgba, w, h, delay)) break;
    }
}

struct GifInput {
    const uint8_t* data;
    size_t size;
    size_t pos = 0;
};

int gifRead(GifFileType* gif, GifByteType* dest, int n) {
    auto* in = static_cast<GifInput*>(gif->UserData);
    const size_t count = std::min(size_t(std::max(n, 0)), in->size - in->pos);
    memcpy(dest, in->data + in->pos, count);
    in->pos += count;
    return int(count);
}

void readRaster(GifFileType* gif, const GifImageDesc& d, std::vector<uint8_t>& raster) {
    raster.resize(size_t(d.Width) * d.Height);
    auto line = [&](int y) {
        require(DGifGetLine(gif, raster.data() + size_t(y) * d.Width, d.Width) == GIF_OK, "truncated gif");
    };
    if (d.Interlace) {
        const int starts[4] = {0, 4, 2, 1}, steps[4] = {8, 8, 4, 2};
        for (int pass = 0; pass < 4; pass++) {
            for (int y = starts[pass]; y < d.Height; y += steps[pass]) line(y);
        }
    } else {
        for (int y = 0; y < d.Height; y++) line(y);
    }
}

void gifFrames(const uint8_t* data, size_t size, const FrameFn& fn) {
    GifInput input{data, size};
    int err = 0;
    std::unique_ptr<GifFileType, GifClose> gif(DGifOpen(&input, gifRead, &err));
    require(gif != nullptr, "invalid gif");
    GifFileType* g = gif.get();
    const int W = g->SWidth, H = g->SHeight;
    checkBounds(W, H);
    std::vector<uint8_t> canvas(size_t(W) * H * 4, 0), saved, raster;
    const GraphicsControlBlock noControl{DISPOSAL_UNSPECIFIED, false, 0, NO_TRANSPARENT_COLOR};
    GraphicsControlBlock control = noControl;
    int frames = 0;
    auto background = [&](uint8_t* px) {
        if (control.TransparentColor != NO_TRANSPARENT_COLOR || !g->SColorMap ||
            g->SBackGroundColor >= g->SColorMap->ColorCount) {
            memset(px, 0, 4);
            return;
        }
        const GifColorType c = g->SColorMap->Colors[g->SBackGroundColor];
        px[0] = c.Red; px[1] = c.Green; px[2] = c.Blue; px[3] = 255;
    };
    while (true) {
        GifRecordType record;
        require(DGifGetRecordType(g, &record) == GIF_OK, "truncated gif");
        if (record == TERMINATE_RECORD_TYPE) break;
        if (record == EXTENSION_RECORD_TYPE) {
            int code = 0;
            GifByteType* ext = nullptr;
            require(DGifGetExtension(g, &code, &ext) == GIF_OK, "invalid gif extension");
            if (code == GRAPHICS_EXT_FUNC_CODE && ext && ext[0] == 4) {
                require(DGifExtensionToGCB(4, ext + 1, &control) == GIF_OK, "invalid gif control");
            }
            while (ext) require(DGifGetExtensionNext(g, &ext) == GIF_OK, "truncated gif extension");
            continue;
        }
        if (record != IMAGE_DESC_RECORD_TYPE) continue;
        require(DGifGetImageDesc(g) == GIF_OK, "invalid gif frame");
        const GifImageDesc d = g->Image;
        require(d.Left >= 0 && d.Top >= 0 && d.Width > 0 && d.Height > 0 && d.Left + d.Width <= W &&
                    d.Top + d.Height <= H, "invalid gif frame bounds");
        const ColorMapObject* colors = d.ColorMap ? d.ColorMap : g->SColorMap;
        require(colors != nullptr, "gif without palette");
        if (++frames == 1) {
            for (size_t i = 0; i < canvas.size(); i += 4) background(&canvas[i]);
        }
        if (control.DisposalMode == DISPOSE_PREVIOUS) saved = canvas;
        readRaster(g, d, raster);
        for (int y = 0; y < d.Height; y++) {
            for (int x = 0; x < d.Width; x++) {
                const int index = raster[size_t(y) * d.Width + x];
                if (index == control.TransparentColor) continue;
                require(index < colors->ColorCount, "invalid gif palette index");
                const GifColorType c = colors->Colors[index];
                uint8_t* px = &canvas[(size_t(y + d.Top) * W + x + d.Left) * 4];
                px[0] = c.Red; px[1] = c.Green; px[2] = c.Blue; px[3] = 255;
            }
        }
        const int delay = shownDelay(control.DelayTime * 10);
        if (!fn(canvas.data(), W, H, delay)) break;
        if (control.DisposalMode == DISPOSE_BACKGROUND) {
            for (int y = 0; y < d.Height; y++) {
                for (int x = 0; x < d.Width; x++) background(&canvas[(size_t(y + d.Top) * W + x + d.Left) * 4]);
            }
        } else if (control.DisposalMode == DISPOSE_PREVIOUS) {
            canvas.swap(saved);
        }
        control = noControl;
    }
    require(frames > 0, "gif without frames");
}

Source encodedSource(const std::vector<uint8_t>& bytes) {
    require(bytes.size() >= 12, "empty input");
    const uint8_t* b = bytes.data();
    if (!memcmp(b, "RIFF", 4) && !memcmp(b + 8, "WEBP", 4)) {
        return [&bytes](const FrameFn& fn) { webpFrames(bytes.data(), bytes.size(), fn); };
    }
    if (!memcmp(b, "GIF87a", 6) || !memcmp(b, "GIF89a", 6)) {
        return [&bytes](const FrameFn& fn) { gifFrames(bytes.data(), bytes.size(), fn); };
    }
    throw std::runtime_error("only gif and webp are decoded natively");
}

std::vector<uint8_t> encodeStill(const std::vector<uint8_t>& rgba, float quality) {
    uint8_t* raw = nullptr;
    const size_t size = WebPEncodeRGBA(rgba.data(), kCanvas, kCanvas, kCanvas * 4, quality, &raw);
    std::unique_ptr<uint8_t, WebPMemoryFree> out(raw);
    require(size > 0, "still encoding failed");
    return {raw, raw + size};
}

struct Encoded {
    std::vector<uint8_t> bytes;
    int keptFrames = 0;
};

// libwebp merges frames too alike, at worst down to one or to a still, which an animated pack rejects: that frame, twice.
void keepTwoFrames(std::vector<uint8_t>& webp, int durationMs, const WebPMuxAnimParams& params) {
    const WebPData input = {webp.data(), webp.size()};
    std::unique_ptr<WebPMux, MuxFree> in(WebPMuxCreate(&input, 0));
    int frames = 0;
    require(in != nullptr && WebPMuxNumChunks(in.get(), WEBP_CHUNK_ANMF, &frames) == WEBP_MUX_OK, "invalid animation");
    if (frames >= 2) return;
    WebPMuxFrameInfo frame;
    require(WebPMuxGetFrame(in.get(), 1, &frame) == WEBP_MUX_OK, "invalid animation");
    Data image;
    image.data = frame.bitstream;
    std::unique_ptr<WebPMux, MuxFree> out(WebPMuxNew());
    require(out != nullptr, "codec init");
    frame.id = WEBP_CHUNK_ANMF;
    frame.dispose_method = WEBP_MUX_DISPOSE_NONE;
    frame.blend_method = WEBP_MUX_NO_BLEND;
    bool ok = WebPMuxSetCanvasSize(out.get(), kCanvas, kCanvas) == WEBP_MUX_OK &&
              WebPMuxSetAnimationParams(out.get(), &params) == WEBP_MUX_OK;
    for (const int duration : {durationMs / 2, durationMs - durationMs / 2}) {
        frame.duration = duration;
        ok = ok && WebPMuxPushFrame(out.get(), &frame, 1) == WEBP_MUX_OK;
    }
    Data assembled;
    require(ok && WebPMuxAssemble(out.get(), &assembled.data) == WEBP_MUX_OK, "animation rebuild failed");
    webp.assign(assembled.data.bytes, assembled.data.bytes + assembled.data.size);
}

// Dropped frames extend the previous one: the animation keeps its length.
Encoded encodeOnce(const Source& source, int extent, float quality, int step, int method) {
    WebPAnimEncoderOptions options;
    require(WebPAnimEncoderOptionsInit(&options), "codec init");
    options.anim_params.loop_count = 0;
    options.minimize_size = 0;
    options.allow_mixed = 0;
    std::unique_ptr<WebPAnimEncoder, AnimEncoderFree> encoder(WebPAnimEncoderNew(kCanvas, kCanvas, &options));
    require(encoder != nullptr, "codec init");
    WebPConfig config;
    require(WebPConfigInit(&config), "codec init");
    config.quality = quality;
    config.method = method;
    config.thread_level = 1;

    auto add = [&](const std::vector<uint8_t>& rgba, int at) {
        Picture picture;
        picture.pic.width = kCanvas;
        picture.pic.height = kCanvas;
        picture.pic.use_argb = 1;
        require(WebPPictureImportRGBA(&picture.pic, rgba.data(), kCanvas * 4) &&
                    WebPAnimEncoderAdd(encoder.get(), &picture.pic, at, &config),
                "frame encoding failed");
    };
    Encoded result;
    std::vector<uint8_t> frame, first;
    int t = 0, index = 0;
    source([&](const uint8_t* rgba, int w, int h, int delay) {
        if (t > kMaxDurationMs - kMinFrameMs) return false;
        if (index % step == 0) {
            fitInto(rgba, w, h, extent, frame);
            // The first one waits for a second: a lone frame is encoded once, as a still.
            if (result.keptFrames == 0) {
                first = frame;
            } else {
                if (result.keptFrames == 1) add(first, 0);
                add(frame, t);
            }
            result.keptFrames++;
        }
        index++;
        t = std::min(kMaxDurationMs, t + delay);
        return true;
    });
    require(!first.empty(), "empty image");
    if (result.keptFrames == 1) {
        result.bytes = encodeStill(first, quality);
        return result;
    }
    require(WebPAnimEncoderAdd(encoder.get(), nullptr, t, nullptr), "animation close failed");
    Data assembled;
    require(WebPAnimEncoderAssemble(encoder.get(), &assembled.data), "animation assembly failed");
    result.bytes.assign(assembled.data.bytes, assembled.data.bytes + assembled.data.size);
    keepTwoFrames(result.bytes, t, options.anim_params);
    return result;
}

// Each attempt costs in proportion to the kept frames: frames are dropped before the drawing is shrunk.
std::vector<uint8_t> toWhatsApp(const Source& source, int sourceFrames, size_t limit) {
    int extent = kCanvas;
    int step = std::max(1, (sourceFrames + kMaxKeptFrames - 1) / kMaxKeptFrames);
    float quality = 80;
    // For animations, method 3 is quite a bit faster than 4 and weighs almost the same.
    const int method = sourceFrames > 1 ? 3 : 4;
    for (int attempt = 0; attempt < kAttempts; attempt++) {
        const Encoded e = encodeOnce(source, extent, quality, step, method);
        if (e.bytes.size() <= limit) return e.bytes;
        const double ratio = double(e.bytes.size()) / double(limit);
        const bool canDrop = e.keptFrames / 2 >= 10;
        if (ratio > 1.3 && canDrop) {
            step *= ratio > 2.4 && e.keptFrames / 4 >= 10 ? 4 : 2;
            if (ratio > 1.8) quality = std::max(45.f, quality - 15);
        } else if (quality > 30) {
            quality = std::max(30.f, float(quality - std::max(10.0, 50.0 * (ratio - 1.0) + 8.0)));
        } else if (canDrop) {
            step *= 2;
        } else if (extent > 384) {
            extent = 384;
        } else {
            quality = std::max(15.f, quality - 10);
        }
    }
    throw std::runtime_error("does not fit the whatsapp limit");
}

std::vector<uint8_t> bytesOf(JNIEnv* env, jbyteArray array) {
    require(array != nullptr, "empty input");
    const jsize n = env->GetArrayLength(array);
    require(size_t(n) <= kMaxInput, "input too large");
    std::vector<uint8_t> bytes(n);
    env->GetByteArrayRegion(array, 0, n, reinterpret_cast<jbyte*>(bytes.data()));
    return bytes;
}

std::vector<uint8_t> rgbaOf(JNIEnv* env, jintArray argb, int w, int h) {
    checkBounds(w, h);
    require(argb != nullptr && env->GetArrayLength(argb) == w * h, "pixel count mismatch");
    std::vector<jint> pixels(size_t(w) * h);
    env->GetIntArrayRegion(argb, 0, w * h, pixels.data());
    std::vector<uint8_t> rgba(pixels.size() * 4);
    for (size_t i = 0; i < pixels.size(); i++) {
        const uint32_t c = uint32_t(pixels[i]);
        rgba[i * 4] = uint8_t(c >> 16);
        rgba[i * 4 + 1] = uint8_t(c >> 8);
        rgba[i * 4 + 2] = uint8_t(c);
        rgba[i * 4 + 3] = uint8_t(c >> 24);
    }
    return rgba;
}

jbyteArray toJava(JNIEnv* env, const std::vector<uint8_t>& bytes) {
    jbyteArray array = env->NewByteArray(jsize(bytes.size()));
    if (array) env->SetByteArrayRegion(array, 0, jsize(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    return array;
}

template <typename F>
jbyteArray guarded(JNIEnv* env, F&& work) {
    try {
        return toJava(env, work());
    } catch (const std::exception& e) {
        env->ThrowNew(env->FindClass("java/io/IOException"), e.what());
        return nullptr;
    }
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_redrixdev_tikstik_media_NativeCodec_toWhatsApp(JNIEnv* env, jclass, jbyteArray raw, jint sourceFrames, jint limit) {
    return guarded(env, [&] {
        const std::vector<uint8_t> bytes = bytesOf(env, raw);
        return toWhatsApp(encodedSource(bytes), std::max(1, int(sourceFrames)), size_t(limit));
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_redrixdev_tikstik_media_NativeCodec_stillToWhatsApp(JNIEnv* env, jclass, jintArray argb, jint width, jint height, jint limit) {
    return guarded(env, [&] {
        const std::vector<uint8_t> rgba = rgbaOf(env, argb, width, height);
        const Source source = [&](const FrameFn& fn) { fn(rgba.data(), width, height, 0); };
        return toWhatsApp(source, 1, size_t(limit));
    });
}
