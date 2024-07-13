/* //device/libs/android_runtime/android_server_AlarmManagerService.cpp
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "AlarmManagerService"
#define CLOCK_POWEROFF_WAKE  12
#define CLOCK_POWERON_WAKE   13
#define CLOCK_POWEROFF_ALARM 14

#include "JNIHelp.h"
#include "jni.h"
#include <utils/Log.h>
#include <utils/misc.h>
#include <utils/String8.h>

#include <dirent.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/timerfd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <linux/ioctl.h>
#include <linux/android_alarm.h>
#include <linux/rtc.h>
#include <cutils/sockets.h>
#include <netdb.h>
#include <cutils/properties.h>
#include <iostream>
#include <sstream>

using namespace std;

#ifdef __LP64__
typedef time_t time64_t;
#else
#include <time64.h>
#endif

#ifndef ANDROID_NTPSOCKET_DIR
#define ANDROID_NTPSOCKET_DIR      "/data/tmp/socket"
#endif

#define NTP_SERVER0                "0.asia.pool.ntp.org"
#define NTP_SERVER1                "1.asia.pool.ntp.org"
#define NTP_SERVER2                "2.asia.pool.ntp.org"
#define NTP_SERVER3                "3.asia.pool.ntp.org"
#define NTP_SERVER4                "0.cn.pool.ntp.org"
#define NTP_SERVER5                "0.hk.pool.ntp.org"
#define NTP_SERVER6                "3.tw.pool.ntp.org"
#define NTP_SERVER7                "0.jp.pool.ntp.org"
#define NTP_SERVER8                "1.jp.pool.ntp.org"
#define NTP_SERVER9                "2.jp.pool.ntp.org"
#define NTP_SERVER10               "3.jp.pool.ntp.org"
#define NTP_SERVER11               "0.kr.pool.ntp.org"
#define NTP_SERVER12               "0.us.pool.ntp.org"
#define NTP_SERVER13               "1.us.pool.ntp.org"
#define NTP_SERVER14               "2.us.pool.ntp.org"
#define NTP_SERVER15               "3.us.pool.ntp.org"

#define NTP_PORT                    123
#define JAN_1970                    0x83aa7e80
#define NTPFRAC(x) (4294 * (x) + ((1981 * (x))>>11))
#define NTP_CONNECT_MAX_TIME        30
#define NTP_RECV_TIMEOUT            10
#define random(x) (rand()%x+1)
#define DELTA_TIME                  "persist.delta.time"

namespace android {

pthread_mutex_t _mutex = PTHREAD_MUTEX_INITIALIZER;
time64_t _delta = 0;
time64_t delta_alarm = 0;
static int firstConnectNetwork = 0;

typedef struct NtpTime {
    unsigned int coarse;
    unsigned int fine;
} NTPTIME;

typedef struct ntpheader {
    union {
        struct {
            char local_precision;
            char Poll;
            unsigned char stratum;
            unsigned char Mode :3;
            unsigned char VN :3;
            unsigned char LI :2;
        };
        unsigned int headData;
    };
} NTPHEADER;

typedef struct NtpPacked {
    NTPHEADER header;

    unsigned int root_delay;
    unsigned int root_dispersion;
    unsigned int refid;
    NTPTIME reftime;
    NTPTIME orgtime;
    NTPTIME recvtime;
    NTPTIME trantime;
} NTPPACKED, *PNTPPACKED;

static const int ANDROID_ALARM_TYPE_COUNT = 7;
static const size_t N_ANDROID_TIMERFDS = ANDROID_ALARM_TYPE_COUNT + 1;
static const clockid_t android_alarm_to_clockid[N_ANDROID_TIMERFDS] = {
    CLOCK_REALTIME_ALARM,
    CLOCK_REALTIME,
    CLOCK_BOOTTIME_ALARM,
    CLOCK_BOOTTIME,
//    CLOCK_MONOTONIC,
    CLOCK_POWEROFF_WAKE,
    CLOCK_POWERON_WAKE,
    CLOCK_POWEROFF_ALARM,
    CLOCK_REALTIME,
};
/* to match the legacy alarm driver implementation, we need an extra
   CLOCK_REALTIME fd which exists specifically to be canceled on RTC changes */

