#pragma once

#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Logs to logcat and, once castbay_log_open() was called, also appends to a file.
 * Some TVs (e.g. TCL) silence app logs in logd, so the file is the only way to see them.
 * Same arguments as __android_log_print.
 */
int castbay_logf(int priority, const char *tag, const char *format, ...)
    __attribute__((format(printf, 3, 4)));

/* Starts appending to [path]; NULL or "" stops. Thread-safe. */
void castbay_log_open(const char *path);

#ifdef __cplusplus
}

#include <string>

/*
 * The latest lines logged (all builds), oldest first, one per line, for a problem report:
 * some devices (TCL TVs, BYD car displays) keep no app logs in logd at all. Thread-safe.
 */
std::string castbay_recent_log();
#endif
