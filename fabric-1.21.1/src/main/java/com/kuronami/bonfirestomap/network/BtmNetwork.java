package com.kuronami.bonfirestomap.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * payload 型の登録と送信（Fabric 1.21.1）。バニラの {@code CustomPacketPayload} を
 * {@link PayloadTypeRegistry#playS2C()} に登録する。受信側ハンドラは client entrypoint
 * （{@code client.BtmClient}）で登録する。
 *
 * <p>送信前の {@link #canSend(ServerPlayer)} は必須: 本 MOD を持たないクライアント
 * （BRIEF「このMODの無いクライアントがサーバーに入れなくならないこと」）には送っては
 * ならない。NeoForge の optional 登録に相当する挙動を、Fabric では送信側の明示判定で作る。</p>
 */
public final class BtmNetwork {

    private BtmNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(BonfireSyncPayload.TYPE, BonfireSyncPayload.STREAM_CODEC);
    }

    /** 相手が本 MOD のチャネルを理解するか（本 MOD が無ければ false）。 */
    public static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, BonfireSyncPayload.TYPE);
    }

    public static void send(ServerPlayer player, BonfireSyncPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