class AlarmImpl
{
public:
    AlarmImpl(int *fds, size_t n_fds);
    virtual ~AlarmImpl();

    virtual int set(int type, struct timespec *ts) = 0;
    virtual int setTime(struct timeval *tv) = 0;
    virtual int waitForAlarm() = 0;
    /* SPRD: Regular PowerOnOff Feature @{ */
    virtual int clear(int type) = 0;
    /* @} */

protected:
    int *fds;
    size_t n_fds;
    int lsock;
    int drm_sockfd;
};

class AlarmImplAlarmDriver : public AlarmImpl
{
public:
    AlarmImplAlarmDriver(int fd) : AlarmImpl(&fd, 1) { }

    int set(int type, struct timespec *ts);
    int setTime(struct timeval *tv);
    int waitForAlarm();
    /* SPRD: Regular PowerOnOff Feature @{ */
    int clear(int type);
    /* @} */
};

class AlarmImplTimerFd : public AlarmImpl
{
public:
    AlarmImplTimerFd(int fds[N_ANDROID_TIMERFDS], int epollfd, int rtc_id) :
        AlarmImpl(fds, N_ANDROID_TIMERFDS), epollfd(epollfd), rtc_id(rtc_id) { }
    ~AlarmImplTimerFd();

    int set(int type, struct timespec *ts);
    int setTime(struct timeval *tv);
    int waitForAlarm();
    /* SPRD: Regular PowerOnOff Feature @{ */
    int clear(int type);
    /* @} */

private:
    int epollfd;
    int rtc_id;
};

AlarmImpl::AlarmImpl(int *fds_, size_t n_fds) : fds(new int[n_fds]),
        n_fds(n_fds), lsock(-1), drm_sockfd(-1)
{
    memcpy(fds, fds_, n_fds * sizeof(fds[0]));
}

AlarmImpl::~AlarmImpl()
{
    for (size_t i = 0; i < n_fds; i++) {
        close(fds[i]);
    }
    delete [] fds;
    if (drm_sockfd != -1) close(drm_sockfd);
    if (lsock != -1) close(lsock);
}

int AlarmImplAlarmDriver::set(int type, struct timespec *ts)
{
    return ioctl(fds[0], ANDROID_ALARM_SET(type), ts);
}

/* SPRD: Regular PowerOnOff Feature @{ */
int AlarmImplAlarmDriver::clear(int type)
{
    struct timespec ts;
    ts.tv_sec = 0;
    ts.tv_nsec = 0;
    ALOGD("AlarmImplAlarmDriver::clear type = %d", type);
    return ioctl(fds[0], ANDROID_ALARM_CLEAR(type), &ts);
}
/* @} */

void ntp_property_set(time64_t delta_time) {
    char delta_prop[PROPERTY_VALUE_MAX];
    stringstream stream;
    string result;
    stream << delta_time;
    stream >> result;
    strcpy(delta_prop, result.c_str());
    property_set(DELTA_TIME, delta_prop);
}

time64_t ntp_property_get() {
    char delta_prop[PROPERTY_VALUE_MAX];
    property_get(DELTA_TIME, delta_prop, "1LL<<63");
    if(0 == (strcmp(delta_prop, "1LL<<63"))) {
        return 1LL<<63;
    } else {
        time64_t delta_time;
        istringstream is(delta_prop);
        is >> delta_time;
    return delta_time;
    }
}

