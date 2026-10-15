package com.kuronami.bonfirestomap.network;

import com.kuronami.bonfirestomap.client.BtmClient;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * payload の登録（MOD バス・両 dist で実施。サーバーが送れるようにするため）と
 * クライアント受信ハンドラ。
 *
 * <p>1.21.1 では handler は既定で main thread 実行なので enqueueWork は使わない
 * （NEW_MOD_GUIDE networking 慣例）。</p>
 *
 * <p>チャネルは optional にする（BRIEF「このMODの無いクライアントがサーバーに入れなく
 * ならないこと」）。optional でないと、本 MOD を持たない NeoForge クライアントが
 * 接続時に弾かれる。{@code optional()} は新しい registrar を返すので戻り値を使う。</p>
 */
public final class BtmNetwork {

    private BtmNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(BonfireSyncPayload.TYPE, BonfireSyncPayload.STREAM_CODEC, BtmNetwork::handleOnClient);
    }

    private static void handleOnClient(BonfireSyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        // この handler はクライアントでしか発火しない。BtmClient（client 専用クラス）
        // への参照はここで初めて解決される＝サーバー dist ではロードされない。
        BtmClient.onBonfiresFromServer(payload);
    }
}
