package com.kuronami.bonfirestomap.network;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.client.BtmClient;
import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 SimpleChannel ベースのネットワーク層。
 * 1.21.1 NeoForge の payload 登録（RegisterPayloadHandlersEvent + StreamCodec）は
 * 1.20.1 に存在しないので、ここがローダー固有の作り直し部（mod-075/003 forge-1.20.1 と同方式）。
 *
 * <p>チャネルは version predicate を accept-any にしており required ではない。
 * よって本 MOD を持たないクライアントでも接続・プレイは弾かれない
 * （BRIEF「このMODの無いクライアントがサーバーに入れなくならないこと」）。
 * 送信側は相手がチャネルを宣言している場合のみ送るため、未知チャネルへの
 * payload 送出は起きない。</p>
 */
public final class BtmNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BonfiresToMap.MODID, "main"),
            () -> PROTOCOL_VERSION,
            v -> true,   // accept any client version
            v -> true    // accept any server version
    );

    private BtmNetwork() {
    }

    /** FMLCommonSetupEvent の enqueueWork から呼ぶ。 */
    public static void register() {
        CHANNEL.registerMessage(
                0,
                BonfireSyncPacket.class,
                BonfireSyncPacket::encode,
                BonfireSyncPacket::decode,
                BtmNetwork::handleOnClient,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void handleOnClient(BonfireSyncPacket packet, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        BtmClient.onBonfiresFromServer(packet)));
        ctx.setPacketHandled(true);
    }

    /** 差分検出済みの全量スナップショットをこのプレイヤーへだけ送る。 */
    public static void sendBonfires(ServerPlayer player, Map<UUID, PointSnapshot> state) {
        // 本 MOD を持たないクライアント（チャネル未宣言）へは送らない。
        // NeoForge の optional payload / Fabric の canSend に相当する送信側ガード。
        if (!CHANNEL.isRemotePresent(player.connection.connection)) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), BonfireSyncPacket.of(state));
    }
}
