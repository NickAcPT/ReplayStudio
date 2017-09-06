/*
 * This file is part of ReplayStudio, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 johni0702 <https://github.com/johni0702>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.replaymod.replaystudio.io;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.Studio;
import com.replaymod.replaystudio.collection.PacketList;
import com.replaymod.replaystudio.studio.protocol.StudioCodec;
import com.replaymod.replaystudio.studio.protocol.StudioSession;
import org.spacehq.mc.protocol.ProtocolMode;
import org.spacehq.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import org.spacehq.mc.protocol.packet.login.server.LoginSuccessPacket;
import org.spacehq.netty.buffer.ByteBuf;
import org.spacehq.netty.buffer.ByteBufAllocator;
import org.spacehq.netty.buffer.PooledByteBufAllocator;
import org.spacehq.packetlib.packet.Packet;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import static com.replaymod.replaystudio.util.Utils.readInt;

/**
 * Input stream for reading packet data.
 */
public class ReplayInputStream extends InputStream {

    private static final ByteBufAllocator ALLOC = PooledByteBufAllocator.DEFAULT;

    /**
     * The actual input stream.
     */
    private final InputStream in;

    /**
     * The studio session.
     */
    private final StudioSession session;

    /**
     * The studio codec.
     */
    private final StudioCodec codec;

    /**
     * Creates a new replay input stream for reading raw packet data.
     * @param studio The studio
     * @param in The actual input stream.
     */
    public ReplayInputStream(Studio studio, InputStream in) {
        this.session = new StudioSession(studio, true);
        this.codec = new StudioCodec(session);
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /**
     * Read the next packet from this input stream.
     * @return The packet
     * @throws IOException if an I/O error occurs.
     */
    public PacketData readPacket() throws IOException {
        while (true) {
            int next = readInt(in);
            int length = readInt(in);
            if (next == -1 || length == -1) {
                break; // reached end of stream
            }
            if (length == 0) {
                continue; // skip empty segments
            }

            ByteBuf buf = ALLOC.buffer(length);
            while (length > 0) {
                int read = buf.writeBytes(in, length);
                if (read == -1) {
                    throw new EOFException();
                }
                length -= read;
            }

            List<Object> decoded = new LinkedList<>();
            try {
                codec.decode(null, buf, decoded);
            } catch (Exception e) {
                throw e instanceof IOException ? (IOException) e : new IOException("decoding", e);
            }
            buf.release();

            for (Object o : decoded) {
                if (o instanceof ServerKeepAlivePacket) {
                    continue; // They aren't needed in a replay
                }

                if (o instanceof LoginSuccessPacket) {
                    session.getPacketProtocol().setMode(ProtocolMode.GAME, true, session);
                }
                return new PacketData(next, (Packet) o);
            }
        }
        return null;
    }

    /**
     * Reads all packets from the specified input stream into a new packet list.
     * The input stream is closed if no more packets can be read.
     * @param studio The studio
     * @param in The input stream to read from
     * @return The packet list
     */
    public static PacketList readPackets(Studio studio, InputStream in) throws IOException {
        ReplayInputStream replayIn;
        if (in instanceof ReplayInputStream) {
            replayIn = (ReplayInputStream) in;
        } else {
            replayIn = new ReplayInputStream(studio, in);
        }
        List<PacketData> packets = new LinkedList<>();

        PacketData data;
        while ((data = replayIn.readPacket()) != null) {
            packets.add(data);
        }

        in.close();

        return new PacketList(packets);
    }

}
