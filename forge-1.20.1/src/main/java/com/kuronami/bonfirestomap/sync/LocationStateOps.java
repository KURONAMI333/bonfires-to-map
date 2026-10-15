package com.kuronami.bonfirestomap.sync;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * サーバー側の「前回送信状態 vs 現在状態」比較のための純粋関数群。
 * Bonfires / NeoForge の型に依存しない（JUnit が MC クラスパス無しで回る）。
 */
public final class LocationStateOps {

    private LocationStateOps() {
    }

    /**
     * 比較・送信用に状態を正規化する。
     * キー昇順の TreeMap へ詰め替え（payload の順序を決定論的に）＋ null 値の除去。
     *
     * @param state 地点集合（null 許容・null は空として扱う）
     * @return 正規化された不変マップ
     */
    public static Map<UUID, PointSnapshot> normalize(Map<UUID, PointSnapshot> state) {
        TreeMap<UUID, PointSnapshot> sorted = new TreeMap<>();
        if (state != null) {
            for (Map.Entry<UUID, PointSnapshot> entry : state.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sorted.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return java.util.Collections.unmodifiableSortedMap(sorted);
    }

    /**
     * 前回送信した状態と現在の状態が異なるか。
     * 順序の違いは同一とみなし、内容の差分のみを検出する。
     *
     * @param previousSent 前回送信分（初回は null）
     * @param current      現在の状態
     * @return 送信が必要なら true
     */
    public static boolean stateChanged(Map<UUID, PointSnapshot> previousSent,
                                       Map<UUID, PointSnapshot> current) {
        return !normalize(current).equals(normalize(previousSent));
    }
}
