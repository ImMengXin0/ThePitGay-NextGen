package cn.mythicland.framework.component;

public final class LoadPhase {
    public static final int CONFIGURATION = 0;
    public static final int DATA = 1;
    public static final int SERVICES = 2;
    public static final int ADAPTERS = 3;

    private LoadPhase() {
    }
}
