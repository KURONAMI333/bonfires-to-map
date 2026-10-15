package com.kuronami.bonfirestomap.client;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.network.BonfireSyncPayload;
import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * クライアント側の配線。このクラスはサーバー dist では決してロードされない
 * （{@link BonfiresToMap} の dist 分岐と、S2C handler の実行側からのみ参照される）。
 *
 * <p>役割: サーバー受信データの JM 反映を 10 tick ごとに試みるだけ。
 * 地点の検出はサーバー側（ServerBonfireWatcher）が担う。</p>
 */
public final class BtmClient {

    /** 未適用データの再試行間隔。JM API 初期化待ちの吸収が主目的。 */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    private BtmClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(BtmClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(BtmClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BtmClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(BtmClient::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SyncManager.resetSession();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncManager.resetSession();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
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
     * S2C payload 受信入口（BtmNetwork の handler から main thread で呼ばれる）。
     * payload 型（純粋レコード）を JM 差分エンジン向けの PointSnapshot 集合へ変換する。
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
