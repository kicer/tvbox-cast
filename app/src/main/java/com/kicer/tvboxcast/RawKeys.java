package com.kicer.tvboxcast;

/**
 * Linux 输入码（ev.code / KEY_xxx）与 Android keyCode 的对照。
 *
 * 独占 event0 之后拿到的是 Linux 码，和 Android 的 keyCode 不是一套东西，
 * 必须显式转换才能复用同一套动作处理。
 */
public final class RawKeys {

    private RawKeys() {
    }

    public static String name(int c) {
        // Linux: KEY_1..KEY_9 = 2..10, KEY_0 = 11
        if (c >= 2 && c <= 10) {
            return String.valueOf(c - 1);
        }
        switch (c) {
            case 11:
                return "0";
            case 14:
                return "BACKSPACE";
            case 15:
                return "TAB";
            case 28:
                return "ENTER(OK)";
            case 59:
                return "F1";
            case 60:
                return "F2(直播)";
            case 61:
                return "F3";
            case 62:
                return "F4";
            case 102:
                return "HOME(主页)";
            case 103:
                return "UP(上)";
            case 104:
                return "PAGEUP";
            case 105:
                return "LEFT(左)";
            case 106:
                return "RIGHT(右)";
            case 107:
                return "END";
            case 108:
                return "DOWN(下)";
            case 109:
                return "PAGEDOWN";
            case 113:
                return "MUTE(静音)";
            case 114:
                return "VOLUME_DOWN(音量-)";
            case 115:
                return "VOLUME_UP(音量+)";
            case 116:
                return "POWER";
            case 119:
                return "PAUSE";
            case 128:
                return "STOP";
            case 139:
                return "MENU(菜单)";
            case 158:
                return "BACK(返回)";
            case 163:
                return "NEXT";
            case 164:
                return "PLAYPAUSE";
            case 165:
                return "PREVIOUS";
            case 167:
                return "RECORD(回看)";
            case 168:
                return "REWIND";
            case 207:
                return "PLAY";
            case 208:
                return "FASTFORWARD";
            default:
                return "RAW_" + c;
        }
    }

    /** Linux 码 -> Android keyCode；未知返回 -1 */
    public static int toAndroid(int c) {
        if (c >= 2 && c <= 10) {
            return c + 6; // KEY_1..KEY_9 -> 8..16
        }
        switch (c) {
            case 11:
                return 7; // KEY_0
            case 28:
                return 66; // ENTER
            case 158:
                return 4; // BACK
            case 139:
                return 82; // MENU
            case 103:
                return 19; // UP
            case 108:
                return 20; // DOWN
            case 105:
                return 21; // LEFT
            case 106:
                return 22; // RIGHT
            case 115:
                return 24; // VOLUME_UP
            case 114:
                return 25; // VOLUME_DOWN
            case 113:
                return 91; // MUTE
            case 102:
                return 3; // HOME
            case 59:
                return 131; // F1
            case 60:
                return 132; // F2 直播
            case 167:
                return 130; // RECORD 回看
            case 164:
                return 85; // PLAY_PAUSE
            case 168:
                return 89; // REWIND
            case 208:
                return 90; // FAST_FORWARD
            default:
                return -1;
        }
    }
}
