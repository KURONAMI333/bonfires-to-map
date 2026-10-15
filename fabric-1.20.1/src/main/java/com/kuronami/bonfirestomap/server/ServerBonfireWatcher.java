package com.kuronami.bonfirestomap.server;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.network.BtmNetwork;
import com.kuronami.bonfirestomap.sync.LocationStateOps;
import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側の監視ループ（Fabric 1.20.1 版）。全 online プレイヤーを 30 tick ごとに
 * 軽く見て、「そのプレイヤーが発見済みの篝火」スナップショットに変化があればその
 * プレイヤーへだけ送る。ログイン直後にも必ず 1 回送る。ロジックは NeoForge 版と同一で、
 * イベント差し替えのみ。
 */
public final class ServerBonfireWatcher {

    /** 20〜40 tick 帯の中間。地点変更は即座には要らないので軽量優先。 */
    private static final int SYNC_INTERVAL_TICKS = 30;

    /** playerId → 最後に送った状態。 */
    private static final Map<UUID, Map<UUID, PointSnapshot>> LAST_SENT = new ConcurrentHashMap<>();

    private ServerBonfireWatcher() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ServerBonfireWatcher::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                syncIfChanged(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_SENT.remove(handler.player.getUUID()));
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncIfChanged(player);
        }
    }

    private static void syncIfChanged(ServerPlayer player) {
        // 本 MOD を持たないクライアント（未登録チャネル）へは送らない。
        // Fabric では optional 登録が無いため、送信可能性を明示的に確認する。
        if (!BtmNetwork.canSend(player)) {
            return;
        }
        Map<UUID, PointSnapshot> current = BonfireCollector.collect(player);
        if (current == null) {
            return; // Bonfires 不在 / 読み取り失敗 → 触れない
        }
        UUID playerId = player.getUUID();
        Map<UUID, PointSnapshot> previous = LAST_SENT.get(playerId);
        if (!LocationStateOps.stateChanged(previous, current)) {
            return;
        }
        LAST_SENT.put(playerId, current);
        BtmNetwork.sendBonfires(player, current);
        if (previous == null) {
            BonfiresToMap.LOGGER.debug("Initial Bonfires sync: {} bonfire(s) -> {}",
                    current.size(), player.getGameProfile().getName());
        }
    }
}
