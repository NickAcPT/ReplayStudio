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
package com.replaymod.replaystudio.filter;

import com.google.gson.JsonObject;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.Studio;
import com.replaymod.replaystudio.stream.PacketStream;
import com.replaymod.replaystudio.util.Location;
import com.replaymod.replaystudio.util.PacketUtils;
import com.replaymod.replaystudio.util.Utils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.spacehq.mc.protocol.data.game.BlockChangeRecord;
import org.spacehq.mc.protocol.data.game.Chunk;
import org.spacehq.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import org.spacehq.mc.protocol.packet.ingame.server.ServerJoinGamePacket.Difficulty;
import org.spacehq.mc.protocol.packet.ingame.server.ServerJoinGamePacket.GameMode;
import org.spacehq.mc.protocol.packet.ingame.server.ServerJoinGamePacket.WorldType;
import org.spacehq.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.ServerDestroyEntitiesPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.ServerEntityMovementPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.ServerEntityPositionPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.ServerEntityPositionRotationPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.ServerEntityRotationPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.ServerEntityTeleportPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.player.ServerPlayerAbilitiesPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.player.ServerSetExperiencePacket;
import org.spacehq.mc.protocol.packet.ingame.server.scoreboard.ServerTeamPacket;
import org.spacehq.mc.protocol.packet.ingame.server.scoreboard.ServerTeamPacket.FriendlyFireMode;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerConfirmTransactionPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerWindowPropertyPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerBlockBreakAnimPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerBlockChangePacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerBlockValuePacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerExplosionPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerMapDataPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerMultiBlockChangePacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerMultiChunkDataPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerOpenTileEntityEditorPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerPlayEffectPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerPlaySoundPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerSpawnParticlePacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerUpdateSignPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerUpdateTileEntityPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerUpdateTimePacket;
import org.spacehq.packetlib.packet.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.replaymod.replaystudio.io.WrappedPacket.instanceOf;
import static com.replaymod.replaystudio.util.Java8.Map8.getOrCreate;
import static com.replaymod.replaystudio.util.Utils.within;

public class SquashFilter extends StreamFilterBase {

    private static final long POS_MIN = Byte.MIN_VALUE;
    private static final long POS_MAX = Byte.MAX_VALUE;

    private static class Team {
        private enum Status {
            CREATED, UPDATED, REMOVED
        }

        private final Status status;

        private String name;
        private String displayName;
        private String prefix;
        private String suffix;
        private FriendlyFireMode friendlyFire;
        private final Set<String> added = new HashSet<>();
        private final Set<String> removed = new HashSet<>();

        public Team(Status status) {
            this.status = status;
        }
    }

    private static class Entity {
        private List<PacketData> packets = new ArrayList<>();
        private long lastTimestamp = 0;
        private Location loc = null;
        private long dx = 0;
        private long dy = 0;
        private long dz = 0;
        private Float yaw = null;
        private Float pitch = null;
    }

    private final List<PacketData> unhandled = new ArrayList<>();
    private final Map<Integer, Entity> entities = new HashMap<>();
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<Integer, PacketData> mainInventoryChanges = new HashMap<>();
    private final Map<Integer, ServerMapDataPacket> maps = new HashMap<>();

    private final List<PacketData> currentWorld = new ArrayList<>();
    private final List<BlockChangeRecord> currentBlocks = new ArrayList<>();
    private final List<PacketData> currentWindow = new ArrayList<>();
    private final List<PacketData> closeWindows = new ArrayList<>();

    private final Map<Long, ChunkData> chunks = new HashMap<>();
    private final Map<Long, Long> unloadedChunks = new HashMap<>();

    private long lastTimestamp;

    private GameMode gameMode = null;
    private Integer dimension = null;
    private Difficulty difficulty = null;
    private WorldType worldType = null;
    private PacketData joinGame;
    private PacketData respawn;
    private PacketData mainInventory;
    private ServerSetExperiencePacket experience = null;
    private ServerPlayerAbilitiesPacket abilities = null;

    @Override
    public void onStart(PacketStream stream) {

    }

