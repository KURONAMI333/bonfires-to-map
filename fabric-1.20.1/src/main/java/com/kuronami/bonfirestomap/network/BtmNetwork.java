package com.kuronami.bonfirestomap.network;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.sync.LocationStateOps;
import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric 1.20.1 raw byte-buf networking（S2C 1 チャンネル）。
 * 1.21.1 の payload 型レジストリ（{@code CustomPacketPayload} + {@code PayloadTypeRegistry}）は
 * 1.20.1 Fabric に存在しないので、ここがローダー固有の作り直し部（mod-076/003 fabric-1.20.1 と同方式）。
 *
 * <p>送信前の {@link #canSend(ServerPlayer)} は必須: 本 MOD を持たないクライアント
 * （BRIEF「このMODの無いクライアントがサーバーに入れなくならないこと」）には送っては
 * ならない。NeoForge の optional payload 登録に相当する挙動を、Fabric では送信側の
 * 明示判定で作る。</p>
 */
public final class BtmNetwork {

    public static final ResourceLocation BONFIRES_ID =
            new ResourceLocation(BonfiresToMap.MODID, "bonfires");

    private BtmNetwork() {
    }

    /** 相手が本 MOD のチャンネルを理解するか（本 MOD が無ければ false）。 */
    public static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, BONFIRES_ID);
    }

    /**
     * 正規化済みスナップショットを全量送信する（決定論的順序＝篝火 UUID 昇順）。
     * 差分検出はサーバー側 watcher が済ませており、変化時だけ呼ばれる。
     */
    public static void sendBonfires(ServerPlayer player, Map<UUID, PointSnapshot> state) {
        List<PointSnapshot> entries = new ArrayList<>(LocationStateOps.normalize(state).values());
        FriendlyByteBuf buf = PacketByteBufs.create();
        BonfireSyncPacket.write(buf, entries);
        ServerPlayNetworking.send(player, BONFIRES_ID, buf);
    }
}
