package com.kuronami.bonfirestomap.client;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.network.BonfireSyncPacket;
import com.kuronami.bonfirestomap.network.BtmNetwork;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/**
 * クライアント側の配線（Fabric 1.20.1）。このクラスは client entrypoint からだけ
 * ロードされる＝dedicated server では決してクラスロードされない。
 *
 * <p>役割: S2C 受信（netty スレッド→ main thread へ hop）と、
 * JourneyMap 反映を 10 tick ごとに試みること。篝火の検出はサーバー側が担う。</p>
 */
public final class BtmClient {

    /** 未適用データの再試行間隔。JM API 初期化待ちの吸収が主目的。 */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    private BtmClient() {
    }

    public static void init() {
        // Fabric 1.20.1 の raw buf 受信。バッファはネットワークスレッドで使い切ってから
        // main thread へ渡す（buf 自体は持ち越せない）
        ClientPlayNetworking.registerGlobalReceiver(BtmNetwork.BONFIRES_ID,
                (mc, handler, buf, responseSender) -> {
                    var entries = BonfireSyncPacket.read(buf);
                    mc.execute(() -> SyncManager.onBonfiresFromServer(BonfireSyncPacket.toMap(entries)));
                });

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
}
