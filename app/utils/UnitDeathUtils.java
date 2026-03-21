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
            int curAtk = gameState.unitAttack.getOrDefault(aliveId, 0);
            gameState.unitAttack.put(aliveId, curAtk + 1);
            if (out != null) BasicCommands.setUnitAttack(out, aliveUnit, curAtk + 1);

            int curHp = gameState.unitHealth.getOrDefault(aliveId, 0);
            int maxHp = gameState.unitMaxHealth.getOrDefault(aliveId, curHp);

            int newHp = Math.min(curHp + 1, maxHp);
            gameState.unitHealth.put(aliveId,newHp);

            if (out != null) {
                BasicCommands.setUnitHealth(out, aliveUnit, newHp);
            }
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

                int[][] neighbors = {{1,0}, {-1,0}, {0,1}, {0,-1}};
                for (int[] offset : neighbors) {
                    int tx = px + offset[0];
                    int ty = py + offset[1];
                    if (tx >= 0 && tx < 9 && ty >= 0 && ty < 5) {
                        if (!gameState.boardUnits.containsKey(gameState.key(tx, ty))) {
                            utils.SummonUtils.spawnWraithling(out, gameState, tx, ty, owner);
                            break;
                        }
                    }
                }
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