    @Override
    public boolean onPacket(PacketStream stream, PacketData data) {
        Packet packet = data.getPacket();
        lastTimestamp = data.getTime();

        if (instanceOf(packet, ServerSpawnParticlePacket.class)) {
            return false;
        }

        // Entities
        Integer entityId = PacketUtils.getEntityId(packet);
        if (entityId != null) { // Some entity is associated with this packet
            if (entityId == -1) { // Multiple entities in fact
                for (int id : PacketUtils.getEntityIds(packet)) {
                    if (packet instanceof ServerDestroyEntitiesPacket) {
                        entities.remove(id);
                    } else {
                        getOrCreate(entities, id, Entity::new).packets.add(data);
                    }
                }
            } else { // Only one entity
                Entity entity = getOrCreate(entities, entityId, Entity::new);
                if (packet instanceof ServerEntityMovementPacket) {
                    ServerEntityMovementPacket p = (ServerEntityMovementPacket) packet;
                    double mx = p.getMovementX();
                    double my = p.getMovementY();
                    double mz = p.getMovementZ();

                    if (p instanceof ServerEntityPositionPacket || p instanceof ServerEntityPositionRotationPacket) {
                        entity.dx += mx * 32;
                        entity.dy += my * 32;
                        entity.dz += mz * 32;
                    }
                    if (p instanceof ServerEntityRotationPacket || p instanceof ServerEntityPositionRotationPacket) {
                        entity.yaw = p.getYaw();
                        entity.pitch = p.getPitch();
                    }
                } else if (packet instanceof ServerEntityTeleportPacket) {
                    ServerEntityTeleportPacket p = (ServerEntityTeleportPacket) packet;
                    entity.loc = Location.from(p);
                    entity.dx = entity.dy = entity.dz = 0;
                    entity.yaw = entity.pitch = null;
                } else {
                    entity.packets.add(data);
                }
                entity.lastTimestamp = lastTimestamp;
            }
            return false;
        }

        // World
        if (packet instanceof ServerNotifyClientPacket) {
            ServerNotifyClientPacket p = (ServerNotifyClientPacket) packet;
            if (p.getNotification() == ServerNotifyClientPacket.Notification.CHANGE_GAMEMODE) {
                gameMode = GameMode.valueOf(p.getValue().toString());
                return false;
            }
        }

        if (packet instanceof ServerSetExperiencePacket) {
            experience = (ServerSetExperiencePacket) packet;
            return false;
        }

        if (packet instanceof ServerPlayerAbilitiesPacket) {
            abilities = (ServerPlayerAbilitiesPacket) packet;
            return false;
        }

        if (packet instanceof ServerJoinGamePacket) {
            ServerJoinGamePacket p = (ServerJoinGamePacket) packet;
            gameMode = p.getGameMode();
            dimension = p.getDimension();
            difficulty = p.getDifficulty();
            worldType = p.getWorldType();
            joinGame = data;
            return false;
        }

        if (packet instanceof ServerRespawnPacket) {
            ServerRespawnPacket p = (ServerRespawnPacket) packet;
            dimension = p.getDimension();
            difficulty = Difficulty.valueOf(p.getDifficulty().toString());
            worldType = WorldType.valueOf(p.getWorldType().toString());
            gameMode = GameMode.valueOf(p.getGameMode().toString());
            currentWorld.clear();
            chunks.clear();
            unloadedChunks.clear();
            currentBlocks.clear();
            currentWindow.clear();
            entities.clear();
            respawn = data;
            return false;
        }

        if (packet instanceof ServerChunkDataPacket) {
            ServerChunkDataPacket p = (ServerChunkDataPacket) packet;
            updateChunk(data.getTime(), p.getX(), p.getZ(), p.getChunks(), p.getBiomeData());
            return false;
        }
        if (packet instanceof ServerMultiChunkDataPacket) {
            ServerMultiChunkDataPacket p = (ServerMultiChunkDataPacket) packet;
            for (int i = 0; i < p.getColumns(); i++) {
                updateChunk(data.getTime(), p.getX(i), p.getZ(i), p.getChunks(i), p.getBiomeData(i));
            }
            return false;
        }

        if (packet instanceof ServerBlockChangePacket) {
            updateBlock(data.getTime(), ((ServerBlockChangePacket) packet).getRecord());
            return false;
        }

        if (packet instanceof ServerMultiBlockChangePacket) {
            for (BlockChangeRecord record : ((ServerMultiBlockChangePacket) packet).getRecords()) {
                updateBlock(data.getTime(), record);
            }
            return false;
        }

        if (instanceOf(packet, ServerPlayerPositionRotationPacket.class)
                || instanceOf(packet, ServerRespawnPacket.class)
                || instanceOf(packet, ServerBlockBreakAnimPacket.class)
                || instanceOf(packet, ServerBlockChangePacket.class)
                || instanceOf(packet, ServerBlockValuePacket.class)
                || instanceOf(packet, ServerExplosionPacket.class)
                || instanceOf(packet, ServerMultiBlockChangePacket.class)
                || instanceOf(packet, ServerOpenTileEntityEditorPacket.class)
                || instanceOf(packet, ServerPlayEffectPacket.class)
                || instanceOf(packet, ServerPlaySoundPacket.class)
                || instanceOf(packet, ServerSpawnParticlePacket.class)
                || instanceOf(packet, ServerSpawnPositionPacket.class)
                || instanceOf(packet, ServerUpdateSignPacket.class)
                || instanceOf(packet, ServerUpdateTileEntityPacket.class)
                || instanceOf(packet, ServerUpdateTimePacket.class)) {
            currentWorld.add(data);
            return false;
        }

        // Windows
        if (packet instanceof ServerCloseWindowPacket) {
            currentWindow.clear();
            closeWindows.add(data);
            return false;
        }

        if (instanceOf(packet, ServerConfirmTransactionPacket.class)) {
            return false; // This packet isn't of any use in replays
        }

        if (instanceOf(packet, ServerOpenWindowPacket.class)
                || instanceOf(packet, ServerWindowPropertyPacket.class)) {
            currentWindow.add(data);
            return false;
        }

        if (packet instanceof ServerWindowItemsPacket) {
            ServerWindowItemsPacket p = (ServerWindowItemsPacket) packet;
            if (p.getWindowId() == 0) {
                mainInventory = data;
            } else {
                currentWindow.add(data);
            }
            return false;
        }

        if (packet instanceof ServerSetSlotPacket) {
            ServerSetSlotPacket p = (ServerSetSlotPacket) packet;
            if (p.getWindowId() == 0) {
                mainInventoryChanges.put(p.getSlot(), data);
            } else {
                currentWindow.add(data);
            }
            return false;
        }

        // Teams
        if (packet instanceof ServerTeamPacket) {
            ServerTeamPacket p = (ServerTeamPacket) packet;
            Team team = teams.get(p.getTeamName());
            if (team == null) {
                Team.Status status;
                if (p.getAction() == ServerTeamPacket.Action.CREATE) {
                    status = Team.Status.CREATED;
                } else if (p.getAction() == ServerTeamPacket.Action.REMOVE) {
                    status = Team.Status.REMOVED;
                } else {
                    status = Team.Status.UPDATED;
                }
                team = new Team(status);
                team.name = p.getTeamName();
                teams.put(team.name, team);
            }
            ServerTeamPacket.Action action = p.getAction();
            if (action == ServerTeamPacket.Action.REMOVE && team.status == Team.Status.CREATED) {
                teams.remove(team.name);
            }
            if (action == ServerTeamPacket.Action.CREATE || action == ServerTeamPacket.Action.UPDATE) {
                team.displayName = p.getDisplayName();
                team.prefix = p.getPrefix();
                team.suffix = p.getSuffix();
                team.friendlyFire = p.getFriendlyFire();
            }
            if (action == ServerTeamPacket.Action.ADD_PLAYER || action == ServerTeamPacket.Action.CREATE) {
                for (String player : p.getPlayers()) {
                    if (!team.removed.remove(player)) {
                        team.added.add(player);
                    }
                }
            }
            if (action == ServerTeamPacket.Action.REMOVE_PLAYER) {
                for (String player : p.getPlayers()) {
                    if (!team.added.remove(player)) {
                        team.removed.add(player);
                    }
                }
            }
            return false;
        }

        // Misc
        if (packet instanceof ServerMapDataPacket) {
            ServerMapDataPacket p = (ServerMapDataPacket) packet;
            maps.put(p.getMapId(), p);
            return false;
        }

        unhandled.add(data);
        return false;
    }

