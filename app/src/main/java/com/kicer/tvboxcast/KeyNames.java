package com.kicer.tvboxcast;

/**
 * Android keyCode -> 可读名字。
 * 不用 KeyEvent.keyCodeToString()，因为 Android 4.4 上部分常量/方法不可靠。
 */
public final class KeyNames {

    private KeyNames() {
    }

    public static String name(int code) {
        switch (code) {
            // 遥控器实测相关
            case 3:
                return "HOME(主页)";
            case 4:
                return "BACK(返回)";
            case 7:
                return "0";
            case 8:
                return "1";
            case 9:
                return "2";
            case 10:
                return "3";
            case 11:
                return "4";
            case 12:
                return "5";
            case 13:
                return "6";
            case 14:
                return "7";
            case 15:
                return "8";
            case 16:
                return "9";
            case 19:
                return "UP(上)";
            case 20:
                return "DOWN(下)";
            case 21:
                return "LEFT(左)";
            case 22:
                return "RIGHT(右)";
            case 23:
                return "DPAD_CENTER";
            case 24:
                return "VOLUME_UP(音量+)";
            case 25:
                return "VOLUME_DOWN(音量-)";
            case 66:
                return "ENTER(OK)";
            case 67:
                return "DEL";
            case 82:
                return "MENU(菜单)";
            case 84:
                return "SEARCH";
            case 85:
                return "MEDIA_PLAY_PAUSE";
            case 86:
                return "MEDIA_STOP";
            case 87:
                return "MEDIA_NEXT";
            case 88:
                return "MEDIA_PREVIOUS";
            case 89:
                return "MEDIA_REWIND";
            case 90:
                return "MEDIA_FAST_FORWARD";
            case 91:
                return "MUTE(静音)";
            case 92:
                return "PAGE_UP";
            case 93:
                return "PAGE_DOWN";
            case 122:
                return "MEDIA_PLAY";
            case 123:
                return "MEDIA_PAUSE";
            case 126:
                return "MEDIA_PLAY2";
            case 127:
                return "MEDIA_PAUSE2";
            case 130:
                return "RECORD(回看)";
            case 131:
                return "F1";
            case 132:
                return "F2(直播)";
            case 133:
                return "F3";
            case 134:
                return "F4";
            case 164:
                return "VOLUME_MUTE";
            case 165:
                return "INFO";
            case 166:
                return "CHANNEL_UP(频道+)";
            case 167:
                return "CHANNEL_DOWN(频道-)";
            case 168:
                return "ZOOM";
            case 169:
                return "TV";
            default:
                return "KEY_" + code;
        }
    }
}
