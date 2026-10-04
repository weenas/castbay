#include "file_log.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <deque>
#include <mutex>
#include <string>

namespace {
/* Stop appending past this size rather than filling the TV's storage. */
constexpr off_t kMaxFileBytes = 20 * 1024 * 1024;

std::mutex g_mutex;
std::atomic<int> g_fd{-1};

/* The latest lines, for problem reports; each one short enough to keep the whole small. */
constexpr size_t kRecentLines = 1000;
constexpr size_t kRecentLineChars = 400;
std::mutex g_recentMutex;
std::deque<std::string> g_recent;

char priorityLetter(int priority) {
    switch (priority) {
    case ANDROID_LOG_VERBOSE: return 'V';
    case ANDROID_LOG_DEBUG: return 'D';
    case ANDROID_LOG_INFO: return 'I';
    case ANDROID_LOG_WARN: return 'W';
    case ANDROID_LOG_ERROR: return 'E';
    case ANDROID_LOG_FATAL: return 'F';
    default: return '?';
    }
}

void writeLine(int fd, int priority, const char *tag, const char *message) {
    struct stat st{};
    if (fstat(fd, &st) == 0 && st.st_size > kMaxFileBytes) return;
    timespec now{};
    clock_gettime(CLOCK_REALTIME, &now);
    tm local{};
    localtime_r(&now.tv_sec, &local);
    char line[4096];
    int length = snprintf(line, sizeof(line), "%02d-%02d %02d:%02d:%02d.%03ld %5d %5d %c %s: %s\n",
                          local.tm_mon + 1, local.tm_mday, local.tm_hour, local.tm_min, local.tm_sec,
                          now.tv_nsec / 1000000, getpid(), gettid(), priorityLetter(priority),
                          tag ? tag : "", message ? message : "");
    if (length <= 0) return;
    if (length >= static_cast<int>(sizeof(line))) {
        length = sizeof(line) - 1;
        line[length - 1] = '\n';
    }
    // One O_APPEND write per line keeps lines whole alongside the Kotlin writer.
    (void) !write(fd, line, static_cast<size_t>(length));
}
}  // namespace

extern "C" void castbay_log_open(const char *path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    int current = g_fd.load();
    if (!path || !*path) {
        // Not closed: another thread may be mid-write. One descriptor is left open.
        g_fd = -1;
        return;
    }
    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    if (fd < 0) return;
    if (current >= 0 && dup2(fd, current) >= 0) {
        // Swap the file under the existing descriptor, so concurrent writers stay valid.
        close(fd);
    } else {
        g_fd = fd;
    }
}

extern "C" int castbay_logf(int priority, const char *tag, const char *format, ...) {
    char message[3072];
    va_list args;
    va_start(args, format);
    vsnprintf(message, sizeof(message), format, args);
    va_end(args);
    int result = __android_log_write(priority, tag, message);
    {
        timespec now{};
        clock_gettime(CLOCK_REALTIME, &now);
        tm local{};
        localtime_r(&now.tv_sec, &local);
        char line[kRecentLineChars];
        snprintf(line, sizeof(line), "%02d-%02d %02d:%02d:%02d.%03ld %c %s: %s",
                 local.tm_mon + 1, local.tm_mday, local.tm_hour, local.tm_min, local.tm_sec,
                 now.tv_nsec / 1000000, priorityLetter(priority), tag ? tag : "", message);
        std::lock_guard<std::mutex> lock(g_recentMutex);
        g_recent.emplace_back(line);
        if (g_recent.size() > kRecentLines) g_recent.pop_front();
    }
    int fd = g_fd.load();
    if (fd >= 0) writeLine(fd, priority, tag, message);
    return result;
}

std::string castbay_recent_log() {
    std::lock_guard<std::mutex> lock(g_recentMutex);
    std::string all;
    for (const auto &line : g_recent) {
        all += line;
        all += '\n';
    }
    return all;
}
