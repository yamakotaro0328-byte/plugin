package com.yamakotaro.sulfursoccer.gimmick;

import com.yamakotaro.sulfursoccer.arena.Box;

/** One admin-placed gimmick: a type tagged onto a single-column region randomly chosen from the
 * admin's wand selection (see SoccerCommand#randomPositionWithin) - the selection marks a spawn
 * range, not the gimmick's own footprint. Scoped to a single arena and identified by an id unique
 * within that arena (see GimmickManager). */
public record Gimmick(String id, GimmickType type, Box region) {
}
