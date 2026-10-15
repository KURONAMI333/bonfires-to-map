package com.kuronami.bonfirestomap.client;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.network.BonfireSyncPacket;

import net.minecraft.client.Minecraft;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * クライアント側の配線（Forge 1.20.1）。このクラスはサーバー dist では決してロードされない
 * （{@link BonfiresToMap} の dist 分岐と、S2C handler の実行側からのみ参照される）。
 *
 * <p>役割: サーバー受信データの JM 反映を 10 tick ごとに試みるだけ。
 * 篝火の検出はサーバー側（ServerBonfireWatcher）が担う。</p>
 */
public final class BtmClient {

    /** 未適用データの再試行間隔。JM API 初期化待ちの吸収が主目的。 */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    private BtmClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(BtmClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(BtmClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(BtmClient::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SyncManager.resetSession();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncManager.resetSession();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
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
     * S2C 受信入口（BtmNetwork の handler から main thread で呼ばれる）。
     * 受信した全量を SyncManager のあるべき状態として渡す。
     */
    public static void onBonfiresFromServer(BonfireSyncPacket packet) {
        SyncManager.onBonfiresFromServer(packet.toMap());
    }
}
