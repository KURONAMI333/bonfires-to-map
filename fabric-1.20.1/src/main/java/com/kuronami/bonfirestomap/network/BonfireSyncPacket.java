package com.kuronami.bonfirestomap.network;

import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S2C: 「このプレイヤーが発見済みの」篝火の全量スナップショットの手書き codec。
 * Fabric 1.20.1 は {@code CustomPacketPayload} / {@code StreamCodec} が存在しないため、
 * raw {@link FriendlyByteBuf} へ直接書く（mod-076/003 fabric-1.20.1 と同方式）。
 *
 * <p>ワイヤ形式: {@code varint count × (uuid id, utf name, blockPos pos, utf dimensionId, varint rgb)}。
 * 名前・次元は文字列で運ぶ（1.21.1 版の Component / ResourceKey 直列化は 1.20.1 に無い）。
 * クライアントは受信した文字列をそのまま PointSnapshot へ入れる。</p>
 */
public final class BonfireSyncPacket {

    private BonfireSyncPacket() {
    }

    public static void write(FriendlyByteBuf buf, List<PointSnapshot> entries) {
        buf.writeVarInt(entries.size());
        for (PointSnapshot s : entries) {
            buf.writeUUID(s.id());
            buf.writeUtf(s.name());
            buf.writeBlockPos(new BlockPos(s.x(), s.y(), s.z()));
            buf.writeUtf(s.dimensionId());
            buf.writeVarInt(s.rgb());
        }
    }

    public static List<PointSnapshot> read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PointSnapshot> entries = new ArrayList<>(Math.max(0, Math.min(count, 4096)));
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            String dimensionId = buf.readUtf();
            int rgb = buf.readVarInt();
            entries.add(new PointSnapshot(id, name, pos.getX(), pos.getY(), pos.getZ(), dimensionId, rgb));
        }
        return entries;
    }

    /** 受信リストを SyncManager 向けのマップへ詰め替える（id 重複時は後勝ち）。 */
    public static Map<UUID, PointSnapshot> toMap(List<PointSnapshot> entries) {
        LinkedHashMap<UUID, PointSnapshot> map = new LinkedHashMap<>();
        for (PointSnapshot entry : entries) {
            if (entry != null && entry.id() != null) {
                map.put(entry.id(), entry);
            }
        }
        return map;
    }
}