int AlarmImplAlarmDriver::setTime(struct timeval *tv)
{
    struct timespec ts;
    int res;
    struct timeval old_time;
    memset(&old_time, 0, sizeof(old_time));
    if (gettimeofday(&old_time, NULL) == -1) {
        ALOGE("get time fail! %s", strerror(errno));
    }
    
    ts.tv_sec = tv->tv_sec;
    ts.tv_nsec = tv->tv_usec * 1000;
    res = ioctl(fds[0], ANDROID_ALARM_SET_RTC, &ts);
    if (res < 0) {
        ALOGV("ANDROID_ALARM_SET_RTC ioctl failed: %s\n", strerror(errno));
    } else if (old_time.tv_sec && firstConnectNetwork) {
        pthread_mutex_lock(&_mutex);
        delta_alarm = tv->tv_sec - old_time.tv_sec;
        _delta = ntp_property_get();
        _delta -= delta_alarm;
        ntp_property_set(_delta);
        pthread_mutex_unlock(&_mutex);
        ALOGD("AlarmImplAlarmDriver::setTime, drm_ntp, _delta: %lld, delta_alarm: %lld, tv->tv_sec: %ld, old_time.tv_sec: %ld.", _delta, delta_alarm, tv->tv_sec, old_time.tv_sec);
    }
    return res;
}

int AlarmImplAlarmDriver::waitForAlarm()
{
    return ioctl(fds[0], ANDROID_ALARM_WAIT);
}

AlarmImplTimerFd::~AlarmImplTimerFd()
{
    for (size_t i = 0; i < N_ANDROID_TIMERFDS; i++) {
        epoll_ctl(epollfd, EPOLL_CTL_DEL, fds[i], NULL);
    }
    close(epollfd);
}

int AlarmImplTimerFd::set(int type, struct timespec *ts)
{
    if (type > ANDROID_ALARM_TYPE_COUNT) {
        errno = EINVAL;
        return -1;
    }

    if (!ts->tv_nsec && !ts->tv_sec) {
        ts->tv_nsec = 1;
    }
    /* timerfd interprets 0 = disarm, so replace with a practically
       equivalent deadline of 1 ns */

    struct itimerspec spec;
    memset(&spec, 0, sizeof(spec));
    memcpy(&spec.it_value, ts, sizeof(spec.it_value));

    return timerfd_settime(fds[type], TFD_TIMER_ABSTIME, &spec, NULL);
}

/* SPRD: Regular PowerOnOff Feature @{ */
int AlarmImplTimerFd::clear(int type)
{
    struct itimerspec spec;
    memset(&spec, 0, sizeof(spec));
    spec.it_value.tv_sec = spec.it_value.tv_nsec = 0;
    ALOGD("AlarmImplTimerFd::clear type = %d", type);
    return timerfd_settime(fds[type], TFD_TIMER_ABSTIME, &spec, NULL);

}
/* @} */

