package com.thebetterfolia.protocol.servux;

import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

public class PacketSplitter {
    public static final int MAX_TOTAL_PER_PACKET_S2C = 1048576;
    public static final int MAX_PAYLOAD_PER_PACKET_S2C = MAX_TOTAL_PER_PACKET_S2C - 5;
    public static final int MAX_TOTAL_PER_PACKET_C2S = 32767;
    public static final int MAX_PAYLOAD_PER_PACKET_C2S = MAX_TOTAL_PER_PACKET_C2S - 5;
    public static final int DEFAULT_MAX_RECEIVE_SIZE_C2S = 16777216;
    public static final int DEFAULT_MAX_RECEIVE_SIZE_S2C = 67108864;

    private static final Map<Long, ReadingSession> READING_SESSIONS = new HashMap<>();

    public static FriendlyByteBuf receive(long key, FriendlyByteBuf buf) {
        return receive(key, buf, DEFAULT_MAX_RECEIVE_SIZE_S2C);
    }

    @Nullable
    public static FriendlyByteBuf receive(long key, FriendlyByteBuf buf, int maxLength) {
        return READING_SESSIONS.computeIfAbsent(key, ReadingSession::new).receive(buf, maxLength);
    }

    private static class ReadingSession {
        private final long key;
        private int expectedSize = -1;
        private FriendlyByteBuf received;

        private ReadingSession(long key) {
            this.key = key;
        }

        @Nullable
        private FriendlyByteBuf receive(FriendlyByteBuf data, int maxLength) {
            data.resetReaderIndex();

            if (this.expectedSize < 0) {
                this.expectedSize = data.readVarInt();

                if (this.expectedSize > maxLength) {
                    throw new IllegalArgumentException("Payload too large");
                }

                this.received = new FriendlyByteBuf(Unpooled.buffer(this.expectedSize));
            }

            if (this.received == null) {
                throw new RuntimeException("Receive Buffer is empty");
            }

            this.received.writeBytes(data.copy().getByteBuf(), data.readableBytes());

            if (this.received.writerIndex() >= this.expectedSize) {
                READING_SESSIONS.remove(this.key);
                this.received.resetReaderIndex();
                return this.received;
            }

            return null;
        }
    }
}
