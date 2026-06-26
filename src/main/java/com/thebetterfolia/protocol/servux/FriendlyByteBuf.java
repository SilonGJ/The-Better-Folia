package com.thebetterfolia.protocol.servux;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class FriendlyByteBuf {
    private final ByteBuf buf;

    public FriendlyByteBuf(ByteBuf buf) {
        this.buf = buf;
    }

    public FriendlyByteBuf() {
        this.buf = Unpooled.buffer();
    }

    public void writeVarInt(int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                this.buf.writeByte(value);
                return;
            }
            this.buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    public int readVarInt() {
        int i = 0;
        int j = 0;
        while (true) {
            byte b = this.buf.readByte();
            i |= (b & 0x7F) << j * 7;
            if ((b & 0x80) != 128) {
                return i;
            }
            ++j;
            if (j > 5) {
                throw new RuntimeException("VarInt too long");
            }
        }
    }

    public void writeBytes(byte[] bytes) {
        this.buf.writeBytes(bytes);
    }

    public void writeBytes(ByteBuf other, int length) {
        this.buf.writeBytes(other, length);
    }

    public void writeBytes(FriendlyByteBuf other, int length) {
        this.buf.writeBytes(other.buf, length);
    }

    public byte[] readBytes(int length) {
        byte[] bytes = new byte[length];
        this.buf.readBytes(bytes);
        return bytes;
    }

    public int writerIndex() {
        return this.buf.writerIndex();
    }

    public int readerIndex() {
        return this.buf.readerIndex();
    }

    public void readerIndex(int index) {
        this.buf.readerIndex(index);
    }

    public void resetReaderIndex() {
        this.buf.readerIndex(0);
    }

    public int readableBytes() {
        return this.buf.readableBytes();
    }

    public boolean isReadable() {
        return this.buf.isReadable();
    }

    public ByteBuf getByteBuf() {
        return this.buf;
    }

    public void release() {
        this.buf.release();
    }

    public FriendlyByteBuf copy() {
        return new FriendlyByteBuf(this.buf.copy());
    }
}
