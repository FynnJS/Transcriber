#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct TranscriberContext {
    whisper_context * ctx = nullptr;
    std::atomic<bool> canceled{false};

    JavaVM * vm = nullptr;
    jobject listener = nullptr;
    jmethodID on_progress = nullptr;

    // Length of the input in 10 ms units and the last reported percentage, so progress
    // derived from decoded segments and whisper's segment windows stays monotonic.
    int total_cs = 0;
    int last_progress = 0;
};

TranscriberContext * from_handle(jlong handle) {
    return reinterpret_cast<TranscriberContext *>(handle);
}

JavaVM * java_vm(JNIEnv * env) {
    JavaVM * vm = nullptr;
    env->GetJavaVM(&vm);
    return vm;
}

JNIEnv * attach_env(JavaVM * vm, bool * did_attach) {
    *did_attach = false;
    JNIEnv * env = nullptr;
    if (vm == nullptr) {
        return nullptr;
    }
    const jint status = vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if (vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            *did_attach = true;
        } else {
            return nullptr;
        }
    }
    return env;
}

void report_progress(TranscriberContext * handle, int progress) {
    if (handle->listener == nullptr || handle->on_progress == nullptr) {
        return;
    }
    bool did_attach = false;
    JNIEnv * env = attach_env(handle->vm, &did_attach);
    if (env == nullptr) {
        return;
    }
    env->CallVoidMethod(handle->listener, handle->on_progress, static_cast<jint>(progress));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (did_attach) {
        handle->vm->DetachCurrentThread();
    }
}

/**
 * Reports a percentage only when it advances. Whisper reports 100 for the last window, but the
 * transcript is not finished at that point, so the bar stops at 99 until the service completes
 * the entry.
 */
void emit_progress(TranscriberContext * handle, int progress) {
    const int clamped = progress < 0 ? 0 : (progress > 99 ? 99 : progress);
    if (clamped <= handle->last_progress) {
        return;
    }
    handle->last_progress = clamped;
    report_progress(handle, clamped);
}

// Whisper only reports progress once per segment window (about 30 s of audio), which leaves the
// bar at 0 % for short voice messages. The end of the last decoded segment gives a much finer
// estimate, independent of the window size.
void segment_callback(whisper_context * ctx, whisper_state * /*state*/, int /*n_new*/, void * user_data) {
    auto * handle = static_cast<TranscriberContext *>(user_data);
    if (handle == nullptr || handle->total_cs <= 0) {
        return;
    }
    const int segments = whisper_full_n_segments(ctx);
    if (segments <= 0) {
        return;
    }
    const int64_t decoded_cs = whisper_full_get_segment_t1(ctx, segments - 1);
    if (decoded_cs <= 0) {
        return;
    }
    emit_progress(handle, static_cast<int>((100 * decoded_cs) / handle->total_cs));
}

void progress_callback(whisper_context * /*ctx*/, whisper_state * /*state*/, int progress, void * user_data) {
    auto * handle = static_cast<TranscriberContext *>(user_data);
    if (handle == nullptr) {
        return;
    }
    emit_progress(handle, progress);
}

void log_callback(enum ggml_log_level level, const char * text, void * /*user_data*/) {
    const int priority = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO;
    __android_log_print(priority, LOG_TAG, "%s", text);
}

