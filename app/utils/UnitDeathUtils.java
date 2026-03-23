package utils;

import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;
import structures.basic.Player;

/**
 * Story Card #13 (Unit Death)
 *
 * When a unit's health is changed, we must check if it has reached 0 (or below).
 * If so, play the death animation and then delete the unit from the board.
 */
public class UnitDeathUtils {

    private UnitDeathUtils() {}

    /**
     * Set a unit's health (server + UI) and apply Story #13 death behaviour.
     */
public static void setUnitHealthAndCheckDeath(ActorRef out, GameState gameState, Unit unit, int newHealth) {
    if (unit == null) return;

    int unitId = unit.getId();

    int currentHealth = gameState.unitHealth.getOrDefault(unitId, 0);
    int damage = currentHealth - newHealth;
    if (damage > 0) {
        gameState.damageOnAvatarTrigger(out, unitId, damage);
    }


    // clamp to zero so UI never shows negative health
    // stop it from going over max health 20 too
    int maxHealth = gameState.unitMaxHealth.getOrDefault(unitId, 20);
    newHealth = Math.max(0, Math.min(newHealth, maxHealth));


    // 1) Update server-side health state
    gameState.unitHealth.put(unitId, newHealth);

    // 2) Update UI health label on the unit
    if (out != null) {
        BasicCommands.setUnitHealth(out, unit, newHealth);
    }

    // 3) If unit is avatar, also update player health UI
    if (unitId == gameState.humanAvatarId) {
        gameState.humanHealth = newHealth;
        if (out != null) {
            BasicCommands.setPlayer1Health(out, new Player(gameState.humanHealth, gameState.humanMana));
        }
    } else if (unitId == gameState.aiAvatarId) {
            gameState.aiHealth = newHealth;
            if (out != null) {
                BasicCommands.setPlayer2Health(out, new Player(gameState.aiHealth, gameState.aiMana));
            }
        }



    // 4) If health <= 0, kill the unit
    if (newHealth <= 0) {
        killUnit(out, gameState, unit);
    }
}

    /**
     * Remove a unit from UI and server-side board state.
     */
public static void killUnit(ActorRef out, GameState gameState, Unit unit) {
    if (unit == null) return;

    int unitId = unit.getId();
    boolean isHumanAvatar = (unitId == gameState.humanAvatarId);
    boolean isAiAvatar = (unitId == gameState.aiAvatarId);

    // set winner / game over for avatars, but DO NOT return early
    if (isHumanAvatar) {
        gameState.humanHealth = 0;
        gameState.gameOver = true;
        gameState.winner = "AI";
        if (out != null) {
            BasicCommands.setPlayer1Health(out, new Player(gameState.humanHealth, gameState.humanMana));
        }
    }

    if (isAiAvatar) {
        gameState.aiHealth = 0;
        gameState.gameOver = true;
        gameState.winner = "HUMAN";
        if (out != null) {
            BasicCommands.setPlayer2Health(out, new Player(gameState.aiHealth, gameState.aiMana));
        }
    }

    // play death animation
    if (out != null) {
        int delayMs = BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.death);
        sleep(delayMs + 500);

        BasicCommands.deleteUnit(out, unit);
        sleep(300);
    }

    // remove from server-side tracking
    gameState.uiUnitById.remove(unitId);
    gameState.unitHealth.remove(unitId);
    gameState.unitAttack.remove(unitId);
    gameState.unitHasMoved.remove(unitId);
    gameState.unitHadAttacked.remove(unitId);

    gameState.zealUnitIds.remove(unitId);
    gameState.provokeUnitIds.remove(unitId);

    if (gameState.unitMaxHealth != null) gameState.unitMaxHealth.remove(unitId);
    if (gameState.unitOwner != null) gameState.unitOwner.remove(unitId);
    if (gameState.unitName != null) gameState.unitName.remove(unitId);

    String key = gameState.unitPositionKey.remove(unitId);
    if (key != null) {
        gameState.boardUnits.remove(key);
    } else {
        gameState.boardUnits.entrySet().removeIf(
                e -> e.getValue() != null && e.getValue().getId() == unitId
        );
    }

    //HighlightUtils.clearHighlightedTiles(out, gameState);

    // for avatar deaths, show notification AFTER deletion
    if (isHumanAvatar) {
        if (out != null) {
            BasicCommands.addPlayer1Notification(out, "Game Over! AI wins!", 5);
        }
        return;
    }

