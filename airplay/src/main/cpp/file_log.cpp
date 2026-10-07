#include "file_log.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <deque>
#include <map>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex g_mutex;
std::atomic<int> g_fd{-1};
/* Under g_mutex: the file's path, for starting it afresh. */
std::string g_path;
std::atomic<long> g_max_bytes{20 * 1024 * 1024};

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

/* Moves the full file to ".old" and starts a new one under the same descriptor. */
void startAfresh(int fd) {
    std::lock_guard<std::mutex> lock(g_mutex);
    struct stat st{};
    // Another thread may have just done it.
    if (g_path.empty() || fstat(fd, &st) != 0 || st.st_size <= g_max_bytes.load()) return;
    std::string old = g_path + ".old";
    if (rename(g_path.c_str(), old.c_str()) != 0) return;
    int fresh = open(g_path.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    if (fresh < 0) return;
    dup2(fresh, fd);
    close(fresh);
}

void writeLine(int fd, int priority, const char *tag, const char *message) {
    struct stat st{};
    if (fstat(fd, &st) == 0 && st.st_size > g_max_bytes.load()) startAfresh(fd);
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
    // One O_APPEND write per line keeps each line whole.
    (void) !write(fd, line, static_cast<size_t>(length));
}
}  // namespace

extern "C" void castbay_log_open(const char *path, long max_bytes) {
    std::lock_guard<std::mutex> lock(g_mutex);
    int current = g_fd.load();
    if (!path || !*path) {
        // Not closed: another thread may be mid-write. One descriptor is left open.
        g_fd = -1;
        g_path.clear();
        return;
    }
    if (max_bytes > 0) g_max_bytes = max_bytes;
    g_path = path;
    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    if (fd < 0) return;
    if (current >= 0 && dup2(fd, current) >= 0) {
        // Swap the file under the existing descriptor, so concurrent writers stay valid.
        close(fd);
    } else {
        g_fd = fd;
    }
}

namespace {
/*
 * The same line again within kRepeatWindowSec is counted rather than logged. With the sender
 * out of reach, UxPlay logged "raop_rtp resend failed" some fifty times a second, which pushed
 * everything else out of the recent lines: a problem report about stuttering music then held
 * 20 seconds. Once the window is over, one line says how many there were.
 */
constexpr long kRepeatWindowSec = 10;
constexpr size_t kMaxRepeatKeys = 64;
struct Repeat {
    long since;
    int count;
    int priority;
    std::string tag;
    std::string message;
};
std::mutex g_repeatMutex;
std::map<std::string, Repeat> g_repeats;

long monotonicSec() {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec;
}

int emit(int priority, const char *tag, const char *message) {
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

/* The counts of lines whose window is over (all of them with [all]), taken out of the table. */
std::vector<Repeat> takeRepeats(long now, bool all) {
    std::vector<Repeat> due;
    std::lock_guard<std::mutex> lock(g_repeatMutex);
    for (auto it = g_repeats.begin(); it != g_repeats.end();) {
        if (all || now - it->second.since >= kRepeatWindowSec) {
            if (it->second.count > 0) due.push_back(it->second);
            it = g_repeats.erase(it);
        } else {
            ++it;
        }
    }
    return due;
}

void emitRepeats(const std::vector<Repeat> &due, long now) {
    for (const auto &repeat : due) {
        char line[512];
        snprintf(line, sizeof(line), "(%d more of \"%.300s\" in %ld s)", repeat.count, repeat.message.c_str(),
                 now - repeat.since);
        emit(repeat.priority, repeat.tag.c_str(), line);
    }
}
}  // namespace

extern "C" int castbay_logf(int priority, const char *tag, const char *format, ...) {
    char message[3072];
    va_list args;
    va_start(args, format);
    vsnprintf(message, sizeof(message), format, args);
    va_end(args);
    long now = monotonicSec();
    emitRepeats(takeRepeats(now, false), now);
    std::string key = std::string(tag ? tag : "") + '\x1f' + message;
    {
        std::lock_guard<std::mutex> lock(g_repeatMutex);
        auto found = g_repeats.find(key);
        if (found != g_repeats.end()) {
            found->second.count++;
            return 0;
        }
        if (g_repeats.size() < kMaxRepeatKeys) g_repeats[key] = Repeat{now, 0, priority, tag ? tag : "", message};
    }
    return emit(priority, tag, message);
}

std::string castbay_recent_log() {
    // Counts still being kept go in now, so a report shows them.
    long now = monotonicSec();
    emitRepeats(takeRepeats(now, true), now);
    std::lock_guard<std::mutex> lock(g_recentMutex);
    std::string all;
    for (const auto &line : g_recent) {
        all += line;
        all += '\n';
    }
    return all;
}