    @Override
    public void onEnd(PacketStream stream, long timestamp) {
        List<PacketData> result = new ArrayList<>();

        result.addAll(unhandled);
        result.addAll(currentWorld);
        result.addAll(currentWindow);
        result.addAll(closeWindows);
        result.addAll(mainInventoryChanges.values());

        if (mainInventory != null) {
            result.add(mainInventory);
        }

        if (joinGame != null) {
            ServerJoinGamePacket org = (ServerJoinGamePacket) joinGame.getPacket();
            Packet packet = new ServerJoinGamePacket(org.getEntityId(), org.getHardcore(), gameMode, dimension,
                    difficulty, org.getMaxPlayers(), worldType);
            result.add(new PacketData(joinGame.getTime(), packet));
        } else if (respawn != null) {
            Packet packet = new ServerRespawnPacket(dimension,
                    ServerRespawnPacket.Difficulty.valueOf(difficulty.toString()),
                    ServerRespawnPacket.GameMode.valueOf(gameMode.toString()),
                    ServerRespawnPacket.WorldType.valueOf(worldType.toString()));
            result.add(new PacketData(respawn.getTime(), packet));
        } else {
            if (gameMode != null) {
                Packet packet = new ServerNotifyClientPacket(ServerNotifyClientPacket.Notification.CHANGE_GAMEMODE,
                        ServerNotifyClientPacket.GameModeValue.valueOf(gameMode.toString()));
                result.add(new PacketData(lastTimestamp, packet));
            }
        }

        if (experience != null) {
            result.add(new PacketData(lastTimestamp, experience));
        }
        if (abilities != null) {
            result.add(new PacketData(lastTimestamp, abilities));
        }

        for (Map.Entry<Integer, Entity> e : entities.entrySet()) {
            Entity entity = e.getValue();

            FOR_PACKETS:
            for (PacketData data : entity.packets) {
                Packet packet = data.getPacket();
                Integer id = PacketUtils.getEntityId(packet);
                if (id == -1) { // Multiple entities
                    List<Integer> allIds = PacketUtils.getEntityIds(packet);
                    for (int i : allIds) {
                        if (!entities.containsKey(i)) { // Other entity doesn't exist
                            continue FOR_PACKETS;
                        }
                    }
                }
                result.add(data);
            }

            if (entity.loc != null) {
                result.add(new PacketData(entity.lastTimestamp, entity.loc.toServerEntityTeleportPacket(e.getKey())));
            }
            while (entity.dx != 0 && entity.dy != 0 && entity.dz != 0) {
                long mx = within(entity.dx, POS_MIN, POS_MAX);
                long my = within(entity.dy, POS_MIN, POS_MAX);
                long mz = within(entity.dz, POS_MIN, POS_MAX);
                entity.dx -= mx;
                entity.dy -= my;
                entity.dz -= mz;
                ServerEntityPositionPacket p = new ServerEntityPositionPacket(e.getKey(), mx / 32d, my / 32d, mz / 32d);
                result.add(new PacketData(entity.lastTimestamp, p));
            }
            if (entity.yaw != null && entity.pitch != null) {
                ServerEntityRotationPacket p = new ServerEntityRotationPacket(e.getKey(), entity.yaw, entity.pitch);
                result.add(new PacketData(entity.lastTimestamp, p));
            }
        }

        for (Map.Entry<Long, Long> e : unloadedChunks.entrySet()) {
            int x = ChunkData.longToX(e.getKey());
            int z = ChunkData.longToZ(e.getKey());
            result.add(new PacketData(e.getValue(), new ServerChunkDataPacket(x, z)));
        }

        for (ChunkData chunk : chunks.values()) {
            Packet packet = new ServerChunkDataPacket(chunk.x, chunk.z, chunk.changes, chunk.biomeData);
            result.add(new PacketData(chunk.firstAppearance, packet));
            for (Map<Short, MutablePair<Long, BlockChangeRecord>> e : chunk.blockChanges) {
                if (e != null) {
                    for (MutablePair<Long, BlockChangeRecord> pair : e.values()) {
                        result.add(new PacketData(pair.getLeft(), new ServerBlockChangePacket(pair.getRight())));
                    }
                }
            }
        }

        Collections.sort(result, (e1, e2) -> Long.compare(e1.getTime(), e2.getTime()));
        for (PacketData data : result) {
            add(stream, timestamp, data.getPacket());
        }

        for (Team team : teams.values()) {
            String[] added = team.added.toArray(new String[team.added.size()]);
            String[] removed = team.added.toArray(new String[team.removed.size()]);
            if (team.status == Team.Status.CREATED) {
                add(stream, timestamp, new ServerTeamPacket(team.name, team.displayName, team.prefix, team.suffix,
                        team.friendlyFire, added));
            } else if (team.status == Team.Status.UPDATED) {
                if (added.length > 0) {
                    add(stream, timestamp, new ServerTeamPacket(team.name, ServerTeamPacket.Action.ADD_PLAYER, added));
                }
                if (removed.length > 0) {
                    add(stream, timestamp, new ServerTeamPacket(team.name, ServerTeamPacket.Action.REMOVE_PLAYER, removed));
                }
            } else if (team.status == Team.Status.REMOVED) {
                add(stream, timestamp, new ServerTeamPacket(team.name));
            }
        }

        for (ServerMapDataPacket packet : maps.values()) {
            add(stream, timestamp, packet);
        }
    }