int AlarmImplTimerFd::setTime(struct timeval *tv)
{
    struct rtc_time rtc;
    struct tm tm, *gmtime_res;
    int fd;
    int res;

    struct timeval old_time;
    memset(&old_time, 0, sizeof(old_time));
    if (gettimeofday(&old_time, NULL) == -1) {
        ALOGE("get time fail! %s", strerror(errno));
    }
    
    res = settimeofday(tv, NULL);
    if (res < 0) {
        ALOGV("settimeofday() failed: %s\n", strerror(errno));
        return -1;
    }

    if (old_time.tv_sec && firstConnectNetwork) {
        pthread_mutex_lock(&_mutex);
        delta_alarm = tv->tv_sec - old_time.tv_sec;
        _delta = ntp_property_get();
        _delta -= delta_alarm;
        ntp_property_set(_delta);
        pthread_mutex_unlock(&_mutex);
        ALOGD("AlarmImplTimerFd::setTime, drm_ntp, _delta: %lld, delta_alarm: %lld, tv->tv_sec: %ld, old_time.tv_sec: %ld.", _delta, delta_alarm, tv->tv_sec, old_time.tv_sec);
    }

    if (rtc_id < 0) {
        ALOGV("Not setting RTC because wall clock RTC was not found");
        errno = ENODEV;
        return -1;
    }

    android::String8 rtc_dev = String8::format("/dev/rtc%d", rtc_id);
    fd = open(rtc_dev.string(), O_RDWR);
    if (fd < 0) {
        ALOGV("Unable to open %s: %s\n", rtc_dev.string(), strerror(errno));
        return res;
    }

    gmtime_res = gmtime_r(&tv->tv_sec, &tm);
    if (!gmtime_res) {
        ALOGV("gmtime_r() failed: %s\n", strerror(errno));
        res = -1;
        goto done;
    }

    memset(&rtc, 0, sizeof(rtc));
    rtc.tm_sec = tm.tm_sec;
    rtc.tm_min = tm.tm_min;
    rtc.tm_hour = tm.tm_hour;
    rtc.tm_mday = tm.tm_mday;
    rtc.tm_mon = tm.tm_mon;
    rtc.tm_year = tm.tm_year;
    rtc.tm_wday = tm.tm_wday;
    rtc.tm_yday = tm.tm_yday;
    rtc.tm_isdst = tm.tm_isdst;
    res = ioctl(fd, RTC_SET_TIME, &rtc);
    if (res < 0)
        ALOGV("RTC_SET_TIME ioctl failed: %s\n", strerror(errno));
done:
    close(fd);
    return res;
}

int AlarmImplTimerFd::waitForAlarm()
{
    epoll_event events[N_ANDROID_TIMERFDS];

    int nevents = epoll_wait(epollfd, events, N_ANDROID_TIMERFDS, -1);
    if (nevents < 0) {
        return nevents;
    }

    int result = 0;
    for (int i = 0; i < nevents; i++) {
        uint32_t alarm_idx = events[i].data.u32;
        uint64_t unused;
        ssize_t err = read(fds[alarm_idx], &unused, sizeof(unused));
        if (err < 0) {
            if (alarm_idx == ANDROID_ALARM_TYPE_COUNT && errno == ECANCELED) {
                result |= ANDROID_ALARM_TIME_CHANGE_MASK;
            } else {
                return err;
            }
        } else {
            result |= (1 << alarm_idx);
        }
    }

    return result;
}

static jint android_server_AlarmManagerService_setKernelTime(JNIEnv*, jobject, jlong nativeData, jlong millis)
{
    AlarmImpl *impl = reinterpret_cast<AlarmImpl *>(nativeData);
    struct timeval tv;
    int ret;

    if (millis <= 0 || millis / 1000LL >= INT_MAX) {
        return -1;
    }

    tv.tv_sec = (time_t) (millis / 1000LL);
    tv.tv_usec = (suseconds_t) ((millis % 1000LL) * 1000LL);

    ALOGD("Setting time of day to sec=%d\n", (int) tv.tv_sec);

    ret = impl->setTime(&tv);

    if(ret < 0) {
        ALOGW("Unable to set rtc to %ld: %s\n", tv.tv_sec, strerror(errno));
        ret = -1;
    }
    return ret;
}

static jint android_server_AlarmManagerService_setKernelTimezone(JNIEnv*, jobject, jlong, jint minswest)
{
    struct timezone tz;

    tz.tz_minuteswest = minswest;
    tz.tz_dsttime = 0;

    int result = settimeofday(NULL, &tz);
    if (result < 0) {
        ALOGE("Unable to set kernel timezone to %d: %s\n", minswest, strerror(errno));
        return -1;
    } else {
        ALOGD("Kernel timezone updated to %d minutes west of GMT\n", minswest);
    }

    return 0;
}

static jlong init_alarm_driver()
{
    int fd = open("/dev/alarm", O_RDWR);
    if (fd < 0) {
        ALOGV("opening alarm driver failed: %s", strerror(errno));
        return 0;
    }

    AlarmImpl *ret = new AlarmImplAlarmDriver(fd);
    return reinterpret_cast<jlong>(ret);
}

