package com.yamakotaro.ecotp.gui;

import java.util.List;

/**
 * ホーム選択・プレイヤー選択・ランキングGUIで使う、単純なページ分割ヘルパー。
 * 45件を超える一覧を「表示しきれない分は諦める」のではなく、ページ送りできるようにする。
 */
final class Paginator<T> {

    private final List<T> items;
    private final int pageSize;

    Paginator(List<T> items, int pageSize) {
        this.items = items;
        this.pageSize = pageSize;
    }

    int pageCount() {
        return Math.max(1, (items.size() + pageSize - 1) / pageSize);
    }

    /** @param pageIndex 0始まり。範囲外は最も近い有効ページに丸める。 */
    List<T> page(int pageIndex) {
        int clamped = Math.max(0, Math.min(pageIndex, pageCount() - 1));
        int from = Math.min(clamped * pageSize, items.size());
        int to = Math.min(from + pageSize, items.size());
        return items.subList(from, to);
    }

    boolean hasPrevPage(int pageIndex) {
        return pageIndex > 0;
    }

    boolean hasNextPage(int pageIndex) {
        return pageIndex < pageCount() - 1;
    }
}
