package com.kuronami.bonfirestomap.network;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.sync.LocationStateOps;
import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S2C: サーバーが「このプレイヤーが発見済みの」篝火の全量スナップショットを
 * クライアントへ届ける payload。差分検出はサーバー側で行い、変化時だけ全量を送る
 * （冪等・クライアントは受信した集合で望ましい状態を丸ごと置き換えられる）。
 */
public record BonfireSyncPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<BonfireSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BonfiresToMap.MODID, "bonfires"));

    /** 1 地点分のデータ。名前は Component・座標は BlockPos・色は 0xRRGGBB。 */
    public record Entry(UUID id, Component name, BlockPos pos, ResourceKey<Level> dimension, int rgb) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::id,
                ComponentSerialization.STREAM_CODEC, Entry::name,
                BlockPos.STREAM_CODEC, Entry::pos,
                ResourceKey.streamCodec(Registries.DIMENSION), Entry::dimension,
                ByteBufCodecs.VAR_INT, Entry::rgb,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BonfireSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, Entry.STREAM_CODEC), BonfireSyncPayload::entries,
                    BonfireSyncPayload::new);

    /**
     * 正規化済みスナップショットから payload を組む。エントリは篝火 UUID 昇順
     * （決定論的＝ログ比較とテストが安定する）。
     */
    public static BonfireSyncPayload of(Map<UUID, PointSnapshot> state) {
        List<Entry> entries = new ArrayList<>();
        for (PointSnapshot snapshot : LocationStateOps.normalize(state).values()) {
            entries.add(new Entry(
                    snapshot.id(),
                    Component.literal(snapshot.name()),
                    new BlockPos(snapshot.x(), snapshot.y(), snapshot.z()),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(snapshot.dimensionId())),
                    snapshot.rgb()));
        }
        return new BonfireSyncPayload(List.copyOf(entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