static const char rtc_sysfs[] = "/sys/class/rtc";

static bool rtc_is_hctosys(unsigned int rtc_id)
{
    android::String8 hctosys_path = String8::format("%s/rtc%u/hctosys",
            rtc_sysfs, rtc_id);

    FILE *file = fopen(hctosys_path.string(), "re");
    if (!file) {
        ALOGE("failed to open %s: %s", hctosys_path.string(), strerror(errno));
        return false;
    }

    unsigned int hctosys;
    bool ret = false;
    int err = fscanf(file, "%u", &hctosys);
    if (err == EOF)
        ALOGE("failed to read from %s: %s", hctosys_path.string(),
                strerror(errno));
    else if (err == 0)
        ALOGE("%s did not have expected contents", hctosys_path.string());
    else
        ret = hctosys;

    fclose(file);
    return ret;
}

static int wall_clock_rtc()
{
    DIR *dir = opendir(rtc_sysfs);
    if (!dir) {
        ALOGE("failed to open %s: %s", rtc_sysfs, strerror(errno));
        return -1;
    }

    struct dirent *dirent;
    while (errno = 0, dirent = readdir(dir)) {
        unsigned int rtc_id;
        int matched = sscanf(dirent->d_name, "rtc%u", &rtc_id);

        if (matched < 0)
            break;
        else if (matched != 1)
            continue;

        if (rtc_is_hctosys(rtc_id)) {
            ALOGV("found wall clock RTC %u", rtc_id);
            return rtc_id;
        }
    }

    if (errno == 0)
        ALOGW("no wall clock RTC found");
    else
        ALOGE("failed to enumerate RTCs: %s", strerror(errno));

    return -1;
}

static jlong init_timerfd()
{
    int epollfd;
    int fds[N_ANDROID_TIMERFDS];

    epollfd = epoll_create(N_ANDROID_TIMERFDS);
    if (epollfd < 0) {
        ALOGV("epoll_create(%zu) failed: %s", N_ANDROID_TIMERFDS,
                strerror(errno));
        return 0;
    }

    for (size_t i = 0; i < N_ANDROID_TIMERFDS; i++) {
        fds[i] = timerfd_create(android_alarm_to_clockid[i], 0);
        if (fds[i] < 0) {
            ALOGV("timerfd_create(%u) failed: %s",  android_alarm_to_clockid[i],
                    strerror(errno));
            close(epollfd);
            for (size_t j = 0; j < i; j++) {
                close(fds[j]);
            }
            return 0;
        }
    }

    AlarmImpl *ret = new AlarmImplTimerFd(fds, epollfd, wall_clock_rtc());

    for (size_t i = 0; i < N_ANDROID_TIMERFDS; i++) {
        epoll_event event;
        event.events = EPOLLIN | EPOLLWAKEUP;
        event.data.u32 = i;

        int err = epoll_ctl(epollfd, EPOLL_CTL_ADD, fds[i], &event);
        if (err < 0) {
            ALOGV("epoll_ctl(EPOLL_CTL_ADD) failed: %s", strerror(errno));
            delete ret;
            return 0;
        }
    }

    struct itimerspec spec;
    memset(&spec, 0, sizeof(spec));
    /* 0 = disarmed; the timerfd doesn't need to be armed to get
       RTC change notifications, just set up as cancelable */

    int err = timerfd_settime(fds[ANDROID_ALARM_TYPE_COUNT],
            TFD_TIMER_ABSTIME | TFD_TIMER_CANCEL_ON_SET, &spec, NULL);
    if (err < 0) {
        ALOGV("timerfd_settime() failed: %s", strerror(errno));
        delete ret;
        return 0;
    }

    return reinterpret_cast<jlong>(ret);
}

