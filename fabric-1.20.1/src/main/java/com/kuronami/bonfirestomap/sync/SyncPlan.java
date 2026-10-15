package com.kuronami.bonfirestomap.sync;

import java.util.List;
import java.util.UUID;

/**
 * 差分同期エンジンの出力。JM への適用方法だけを表す。
 *
 * @param upserts  新規追加または内容更新が必要な地点（同一キーの remove + add で適用される）
 * @param removals JM から取り除くべき地点のキー
 */
public record SyncPlan(List<PointSnapshot> upserts, List<UUID> removals) {

    public SyncPlan {
        upserts = List.copyOf(upserts);
        removals = List.copyOf(removals);
    }

    /** 差分ゼロかどうか。 */
    public boolean isEmpty() {
        return upserts.isEmpty() && removals.isEmpty();
    }
}
