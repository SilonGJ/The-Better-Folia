package com.thebetterfolia.protocol.servux;

public class ServuxBase {

    public static final String PROTOCOL_ID = "servux";
    public static final String SERVUX_VERSION = "servux-thebetterfolia-1.0.0";
    public static final int DATA_PROTOCOL_VERSION = 1;
    public static final int HUD_PROTOCOL_VERSION = 2;
    public static final int STRUCTURES_PROTOCOL_VERSION = 2;

    public static final int MAX_DELAY_DEFAULT = 200;
    public static final int HUD_UPDATE_INTERVAL = 5;

    public enum PacketType {
        METADATA(1),
        METADATA_REQUEST(2),
        DATA(3),
        DATA_START(10),
        DATA_CONTINUE(11);

        public final int type;
        PacketType(int type) {
            this.type = type;
        }
    }
}