const char* server_list[] = {NTP_SERVER0, NTP_SERVER1, NTP_SERVER2, NTP_SERVER3,
                       NTP_SERVER4, NTP_SERVER5, NTP_SERVER6, NTP_SERVER7,
                       NTP_SERVER8, NTP_SERVER9, NTP_SERVER10, NTP_SERVER11,
                       NTP_SERVER12, NTP_SERVER13, NTP_SERVER14, NTP_SERVER15};

int createNTPClientSockfd() {
    int sockfd;
    int addr_len;
    struct sockaddr_in addr_src;
    int ret;

    addr_len = sizeof(struct sockaddr_in);
    memset(&addr_src, 0, addr_len);
    addr_src.sin_family = AF_INET;
    addr_src.sin_addr.s_addr = htonl(INADDR_ANY);
    addr_src.sin_port = htons(0);
    /* create socket. */
    if (-1 == (sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP))) {
        ALOGE("drm_ntp: create socket error! %s", strerror(errno));
        return -1;
    }
    ALOGD("drm_ntp: CreateNtpClientSockfd sockfd=%d\n", sockfd);
    return sockfd;
}

int connectNTPServer(int sockfd, char * serverAddr, int serverPort, struct sockaddr_in * ServerSocket_in) {

    struct addrinfo hints;
    struct addrinfo* result = 0;
    struct addrinfo* iter = 0;
    int ret;
    bzero(&hints, sizeof(struct addrinfo));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_CANONNAME;
    hints.ai_protocol = 0;
    ret = getaddrinfo((const char*)serverAddr, 0, &hints, &result);
    if (ret != 0) {
        ALOGE("drm_ntp: get hostname %s address info failed! %s", serverAddr, gai_strerror(ret));
        return -1;
    }

    char host[1025] = "";
    for (iter = result; iter != 0; iter = iter->ai_next) {
        ret = getnameinfo(result->ai_addr, result->ai_addrlen, host, sizeof(host), 0, 0, NI_NUMERICHOST);
        if (ret != 0) {
            ALOGE("drm_ntp: get hostname %s name info failed! %s", serverAddr, gai_strerror(ret));
            continue;
        } else {
            ALOGD("drm_ntp: hostname %s -> ip %s", serverAddr, host);
            struct sockaddr_in addr_dst;
            int addr_len;
            addr_len = sizeof(struct sockaddr_in);
            memset(&addr_dst, 0, addr_len);
            addr_dst.sin_family = AF_INET;
            addr_dst.sin_addr.s_addr = inet_addr(host);
            addr_dst.sin_port = htons(serverPort);
            memcpy(ServerSocket_in, &addr_dst, sizeof(struct sockaddr_in));

            /* connect to ntp server. */
            ALOGE("drm_ntp: try to connect ntp server %s", serverAddr);
            ret = connect(sockfd, (struct sockaddr*) &addr_dst, addr_len);
            if (-1 == ret) {
                ALOGE("drm_ntp: connect to ntp server %s failed, %s", serverAddr, strerror(errno));
                continue;
            } else {
                ALOGD("drm_ntp: ConnectNtpServer sucessful!\n");
                break;
            }
        }
    }

    if (result) freeaddrinfo(result);
        return sockfd;
}