bool abort_callback(void * user_data) {
    auto * handle = static_cast<TranscriberContext *>(user_data);
    return handle != nullptr && handle->canceled.load();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_fynnjs_transcriber_whisper_WhisperContext_nativeInit(
        JNIEnv * env, jclass /*clazz*/, jstring model_path, jint threads) {
    if (model_path == nullptr) {
        return 0;
    }
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        return 0;
    }

    auto * handle = new TranscriberContext();
    handle->vm = java_vm(env);
    whisper_log_set(log_callback, nullptr);

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;

    LOGI("Loading model %s", path);
    handle->ctx = whisper_init_from_file_with_params(path, context_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (handle->ctx == nullptr) {
        LOGE("Failed to load model");
        delete handle;
        return 0;
    }
    LOGI("Model loaded, threads=%d", static_cast<int>(threads));
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_fynnjs_transcriber_whisper_WhisperContext_nativeFree(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong handle_ptr) {
    auto * handle = from_handle(handle_ptr);
    if (handle == nullptr) {
        return;
    }
    if (handle->ctx != nullptr) {
        whisper_free(handle->ctx);
    }
    delete handle;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_fynnjs_transcriber_whisper_WhisperContext_nativeCancel(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong handle_ptr) {
    auto * handle = from_handle(handle_ptr);
    if (handle != nullptr) {
        handle->canceled.store(true);
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_fynnjs_transcriber_whisper_WhisperContext_nativeTranscribe(
        JNIEnv * env,
        jclass /*clazz*/,
        jlong handle_ptr,
        jfloatArray samples,
        jstring language,
        jint threads,
        jint audio_context_frames,
        jobject listener) {
    auto * handle = from_handle(handle_ptr);
    if (handle == nullptr || handle->ctx == nullptr || samples == nullptr) {
        return nullptr;
    }

    const jsize count = env->GetArrayLength(samples);
    if (count <= 0) {
        return nullptr;
    }
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    handle->canceled.store(false);
    handle->last_progress = 0;
    // Input is 16 kHz mono, so one 10 ms unit is 160 samples.
    handle->total_cs = count / 160;
    handle->listener = listener;
    handle->on_progress = nullptr;
    if (listener != nullptr) {
        handle->listener = env->NewGlobalRef(listener);
        jclass listener_class = env->GetObjectClass(listener);
        handle->on_progress = env->GetMethodID(listener_class, "onProgress", "(I)V");
        env->DeleteLocalRef(listener_class);
    }

    const char * lang_chars = nullptr;
    std::string lang;
    if (language != nullptr) {
        lang_chars = env->GetStringUTFChars(language, nullptr);
        if (lang_chars != nullptr) {
            lang = lang_chars;
            env->ReleaseStringUTFChars(language, lang_chars);
        }
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.suppress_blank = true;
    params.n_threads = static_cast<int>(threads);
    // Short live-preview windows must shrink the encoder context, otherwise every window pays
    // for the full 30 s encoder pass. 0 keeps whisper's default.
    params.audio_ctx = static_cast<int>(audio_context_frames);
    // "auto" makes whisper detect the language and then transcribe. Setting
    // params.detect_language instead would return right after detection with an
    // empty result, and a null language would leave the detection flag unused.
    const bool detect = lang.empty() || lang == "auto";
    params.language = detect ? "auto" : lang.c_str();
    params.detect_language = false;
    params.progress_callback = progress_callback;
    params.progress_callback_user_data = handle;
    params.new_segment_callback = segment_callback;
    params.new_segment_callback_user_data = handle;
    params.abort_callback = abort_callback;
    params.abort_callback_user_data = handle;

    LOGI("Transcribing %d samples in %s mode", static_cast<int>(count), detect ? "auto" : params.language);
    report_progress(handle, 0);

    const int result = whisper_full(handle->ctx, params, pcm.data(), static_cast<int>(pcm.size()));

    if (handle->listener != nullptr) {
        env->DeleteGlobalRef(handle->listener);
        handle->listener = nullptr;
        handle->on_progress = nullptr;
    }

    if (result != 0) {
        LOGE("whisper_full failed with %d", result);
        return nullptr;
    }

    std::string text;
    const int segment_count = whisper_full_n_segments(handle->ctx);
    for (int i = 0; i < segment_count; ++i) {
        const char * segment = whisper_full_get_segment_text(handle->ctx, i);
        if (segment != nullptr) {
            text += segment;
        }
    }

    // Trim leading/trailing whitespace.
    const auto first = text.find_first_not_of(" \t\n\r");
    const auto last = text.find_last_not_of(" \t\n\r");
    text = (first == std::string::npos) ? "" : text.substr(first, last - first + 1);

    std::string detected = "unknown";
    const int lang_id = whisper_full_lang_id(handle->ctx);
    if (lang_id >= 0) {
        const char * lang_str = whisper_lang_str(lang_id);
        if (lang_str != nullptr) {
            detected = lang_str;
        }
    }

    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray output = env->NewObjectArray(2, string_class, nullptr);
    env->SetObjectArrayElement(output, 0, env->NewStringUTF(text.c_str()));
    env->SetObjectArrayElement(output, 1, env->NewStringUTF(detected.c_str()));
    return output;
}
