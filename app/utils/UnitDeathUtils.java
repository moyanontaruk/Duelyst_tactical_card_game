package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;

/**
 * Story Card #13 (Unit Death)
 *
 * When a unit's health is changed, we must check if it has reached 0 (or below).
 * If so, play the death animation and then delete the unit from the board.
 *
 * Note: the provided Unit class is mainly a UI representation, so we store server-side
 * health/attack in GameState.
 */
public class UnitDeathUtils {

    private UnitDeathUtils() {}

    /**
     * Set a unit's health (server + UI) and apply Story #13 death behaviour.
     */
    public static void setUnitHealthAndCheckDeath(ActorRef out, GameState gameState, Unit unit, int newHealth) {
        if (unit == null) return;
        int unitId = unit.getId();

        // 1) Update server-side health state
        gameState.unitHealth.put(unitId, newHealth);

        // 2) Update UI health label
        BasicCommands.setUnitHealth(out, unit, newHealth);

        // 3) If health <= 0, kill the unit
        if (newHealth <= 0) {
            killUnit(out, gameState, unit);
        }
    }

    /**
     * Remove a unit from UI and server-side board state.
     */
    public static void killUnit(ActorRef out, GameState gameState, Unit unit) {
        if (unit == null) return;

        // play death animation (returns an estimate of duration)
        int delayMs = BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.death);
        sleep(delayMs);

        // delete from UI
        BasicCommands.deleteUnit(out, unit);

        // remove from server-side tracking
        int unitId = unit.getId();
        gameState.uiUnitById.remove(unitId);
        gameState.unitHealth.remove(unitId);
        gameState.unitAttack.remove(unitId);

        String key = gameState.unitPositionKey.remove(unitId);
        if (key != null) {
            gameState.boardUnits.remove(key);
        } else {
            // fallback: scan board in case position map was not maintained
            gameState.boardUnits.entrySet().removeIf(e -> e.getValue() != null && e.getValue().getId() == unitId);
        }
    }

    private static void sleep(int ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
