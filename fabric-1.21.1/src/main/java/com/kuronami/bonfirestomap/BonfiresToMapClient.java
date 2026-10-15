package com.kuronami.bonfirestomap;

import com.kuronami.bonfirestomap.client.BtmClient;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric クライアント側エントリ。S2C payload の受信ハンドラ登録と tick ループの配線を行う。
 * このクラスは client entrypoint からだけロードされる＝dedicated server では決して
 * クラスロードされない。
 */
public final class BonfiresToMapClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BtmClient.init();
    }
}
