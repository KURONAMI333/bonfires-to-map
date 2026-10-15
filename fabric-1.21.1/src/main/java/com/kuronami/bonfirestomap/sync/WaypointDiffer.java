package com.kuronami.bonfirestomap.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 望ましい状態（Bonfires の篝火一覧）と追跡中の状態（JM へ反映済みスナップショット）を
 * 比較して差分プランを作る純粋関数群。JourneyMap の型に一切依存しない。
 *
 * <p>更新判定はレコード等価性（名前・座標・次元・色のいずれかが変われば更新対象）。
 * 引数のマップを壊さない。</p>
 */
public final class WaypointDiffer {

    private WaypointDiffer() {
    }

    /**
     * @param desired 現在あるべき地点集合（null 禁止・空なら全削除を意味する）
     * @param tracked 最後に JM へ反映した地点集合（null 禁止）
     * @return 適用すべき差分。決定論的な順序（upserts=id 昇順、removals=id 昇順）を持つ
     */
    public static SyncPlan plan(Map<UUID, PointSnapshot> desired, Map<UUID, PointSnapshot> tracked) {
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(tracked, "tracked");

        List<PointSnapshot> upserts = new ArrayList<>();
        for (Map.Entry<UUID, PointSnapshot> entry : desired.entrySet()) {
            PointSnapshot previous = tracked.get(entry.getKey());
            // previous == null → 新規、それ以外はレコード等価性で更新要否を判定
            if (!entry.getValue().equals(previous)) {
                upserts.add(entry.getValue());
            }
        }

        List<UUID> removals = new ArrayList<>();
        for (UUID id : tracked.keySet()) {
            if (!desired.containsKey(id)) {
                removals.add(id);
            }
        }

        Comparator<PointSnapshot> byId = Comparator.comparing(PointSnapshot::id);
        upserts.sort(byId);
        removals.sort(Comparator.naturalOrder());
        return new SyncPlan(List.copyOf(upserts), List.copyOf(removals));
    }
}
