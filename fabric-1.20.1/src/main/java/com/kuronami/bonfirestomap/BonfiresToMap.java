package com.kuronami.bonfirestomap;

import com.kuronami.bonfirestomap.server.ServerBonfireWatcher;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bonfires to Map — Bonfires で見つけた篝火（その人がワープできる篝火）を
 * JourneyMap のウェイポイントとして自動表示するブリッジ（Fabric 1.20.1）。
 *
 * <p>構成は NeoForge 1.21.1 版と同型: サーバーが Bonfires の篝火登録と
 * プレイヤーごとの発見記録を読み、変化時だけ S2C で送る。クライアントは
 * 受信集合を JourneyMap のウェイポイントへ差分同期する。シングルプレイでは
 * 統合サーバーが同じ経路で動く。</p>
 *
 * <p>1.20.1 Fabric は payload 型レジストリが無く、チャンネルは
 * {@code net.minecraft.resources.ResourceLocation} 名で送受信する raw byte-buf 方式
 * （mod-076/003 fabric-1.20.1 と同方式）。</p>
 *
 * <p>クライアント側の配線は {@code BonfiresToMapClient}（client entrypoint）が担うため、
 * このクラスは dedicated server でもそのまま動く。設計方針（v1.0）: config 無しの
 * 固定挙動。常時同期・永続ウェイポイント・片方向。</p>
 */
public final class BonfiresToMap implements ModInitializer {

    public static final String MODID = "bonfirestomap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        // サーバー側の監視ループ（統合サーバー＝シングルプレイでも同じ経路で動く）
        ServerBonfireWatcher.init();
    }
}
