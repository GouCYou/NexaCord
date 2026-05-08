package cn.cctstudio.nexacord.exception;

public class SessionReplacedException extends RuntimeException {
    private final String deviceName;

    public SessionReplacedException(String deviceName) {
        super("你的账号已在其他设备登录。");
        this.deviceName = deviceName;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
