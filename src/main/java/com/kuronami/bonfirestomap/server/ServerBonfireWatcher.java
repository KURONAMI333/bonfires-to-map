package com.kuronami.bonfirestomap.server;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.network.BonfireSyncPayload;
import com.kuronami.bonfirestomap.sync.LocationStateOps;
import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側の監視ループ。全 online プレイヤーを 30 tick ごとに軽く見て、
 * 「そのプレイヤーが発見済みの篝火」スナップショットに変化があればそのプレイヤーへだけ
 * payload を送る。ログイン直後にも必ず 1 回送る。
 *
 * <p>これらのイベントはサーバー側で発火する。専用サーバーだけでなく、シングルプレイの
 * 統合サーバー（クライアントと同一 JVM で動く）でも発火する。クライアント dist の
 * クライアント側 tick では発火しない。</p>
 */
public final class ServerBonfireWatcher {

    /** 20〜40 tick 帯の中間。地点変更は即座には要らないので軽量優先。 */
    private static final int SYNC_INTERVAL_TICKS = 30;

    /** playerId → 最後に送った状態。 */
    private static final Map<UUID, Map<UUID, PointSnapshot>> LAST_SENT = new ConcurrentHashMap<>();

    private ServerBonfireWatcher() {
    }

    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerBonfireWatcher::onServerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerBonfireWatcher::onLoggedIn);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerBonfireWatcher::onLoggedOut);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncIfChanged(player);
        }
    }

    private static void onLoggedIn(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncIfChanged(player);
        }
    }

    private static void onLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_SENT.remove(player.getUUID());
        }
    }

    private static void syncIfChanged(ServerPlayer player) {
        // 本 MOD を持たないクライアント（optional チャネル）へは送らない。
        // 送ると未知チャネルの payload になり得るので、交渉済みチャネルを確認する。
        if (!net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(
                player.connection, BonfireSyncPayload.TYPE.id())) {
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
        PacketDistributor.sendToPlayer(player, BonfireSyncPayload.of(current));
        if (previous == null) {
            BonfiresToMap.LOGGER.debug("Initial Bonfires sync: {} bonfire(s) -> {}", current.size(), player.getGameProfile().getName());
        }
    }
}
