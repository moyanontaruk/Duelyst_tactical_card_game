package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Story Card #17: Ability Trigger - Unit Summoned (Opening Gambit)
 *
 * When a unit is created on the board, run its on-summon effect once.
 * Test with:
 * - Gloom Chaser
 * - Nightsorrow Assassin
 * - Silverguard Squire
 */
public final class OpeningGambitResolver {

    private OpeningGambitResolver() {}

    /**
     * Call this right after a unit is successfully spawned on the board.
     */
public static void onSummoned(ActorRef out, GameState gameState, Unit summonedUnit, String cardName) {
    if (summonedUnit == null || cardName == null) return;

    String name = cardName.trim().toLowerCase();
    String owner = gameState.unitOwner.get(summonedUnit.getId());
    if (owner == null) return;

    boolean isHuman = "HUMAN".equals(owner);

    if (name.equals("gloom chaser")) {
        int x = summonedUnit.getPosition().getTilex();
        int y = summonedUnit.getPosition().getTiley();

        int bx = isHuman ? x - 1 : x + 1;
        int by = y;

        if (isOnBoard(bx, by) && !gameState.boardUnits.containsKey(gameState.key(bx, by))) {
            SummonUtils.spawnWraithling(out, gameState, bx, by, owner);
        }
        return;
    }

    if (name.equals("nightsorrow assassin")) {
        Unit target = firstAdjacentEnemyBelowMax(gameState, summonedUnit, owner);
        if (target != null) {
            if (isEnemyAvatar(gameState, owner, target.getId())) {
                int damage = gameState.unitAttack.getOrDefault(summonedUnit.getId(), 0);
                int targetHp = gameState.unitHealth.getOrDefault(target.getId(), 0);
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, targetHp - damage);
            } else {
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, 0);
            }
        }
        return;
    }

    if (name.equals("silverguard squire")) {
        int[] avatarPos = gameState.getAvatarPosition(owner);
        if (avatarPos == null) return;

        int ax = avatarPos[0];
        int ay = avatarPos[1];

        int frontX = isHuman ? ax + 1 : ax - 1;
        int backX  = isHuman ? ax - 1 : ax + 1;

        buffIfAllied(out, gameState, frontX, ay, owner);
        buffIfAllied(out, gameState, backX, ay, owner);
    }
}

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static void buffIfAllied(ActorRef out, GameState gameState, int x, int y, String owner) {
        if (!isOnBoard(x, y)) return;

        Unit u = gameState.boardUnits.get(gameState.key(x, y));
        if (u == null) return;

        String uOwner = gameState.unitOwner.get(u.getId());
        if (!owner.equals(uOwner)) return;

        // +1 attack
        int oldAtk = gameState.unitAttack.getOrDefault(u.getId(), 0);
        int newAtk = oldAtk + 1;
        gameState.unitAttack.put(u.getId(), newAtk);
        BasicCommands.setUnitAttack(out, u, newAtk);

        // +1 health and +1 max health
        int oldHp = gameState.unitHealth.getOrDefault(u.getId(), 0);
        int oldMax = gameState.unitMaxHealth.getOrDefault(u.getId(), oldHp);

        gameState.unitMaxHealth.put(u.getId(), oldMax + 1);
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, u, oldHp + 1);
    }

    private static Unit firstAdjacentEnemyBelowMax(GameState gameState, Unit src, String srcOwner) {
        int x = src.getPosition().getTilex();
        int y = src.getPosition().getTiley();

        for (int[] p : adjacent(x, y)) {
            int tx = p[0], ty = p[1];
            if (!isOnBoard(tx, ty)) continue;

            Unit u = gameState.boardUnits.get(gameState.key(tx, ty));
            if (u == null) continue;

            String owner = gameState.unitOwner.get(u.getId());
            if (owner == null) continue;

            // enemy only
            if (owner.equals(srcOwner)) continue;

            int hp = gameState.unitHealth.getOrDefault(u.getId(), 0);
            int max = gameState.unitMaxHealth.getOrDefault(u.getId(), hp);

            if (hp < max) return u;
        }
        return null;
    }

    private static List<int[]> adjacent(int x, int y) {
        List<int[]> res = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                res.add(new int[]{x + dx, y + dy});
            }
        }
        return res;
    }

    private static boolean isOnBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }

    private static boolean isEnemyAvatar(GameState gameState, String srcOwner, int targetUnitId) {
        if (gameState == null || srcOwner == null) return false;
        int enemyAvatarId = "HUMAN".equals(srcOwner) ? gameState.aiAvatarId : gameState.humanAvatarId;
        return targetUnitId == enemyAvatarId;
    }
}