    if (isAiAvatar) {
        if (out != null) {
            BasicCommands.addPlayer1Notification(out, "Game Over! You win!", 5);
        }
        return;
    }

    // SC18: Unit Death Trigger (Deathwatch) - non-avatar units only
    List<Unit> aliveSnapshot = new ArrayList<>(gameState.uiUnitById.values());
    for (Unit aliveUnit : aliveSnapshot) {
        if (aliveUnit == null) continue;

        int aliveId = aliveUnit.getId();
        if (aliveId == unitId) continue;

        String name = gameState.unitName.get(aliveId);
        if (name == null) continue;
        name = name.toLowerCase();

        if (name.equals("bad omen")) {
            int curAtk = gameState.unitAttack.getOrDefault(aliveId, 0);
            gameState.unitAttack.put(aliveId, curAtk + 1);
            if (out != null) BasicCommands.setUnitAttack(out, aliveUnit, curAtk + 1);
        }

    else if (name.equals("shadow watcher")) {

    // --- Attack ---
    int curAtk = gameState.unitAttack.getOrDefault(aliveId, 0);
    int newAtk = curAtk + 1;
    gameState.unitAttack.put(aliveId, newAtk);

    if (out != null) {
        BasicCommands.setUnitAttack(out, aliveUnit, newAtk);
    }

    // --- Health + Max Health ---
    int curHp = gameState.unitHealth.getOrDefault(aliveId, 0);
    int curMax = gameState.unitMaxHealth.getOrDefault(aliveId, curHp);

    int newMax = curMax + 1;
    int newHp = curHp + 1;

    gameState.unitMaxHealth.put(aliveId, newMax);

    // use your centralized method (important)
    UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, aliveUnit, newHp);
}

        else if (name.equals("shadowdancer")) {
            String owner = gameState.unitOwner.get(aliveId);
            if ("HUMAN".equals(owner)) {
                int targetId = gameState.aiAvatarId;
                int newHp = gameState.unitHealth.getOrDefault(targetId, 20) - 1;
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(targetId), newHp);

                int myId = gameState.humanAvatarId;
                int myNewHp = gameState.unitHealth.getOrDefault(myId, 20) + 1;
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(myId), myNewHp);
            } else if ("AI".equals(owner)) {
                int targetId = gameState.humanAvatarId;
                int newHp = gameState.unitHealth.getOrDefault(targetId, 20) - 1;
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(targetId), newHp);

                int myId = gameState.aiAvatarId;
                int myNewHp = gameState.unitHealth.getOrDefault(myId, 20) + 1;
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(myId), myNewHp);
            }
        }

        else if (name.equals("bloodmoon priestess")) {
            String posKey = gameState.unitPositionKey.get(aliveId);
            if (posKey != null) {
                String[] parts = posKey.split(",");
                int px = Integer.parseInt(parts[0]);
                int py = Integer.parseInt(parts[1]);
                String owner = gameState.unitOwner.get(aliveId);

                // need to add in all 8 directions
                // andd make sure it's not just the first open tile

                //ALL valid empty adjacent tiles here
                List<int[]> emptyAdjacent = new ArrayList<>();

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {

                        // Skip (0,0) for priestess's own tile
                        if (dx == 0 && dy == 0)
                            continue;

                        int tx = px + dx;
                        int ty = py + dy;

                        // ignore tiles that are off the board
                        if (tx < 0 || tx >= 9 || ty < 0 || ty >= 5)
                            continue;

                        // build the board key for this candidate tile
                        String targetKey = gameState.key(tx, ty);

                        // only keep the tile if nothing is currently on it
                        if (!gameState.boardUnits.containsKey(targetKey)) {
                            emptyAdjacent.add(new int[]{tx, ty});
                        }
                    }
                }

                // If there are no empty adjacent tiles, the effect does nothing
                if (emptyAdjacent.isEmpty()) {
                    return;
                }

                // debugging issue --randomly choose one empty adjacent tile and not the just the first avail

                int idx = (int) (Math.random() * emptyAdjacent.size());
                int[] chosen = emptyAdjacent.get(idx);

                // spawn on the randomly chosen tile
                SummonUtils.spawnWraithling(out, gameState, chosen[0], chosen[1], owner);
            }
        }
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