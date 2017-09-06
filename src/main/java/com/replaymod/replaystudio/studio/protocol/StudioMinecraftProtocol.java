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
package com.replaymod.replaystudio.studio.protocol;

import com.replaymod.replaystudio.Studio;
import com.replaymod.replaystudio.io.WrappedPacket;
import com.replaymod.replaystudio.util.Reflection;
import org.spacehq.mc.protocol.MinecraftProtocol;
import org.spacehq.mc.protocol.ProtocolMode;
import org.spacehq.packetlib.Session;
import org.spacehq.packetlib.packet.Packet;
import org.spacehq.packetlib.packet.PacketProtocol;

import java.util.HashMap;

public class StudioMinecraftProtocol extends MinecraftProtocol {

    public StudioMinecraftProtocol() {
        super(ProtocolMode.LOGIN);
    }

    public StudioMinecraftProtocol(Studio studio, Session session, boolean client) {
        super(ProtocolMode.LOGIN);

        init(studio, session, client, ProtocolMode.GAME);
    }

    public void init(Studio studio, Session session, boolean client, ProtocolMode mode) {
        Reflection.setField(PacketProtocol.class, "incoming", this, new HashMap() {
            @Override
            @SuppressWarnings("unchecked")
            public Object get(Object key) {
                Class<? extends Packet> value = (Class<? extends Packet>) super.get(key);
                return getPacketClass(studio, value);
            }
        });

        Reflection.setField(PacketProtocol.class, "outgoing", this, new HashMap() {
            @Override
            public boolean containsKey(Object key) {
                return get(key) != null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Object get(Object key) {
                if (!(key instanceof Class)) {
                    return super.get(key);
                }
                return super.get(WrappedPacket.getWrappedClassFor((Class) key));
            }
        });

        setMode(mode, client, session);
    }

    @Override
    public void setMode(ProtocolMode mode, boolean client, Session session) {
        super.setMode(mode, client, session);
    }

    private Class<?> getPacketClass(Studio studio, Class<? extends Packet> cls) {
        if (studio.isWrappingEnabled() && !studio.willBeParsed(cls)) {
            return WrappedPacket.getClassFor(cls);
        } else {
            return cls;
        }
    }

}
