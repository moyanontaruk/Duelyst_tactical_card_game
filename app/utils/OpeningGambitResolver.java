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

        // ------------------------------------------------------------
        // Gloom Chaser: summon a 1/1 Wraithling directly behind this unit.
        // (Human "behind" = x-1)
        // ------------------------------------------------------------
        if (name.equals("gloom chaser")) {
            int x = summonedUnit.getPosition().getTilex();
            int y = summonedUnit.getPosition().getTiley();

            int bx = x - 1;
            int by = y;

            if (isOnBoard(bx, by) && !gameState.boardUnits.containsKey(gameState.key(bx, by))) {
                SummonUtils.spawnWraithling(out, gameState, bx, by, "HUMAN");
            }
            return;
        }

        // ------------------------------------------------------------
        // Nightsorrow Assassin:
        // destroy a nearby enemy minion that is damaged (hp < max hp)
        // We pick the first adjacent enemy that matches.
        // ------------------------------------------------------------
        if (name.equals("nightsorrow assassin")) {
            Unit target = firstAdjacentEnemyBelowMax(gameState, summonedUnit, "HUMAN");
            if (target != null) {
                // set to 0 triggers death logic (#13)
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, 0);
            }
            return;
        }

        // ------------------------------------------------------------
        // Silverguard Squire:
        // give +1/+1 to allied units directly in-front and behind.
        // (Human: in-front = x+1, behind = x-1)
        // ------------------------------------------------------------
        if (name.equals("silverguard squire")) {
            int x = summonedUnit.getPosition().getTilex();
            int y = summonedUnit.getPosition().getTiley();

            buffIfAllied(out, gameState, x + 1, y, "HUMAN");
            buffIfAllied(out, gameState, x - 1, y, "HUMAN");
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
}