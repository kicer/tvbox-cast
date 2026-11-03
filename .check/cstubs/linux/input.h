/* 最小 linux/input.h 假头，仅供语法自检（字段与真实定义一致）。 */
#ifndef FAKE_LINUX_INPUT_H
#define FAKE_LINUX_INPUT_H

#include <sys/time.h>
#include <sys/ioctl.h>

struct input_event {
    struct timeval time;
    unsigned short type;
    unsigned short code;
    int value;
};

#define EV_SYN 0x00
#define EV_KEY 0x01

#ifndef _IOC_READ
#define _IOC_READ 2
#endif

#define EVIOCGRAB _IOW('E', 0x90, int)
#define EVIOCGNAME(len) _IOC(_IOC_READ, 'E', 0x06, len)

#endif
