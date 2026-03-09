package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;
import structures.basic.Unit;

/**
 * Common summon helpers used by:
 * - Unit card summon (TileClicked)
 * - Spell summon (#30)
 * - Opening gambit summon (#17)
 */
public final class SummonUtils {

    private SummonUtils() {}

    /**
     * Spawn a unit using a unit config json.
     * Returns the spawned unit, or null if failed (e.g., tile occupied).
     */
    public static Unit spawnUnit(ActorRef out, GameState gameState,
                                 String unitConfig,
                                 int tilex, int tiley,
                                 int atk, int hp,
                                 String owner) {

        // occupancy check
        if (gameState.boardUnits.containsKey(gameState.key(tilex, tiley))) return null;

        int unitId = gameState.allocateUnitId();
        Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);

        Unit unit = BasicObjectBuilders.loadUnit(unitConfig, unitId, BetterUnit.class);
        if (unit == null) return null;

        unit.setPositionByTile(tile);

        // UI: draw + stats
        BasicCommands.drawUnit(out, unit, tile);
        BasicCommands.setUnitAttack(out, unit, atk);
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, unit, hp);

        // server maps
        gameState.boardUnits.put(gameState.key(tilex, tiley), unit);
        gameState.uiUnitById.put(unitId, unit);

        gameState.unitAttack.put(unitId, atk);
        gameState.unitPositionKey.put(unitId, gameState.key(tilex, tiley));

        // for story #17
        gameState.unitMaxHealth.put(unitId, hp);
        gameState.unitOwner.put(unitId, owner);

        // Add to SC18: Register name upon summoning
        if (unitConfig.contains("bad_omen")) gameState.unitName.put(unitId, "Bad Omen");
        else if (unitConfig.contains("shadow_watcher")) gameState.unitName.put(unitId, "Shadow Watcher");
        else if (unitConfig.contains("shadowdancer")) gameState.unitName.put(unitId, "Shadowdancer");
        else if (unitConfig.contains("bloodmoon_priestess")) gameState.unitName.put(unitId, "Bloodmoon Priestess");
        else if (unitConfig.contains("wraithling")) gameState.unitName.put(unitId, "Wraithling");

        // --- story card 19 damage trigger ability ---
        // register Zeal units (Silverguard Knight)
        if (unitConfig != null && unitConfig.contains("silverguard_knight")){
            gameState.zealUnitIds.add(unitId);
        }
        
        // --- story card 23: rush ability ---
        boolean hasRush = (unit instanceof BetterUnit) && ((BetterUnit) unit).getHasRush();
        // if unit doesn't have rush, show it moved and attacked 
        if (!hasRush){
            gameState.unitHasMoved.put(unitId, true);
            gameState.unitHadAttacked.put(unitId, true);
        }

        return unit;
    }

    /**
     * Convenience: spawn a 1/1 Wraithling.
     */
    public static Unit spawnWraithling(ActorRef out, GameState gameState,
                                       int tilex, int tiley,
                                       String owner) {
        return spawnUnit(out, gameState,
                "conf/gameconfs/units/wraithling.json",
                tilex, tiley,
                1, 1,
                owner);
    }
}