package com.kuronami.bonfirestomap;

import com.kuronami.bonfirestomap.client.BtmClient;
import com.kuronami.bonfirestomap.network.BtmNetwork;
import com.kuronami.bonfirestomap.server.ServerBonfireWatcher;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import org.slf4j.Logger;

/**
 * Bonfires to Map — Bonfires で発見済みの篝火を JourneyMap のウェイポイントとして
 * 自動表示するブリッジ（両サイド型・Forge 1.20.1 / Bonfires 1.2.x）。
 *
 * <p>構成は 109 本体（NeoForge 1.21.1）と同型。差分は payload 層
 * （1.20.1 Forge = SimpleChannel + FriendlyByteBuf の手書き codec）と、
 * サーバー読取が capability 方式（DiscoveryHandler / BonfireHandler）になった点。</p>
 *
 * <p>設計方針（v1.0）: config 無しの固定挙動。常時同期・永続ウェイポイント・片方向。
 * 送るのは「そのプレイヤーが発見済みの篝火」のみ。</p>
 */
@Mod(BonfiresToMap.MODID)
public final class BonfiresToMap {

    public static final String MODID = "bonfirestomap";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BonfiresToMap() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(BonfiresToMap::onCommonSetup);

        if (FMLEnvironment.dist.isClient()) {
            // クライアント専用クラスへの参照はこの分岐の中だけで行う
            BtmClient.init();
        }
        // サーバー側の監視ループ（統合サーバーでも同じ経路で動く）。
        // イベント自体がサーバー専用なのでクライアント dist ではハンドラが走らず
        // Bonfires 関連クラスもロードされない
        MinecraftForge.EVENT_BUS.register(ServerBonfireWatcher.class);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // SimpleChannel への message 登録は deferred work で行う（mod-075/003 forge-1.20.1 実績方式）
        event.enqueueWork(BtmNetwork::register);
    }
}
