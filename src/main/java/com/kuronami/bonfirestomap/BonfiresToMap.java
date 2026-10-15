package com.kuronami.bonfirestomap;

import com.kuronami.bonfirestomap.client.BtmClient;
import com.kuronami.bonfirestomap.network.BtmNetwork;
import com.kuronami.bonfirestomap.server.ServerBonfireWatcher;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import org.slf4j.Logger;

/**
 * Bonfires to Map — Bonfires で見つけた篝火（その人がワープできる篝火）を
 * JourneyMap のウェイポイントとして自動表示するブリッジ（両サイド型）。
 *
 * <p>構成: サーバーが Bonfires の篝火登録とプレイヤーごとの発見記録を読み、
 * 変化時だけ S2C payload で送る。クライアントは受信集合を JourneyMap の
 * ウェイポイントへ差分同期する。シングルプレイでは統合サーバーが同じ経路で動く。</p>
 *
 * <p>設計方針（v1.0）: config 無しの固定挙動。常時同期・永続ウェイポイント・片方向。</p>
 */
@Mod(BonfiresToMap.MODID)
public final class BonfiresToMap {

    public static final String MODID = "bonfirestomap";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BonfiresToMap(IEventBus modEventBus, ModContainer modContainer) {
        // payload 登録は両 dist で必須（サーバーが送るにも登録が要る）
        modEventBus.addListener(BtmNetwork::register);

        if (FMLEnvironment.dist.isClient()) {
            // クライアント専用クラスへの参照はこの分岐の中だけで行う
            BtmClient.init(modEventBus);
        }
        // サーバー側の監視ループ。イベント自体が server 専用なので
        // クライアント dist ではハンドラが走らず Bonfires 関連クラスもロードされない
        ServerBonfireWatcher.init();
    }
}
