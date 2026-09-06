package com.yamakotaro.sulfursoccer.gimmick;

import com.yamakotaro.sulfursoccer.arena.Box;

/** One admin-placed gimmick: a wand-selected region tagged with a type. Scoped to a single arena
 * and identified by an id unique within that arena (see GimmickManager). */
public record Gimmick(String id, GimmickType type, Box region) {
}