    @Override
    public String getName() {
        return "squash";
    }

    @Override
    public void init(Studio studio, JsonObject config) {
        PacketUtils.registerAllEntityRelated(studio);

        studio.setParsing(ServerNotifyClientPacket.class, true);
        studio.setParsing(ServerSetExperiencePacket.class, true);
        studio.setParsing(ServerPlayerAbilitiesPacket.class, true);
        studio.setParsing(ServerJoinGamePacket.class, true);
        studio.setParsing(ServerRespawnPacket.class, true);
        studio.setParsing(ServerTeamPacket.class, true);
        studio.setParsing(ServerCloseWindowPacket.class, true);
        studio.setParsing(ServerWindowItemsPacket.class, true);
        studio.setParsing(ServerSetSlotPacket.class, true);
        studio.setParsing(ServerChunkDataPacket.class, true);
        studio.setParsing(ServerMultiChunkDataPacket.class, true);
        studio.setParsing(ServerBlockChangePacket.class, true);
        studio.setParsing(ServerMultiBlockChangePacket.class, true);
        studio.setParsing(ServerMapDataPacket.class, true);
    }

    private void add(PacketStream stream, long timestamp, Packet packet) {
        stream.insert(new PacketData(timestamp, packet));
    }

    private void updateBlock(long time, BlockChangeRecord record) {
        ChunkData data = chunks.get(ChunkData.coordToLong(record.getX(), record.getZ()));
        if (data != null) {
            data.updateBlock(time, record);
        }
    }

