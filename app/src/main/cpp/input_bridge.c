// Native bridge: open /dev/input/eventX, query capabilities, grab exclusively, read events.
// Runs inside the Shizuku user service (shell uid, member of the "input" group).
#include <jni.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <string.h>
#include <errno.h>
#include <poll.h>

#define BITS_PER_LONG (sizeof(long) * 8)
#define NBITS(x) ((((x) - 1) / BITS_PER_LONG) + 1)
#define TEST_BIT(bit, array) (((array)[(bit) / BITS_PER_LONG] >> ((bit) % BITS_PER_LONG)) & 1UL)

#define JNI_FN(name) Java_com_mousemapper_app_service_NativeInput_##name

JNIEXPORT jint JNICALL JNI_FN(nOpen)(JNIEnv *env, jclass clazz, jstring jpath) {
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NONBLOCK);
    int err = errno;
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return fd >= 0 ? fd : -err;
}

JNIEXPORT void JNICALL JNI_FN(nClose)(JNIEnv *env, jclass clazz, jint fd) {
    if (fd >= 0) close(fd);
}

JNIEXPORT jint JNICALL JNI_FN(nGrab)(JNIEnv *env, jclass clazz, jint fd, jboolean on) {
    if (ioctl(fd, EVIOCGRAB, on ? 1 : 0) < 0) return -errno;
    return 0;
}

JNIEXPORT jstring JNICALL JNI_FN(nName)(JNIEnv *env, jclass clazz, jint fd) {
    char name[256];
    memset(name, 0, sizeof(name));
    if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name) < 0) strcpy(name, "unknown");
    return (*env)->NewStringUTF(env, name);
}

// Bitmask: 1 = relative pointer (mouse), 2 = full keyboard, 4 = multi-touch screen
JNIEXPORT jint JNICALL JNI_FN(nCaps)(JNIEnv *env, jclass clazz, jint fd) {
    unsigned long relbits[NBITS(REL_MAX + 1)];
    unsigned long keybits[NBITS(KEY_MAX + 1)];
    unsigned long absbits[NBITS(ABS_MAX + 1)];
    memset(relbits, 0, sizeof(relbits));
    memset(keybits, 0, sizeof(keybits));
    memset(absbits, 0, sizeof(absbits));
    ioctl(fd, EVIOCGBIT(EV_REL, sizeof(relbits)), relbits);
    ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keybits)), keybits);
    ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absbits)), absbits);
    int caps = 0;
    if (TEST_BIT(REL_X, relbits) && TEST_BIT(REL_Y, relbits)) caps |= 1;
    if (TEST_BIT(KEY_A, keybits) && TEST_BIT(KEY_SPACE, keybits)) caps |= 2;
    if (TEST_BIT(ABS_MT_POSITION_X, absbits)) caps |= 4;
    return caps;
}

// Waits up to timeoutMs, then reads events into out as triples (type, code, value).
// Returns number of events, 0 on timeout, negative errno on failure.
JNIEXPORT jint JNICALL JNI_FN(nRead)(JNIEnv *env, jclass clazz, jint fd, jintArray out, jint timeoutMs) {
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN;
    pfd.revents = 0;
    int pr = poll(&pfd, 1, timeoutMs);
    if (pr < 0) return errno == EINTR ? 0 : -errno;
    if (pr == 0) return 0;
    if (pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) return -ENODEV;

    jsize cap = (*env)->GetArrayLength(env, out) / 3;
    if (cap > 64) cap = 64;
    struct input_event evs[64];
    ssize_t n = read(fd, evs, sizeof(struct input_event) * cap);
    if (n < 0) return (errno == EAGAIN || errno == EINTR) ? 0 : -errno;
    int count = (int) (n / sizeof(struct input_event));
    jint buf[64 * 3];
    for (int i = 0; i < count; i++) {
        buf[i * 3] = evs[i].type;
        buf[i * 3 + 1] = evs[i].code;
        buf[i * 3 + 2] = evs[i].value;
    }
    (*env)->SetIntArrayRegion(env, out, 0, count * 3, buf);
    return count;
}