void sendQueryTimePacked(int sockfd) {
    NTPPACKED SynNtpPacked;
    struct timeval now;
    time_t timer;
    memset(&SynNtpPacked, 0, sizeof(SynNtpPacked));

    SynNtpPacked.header.local_precision = -6;
    SynNtpPacked.header.Poll = 4;
    SynNtpPacked.header.stratum = 0;
    SynNtpPacked.header.Mode = 3;
    SynNtpPacked.header.VN = 3;
    SynNtpPacked.header.LI = 0;

    SynNtpPacked.root_delay = 1 << 16; /* Root Delay (seconds) */
    SynNtpPacked.root_dispersion = 1 << 16; /* Root Dispersion (seconds) */

    SynNtpPacked.header.headData = htonl((SynNtpPacked.header.LI << 30) | (SynNtpPacked.header.VN << 27) |
                                         (SynNtpPacked.header.Mode << 24)| (SynNtpPacked.header.stratum << 16) |
                                         (SynNtpPacked.header.Poll << 8) | (SynNtpPacked.header.local_precision & 0xff));
    SynNtpPacked.root_delay = htonl(SynNtpPacked.root_dispersion);
    SynNtpPacked.root_dispersion = htonl(SynNtpPacked.root_dispersion);

    long tmp = 0;
    time(&timer);
    SynNtpPacked.trantime.coarse = htonl(JAN_1970 + (long)timer);
    SynNtpPacked.trantime.fine = htonl((long)NTPFRAC(timer));

    send(sockfd, &SynNtpPacked, sizeof(SynNtpPacked), 0);
}

int recvNTPPacked(int sockfd, PNTPPACKED pSynNtpPacked, struct sockaddr_in * ServerSocket_in) {
    int receivebytes = -1;
    socklen_t addr_len = sizeof(struct sockaddr_in);
    fd_set sockset;

    FD_ZERO(&sockset);
    FD_SET(sockfd, &sockset);
    struct timeval blocktime = {NTP_RECV_TIMEOUT, 0};

    /* recv ntp server's response. */
    if (select(sockfd+1, &sockset, 0, 0, &blocktime) > 0) {
        receivebytes = recvfrom(sockfd, pSynNtpPacked, sizeof(NTPPACKED), 0,(struct sockaddr *) ServerSocket_in, &addr_len);
        if (-1 == receivebytes) {
            ALOGE("drm_ntp: recvfrom error! %s", strerror(errno));
            return -1;
        } else {
            ALOGD("drm_ntp: recvfrom receivebytes=%d",receivebytes);
        }
    } else {
        ALOGE("drm_ntp: recvfrom timeout! %s", strerror(errno));
    }
    return receivebytes;
}

static void startPollingNTPTime() {
    char buffer[1024];
    int i = 0;
    int list_num = sizeof(server_list)/sizeof(server_list[0]);
    int ret = -1;
    int sockfd = -1;

    do {
        sockfd = createNTPClientSockfd();
        if (sockfd == -1) {
            ALOGE("drm_ntp: create socket failed");
            continue;
        }
        struct sockaddr_in ServerSocketn;
        ret= connectNTPServer(sockfd, (char *)server_list[i++%list_num], NTP_PORT, &ServerSocketn);
        if (ret == -1) {
            close(sockfd);
            srand(i);
            int y = random(6);
            sleep(y);
             ALOGE("drm_ntp: sleep %ds and reconnect...", y);
            continue;
        }

        /* send ntp protocol packet. */
        sendQueryTimePacked(sockfd);

        NTPPACKED syn_ntp_packed;
        ret = recvNTPPacked(sockfd,&syn_ntp_packed,&ServerSocketn);
        if (ret == -1) {
            ALOGE("drm_ntp: recv from ntp server failed");
                close(sockfd);
                continue;
        }

        NTPTIME trantime;
        time64_t systemTime;
        trantime.coarse = ntohl(syn_ntp_packed.trantime.coarse) - JAN_1970;
        pthread_mutex_lock(&_mutex);
        systemTime = time(NULL);
        _delta = trantime.coarse - systemTime;
        firstConnectNetwork = 1;
        ntp_property_set(_delta);
        pthread_mutex_unlock(&_mutex);
        ALOGD("drm_ntp: _delta:%lld, trantime.coarse:%d, time(NULL):%lld", _delta, trantime.coarse, systemTime);

        close(sockfd);
        ALOGD("drm_ntp: quit polling NTP time!");
        break;
    } while (i < NTP_CONNECT_MAX_TIME);
    if(i >= NTP_CONNECT_MAX_TIME)
        ALOGE("drm_ntp: query ntp failed!");
}

