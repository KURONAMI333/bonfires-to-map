package com.kuronami.bonfirestomap.client;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.network.BonfireSyncPayload;
import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * クライアント側の配線（Fabric 1.21.1）。このクラスは client entrypoint からだけ
 * ロードされる＝dedicated server では決してクラスロードされない。
 *
 * <p>役割: S2C 受信（netty スレッド→ main thread へ hop）と、JourneyMap 反映を
 * 10 tick ごとに試みること。地点の検出はサーバー側（ServerBonfireWatcher）が担う。</p>
 */
public final class BtmClient {

    /** 未適用データの再試行間隔。JM API 初期化待ちの吸収が主目的。 */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    private BtmClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BonfireSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> onBonfiresFromServer(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(BtmClient::onEndClientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SyncManager.resetSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SyncManager.resetSession());
    }

    private static void onEndClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if ((mc.player.tickCount % FLUSH_INTERVAL_TICKS) != 0) {
            return;
        }
        try {
            SyncManager.flushPending();
        } catch (Throwable t) {
            BonfiresToMap.LOGGER.warn("Bonfires to Map sync cycle failed: {}", t.toString());
        }
    }

    /**
     * S2C payload 受信入口（main thread で呼ばれる）。payload 型（純粋レコード）を
     * JM 差分エンジン向けの PointSnapshot 集合へ変換する。
     */
    public static void onBonfiresFromServer(BonfireSyncPayload payload) {
        Map<UUID, PointSnapshot> fresh = new LinkedHashMap<>();
        for (BonfireSyncPayload.Entry entry : payload.entries()) {
            if (entry == null || entry.id() == null || entry.name() == null
                    || entry.pos() == null || entry.dimension() == null) {
                continue;
            }
            fresh.put(entry.id(), new PointSnapshot(
                    entry.id(),
                    entry.name().getString(),
                    entry.pos().getX(),
                    entry.pos().getY(),
                    entry.pos().getZ(),
                    entry.dimension().location().toString(),
                    entry.rgb()));
        }
        SyncManager.onBonfiresFromServer(fresh);
    }
}
