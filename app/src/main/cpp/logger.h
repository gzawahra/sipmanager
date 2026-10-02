#ifndef BARESIP_LOGGER_H
#define BARESIP_LOGGER_H

#ifdef __cplusplus
extern "C" {
#endif

// Only include Android log if building for Android
#ifdef __ANDROID__
#include <android/log.h>
#else
// Fallback if not on Android - printf as stub
#define __android_log_print(prio, tag, ...) printf(__VA_ARGS__)
#define ANDROID_LOG_DEBUG 3
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_ERROR 6
#endif

#define LOG_TAG "BaresipLib"

#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#ifdef __cplusplus
}
#endif

#endif // BARESIP_LOGGER_H