    private void updateChunk(long time, int x, int z, Chunk[] chunkArray, byte[] biomeData) {
        long coord = ChunkData.coordToLong(x, z);
        if (Utils.containsOnlyNull(chunkArray)) { // UNLOAD
            if (chunks.remove(coord) == null) {
                unloadedChunks.put(coord, time);
            }
        } else { // LOAD
            unloadedChunks.remove(coord);
            ChunkData chunk = chunks.get(coord);
            if (chunk == null) {
                chunks.put(coord, chunk = new ChunkData(time, x, z));
            }
            chunk.update(chunkArray, biomeData);
        }
    }

    private static class ChunkData {
        private final long firstAppearance;
        private final int x;
        private final int z;
        private final Chunk[] changes = new Chunk[16];
        private byte[] biomeData;
        @SuppressWarnings("unchecked")
        private Map<Short, MutablePair<Long, BlockChangeRecord>>[] blockChanges = new Map[16];

        public ChunkData(long firstAppearance, int x, int z) {
            this.firstAppearance = firstAppearance;
            this.x = x;
            this.z = z;
        }

        public void update(Chunk[] newChunks, byte[] newBiomeData) {
            for (int i = 0; i < newChunks.length; i++) {
                if (newChunks[i] != null) {
                    changes[i] = newChunks[i];
                }
            }

            if (newBiomeData != null) {
                this.biomeData = newBiomeData;
            }
        }

        private MutablePair<Long, BlockChangeRecord> blockChanges(int x, int y, int z) {
            y = y / 16;
            if (blockChanges[y] == null) {
                blockChanges[y] = new HashMap<>();
            }
            short index = (short) ((x % 16) << 10 | (y % 16) << 5 | (z % 16));
            MutablePair<Long, BlockChangeRecord> pair = blockChanges[y].get(index);
            if (pair == null) {
                blockChanges[y].put(index, pair = MutablePair.of(0l, null));
            }
            return pair;
        }

        public void updateBlock(long time, BlockChangeRecord record) {
            MutablePair<Long, BlockChangeRecord> pair = blockChanges(record.getX(), record.getY(), record.getZ());
            if (pair.getLeft() < time) {
                pair.setLeft(time);
                pair.setRight(record);
            }
        }

        public static long coordToLong(int x, int z) {
            return (long) x << 32 | z & 0xFFFFFFFFL;
        }

        public static int longToX(long coord) {
            return (int) (coord >> 32);
        }

        public static int longToZ(long coord) {
            return (int) (coord & 0xFFFFFFFFL);
        }
    }

}