static void android_server_AlarmManagerService_pollingNTPTime() {

    pthread_t tid;
    _delta = ntp_property_get();
    ALOGD("drm_ntp, pollingNTPTime, _delta: %lld.", _delta);
    if (_delta == 1LL<<63) {  // not synced with ntp yet
        if(pthread_create(&tid,NULL,(void*(*)(void*))(&startPollingNTPTime),NULL)) {
            ALOGE("drm_ntp, pthread_create (%s)\n", strerror(errno));
        }
    } else {
        firstConnectNetwork = 1;
    }
}

static jlong android_server_AlarmManagerService_init(JNIEnv*, jobject)
{    
    jlong ret = init_alarm_driver();
    if (ret) {
        return ret;
    }

    return init_timerfd();
}

static void android_server_AlarmManagerService_close(JNIEnv*, jobject, jlong nativeData)
{
    AlarmImpl *impl = reinterpret_cast<AlarmImpl *>(nativeData);
    delete impl;
}

static void android_server_AlarmManagerService_set(JNIEnv*, jobject, jlong nativeData, jint type, jlong seconds, jlong nanoseconds)
{
    AlarmImpl *impl = reinterpret_cast<AlarmImpl *>(nativeData);
    struct timespec ts;
    ts.tv_sec = seconds;
    ts.tv_nsec = nanoseconds;

    ALOGD("set alarm to kernel: %lld.%09lld, type=%d \n", seconds, nanoseconds, type);
    int result = impl->set(type, &ts);
    if (result < 0)
    {
        ALOGE("Unable to set alarm to %lld.%09lld: %s\n",
              static_cast<long long>(seconds),
              static_cast<long long>(nanoseconds), strerror(errno));
    }
}

static jint android_server_AlarmManagerService_waitForAlarm(JNIEnv*, jobject, jlong nativeData)
{
    AlarmImpl *impl = reinterpret_cast<AlarmImpl *>(nativeData);
    int result = 0;

    do
    {
        result = impl->waitForAlarm();
    } while (result < 0 && errno == EINTR);

    if (result < 0)
    {
        ALOGE("Unable to wait on alarm: %s\n", strerror(errno));
        return 0;
    }

    return result;
}

/**
 * SPRD: Regular PowerOnOff Feature @{
 * clear the power alarm.
 */
static void android_server_AlarmManagerService_clear(JNIEnv* env, jobject obj, jlong nativeData, jint type)
{
    AlarmImpl *impl = reinterpret_cast<AlarmImpl *>(nativeData);

    int result = impl->clear(type);
    if (result < 0)
    {
        ALOGE("Unable to clear alarm to  %d \n", result);
    }

}
/* @} */

static JNINativeMethod sMethods[] = {
     /* name, signature, funcPtr */
    {"pollingNTPTime", "()V", (void*)android_server_AlarmManagerService_pollingNTPTime},
    {"init", "()J", (void*)android_server_AlarmManagerService_init},
    {"close", "(J)V", (void*)android_server_AlarmManagerService_close},
    {"set", "(JIJJ)V", (void*)android_server_AlarmManagerService_set},
    {"waitForAlarm", "(J)I", (void*)android_server_AlarmManagerService_waitForAlarm},
    {"setKernelTime", "(JJ)I", (void*)android_server_AlarmManagerService_setKernelTime},
    {"setKernelTimezone", "(JI)I", (void*)android_server_AlarmManagerService_setKernelTimezone},
    /* SPRD: Regular PowerOnOff Feature @{ */
    {"clear", "(JI)V", (void*)android_server_AlarmManagerService_clear},
    /* @} */
};

int register_android_server_AlarmManagerService(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/AlarmManagerService",
                                    sMethods, NELEM(sMethods));
}

} /* namespace android */
