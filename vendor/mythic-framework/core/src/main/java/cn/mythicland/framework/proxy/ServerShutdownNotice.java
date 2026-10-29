package cn.mythicland.framework.proxy;

public final class ServerShutdownNotice {
    public static final String CHANNEL = "mythic:shutdown";

    private ServerShutdownNotice() {
    }

    public static byte[] encode() {
        return new byte[]{1};
    }

    public static boolean matches(byte[] payload) {
        return payload.length == 1 && payload[0] == 1;
    }
}
