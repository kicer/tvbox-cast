/* 最小 android/log.h 假头，仅供语法自检。 */
#ifndef FAKE_ANDROID_LOG_H
#define FAKE_ANDROID_LOG_H

#define ANDROID_LOG_INFO 4

int __android_log_print(int prio, const char *tag, const char *fmt, ...);

#endif
