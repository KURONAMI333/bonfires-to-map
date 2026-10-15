package com.kuronami.bonfirestomap.client;

import com.kuronami.bonfirestomap.compat.jm.JourneyMapBridge;
import com.kuronami.bonfirestomap.sync.PointSnapshot;
import com.kuronami.bonfirestomap.sync.SyncPlan;
import com.kuronami.bonfirestomap.sync.WaypointDiffer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * クライアント側の同期状態機械。サーバーから受信した「自分の発見済み篝火」全量スナップショットを
 * あるべき状態として保持し、JM へ差分適用する。
 *
 * <p>セマンティクス: 片方向ミラー（Bonfires[server registry+discovery] → payload → JM）。</p>
 *
 * <p>セッション開始の扱い: ウェイポイントは永続（JM の保存データに残る）ため、
 * セッション最初の適用では {@code removeAllWaypoints} → 受信地点で全件再構築して
 * 重複蓄積を防ぐ。2 回目以降は差分適用（add / update=remove+add / remove）。</p>
 *
 * <p>JM API がまだ初期化されていない間に payload が届いても、dirty フラグで
 * 保持しておき tick ループから再試行する（取りこぼし無し）。</p>
 */
public final class SyncManager {

    /** 最後に JM へ反映した地点集合（キー = 篝火 UUID）。 */
    private static final Map<UUID, PointSnapshot> TRACKED = new LinkedHashMap<>();

    /** サーバーから受信した最新のあるべき状態。 */
    private static Map<UUID, PointSnapshot> desired = null;

    /** desired の未適用フラグ。 */
    private static boolean dirty = false;

    /** 今セッションで JM 側を一度全再構築済みか。 */
    private static boolean rebuiltThisSession = false;

    private SyncManager() {
    }

    /**
     * サーバーからの payload 受信（main thread）。受信した集合を新しいあるべき状態とする。
     */
    public static void onBonfiresFromServer(Map<UUID, PointSnapshot> fresh) {
        Map<UUID, PointSnapshot> next = new LinkedHashMap<>(fresh);
        if (next.equals(desired)) {
            return; // 内容同一（順序違いを含む）なら JM 触らず済む
        }
        desired = next;
        dirty = true;
    }

    /**
     * 未適用の受信データがあれば JM へ適用する。クライアント tick から定期呼び出しされる。
     * JM 不在・API 未初期化・失敗時は静かに何もせず次回再試行。
     *
     * @return 実際に何か適用したら true
     */
    public static boolean flushPending() {
        if (!dirty || desired == null) {
            return false;
        }
        if (!rebuiltThisSession) {
            if (!JourneyMapBridge.replaceAll(desired)) {
                return false; // API 未初期化 → 次サイクルで再試行
            }
            commit();
            return true;
        }
        SyncPlan plan = WaypointDiffer.plan(desired, TRACKED);
        if (plan.isEmpty()) {
            commit();
            return false;
        }
        if (!JourneyMapBridge.apply(plan)) {
            return false;
        }
        commit();
        com.kuronami.bonfirestomap.BonfiresToMap.LOGGER.debug("Synced {} upsert(s) / {} removal(s) to JourneyMap",
                plan.upserts().size(), plan.removals().size());
        return true;
    }

    /** dirty 状態か（テスト・デバッグ用）。 */
    public static boolean hasPending() {
        return dirty;
    }

    private static void commit() {
        TRACKED.clear();
        TRACKED.putAll(desired);
        dirty = false;
        rebuiltThisSession = true;
    }

    /**
     * ワールド接続/切断の境界でのリセット。JM 上のウェイポイントは
     * 永続データなので削除しに行かない。サーバーはログイン時に必ず再送してくる。
     */
    public static void resetSession() {
        TRACKED.clear();
        desired = null;
        dirty = false;
        rebuiltThisSession = false;
        JourneyMapBridge.forgetSession();
    }
}
