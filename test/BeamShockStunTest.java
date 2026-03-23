import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.SimpleAI;
import utils.StaticConfFiles;
import utils.StunRules;

public class BeamShockStunTest {
    private GameState gameState;

    @Before
    public void setup() {
        BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
        gameState = new GameState();
        gameState.humanAvatarId = 100;
        gameState.aiAvatarId = 200;
        gameState.humanHealth = 20;
        gameState.aiHealth = 20;
        gameState.turnNumber = 1;
    }

    @Test
    public void stunIsTrackedForOwnersNextTurnAndClearsAfterThatTurnEnds() {
        Unit aiUnit = addUnit("conf/gameconfs/units/wraithling.json", 1001, 5, 2, 1, 1, "AI");

        boolean ok = StunRules.applyStunToUnit(null, gameState, aiUnit);
        assertTrue(ok);

        gameState.activePlayer = "HUMAN";
        assertFalse(StunRules.isStunnedThisTurn(gameState, aiUnit.getId()));

        gameState.activePlayer = "AI";
        assertTrue(StunRules.isStunnedThisTurn(gameState, aiUnit.getId()));

        StunRules.clearStunsForEndingPlayer(gameState, "AI");
        assertFalse(gameState.stunnedUntilEndOfOwnersTurn.containsKey(aiUnit.getId()));
    }

    @Test
    public void stunnedAiUnitCannotMoveOrAttackDuringAiTurn() {
        Unit humanAvatar = addAvatar(StaticConfFiles.humanAvatar, gameState.humanAvatarId, 1, 2, "HUMAN");
        Unit aiAvatar = addAvatar(StaticConfFiles.aiAvatar, gameState.aiAvatarId, 7, 2, "AI");

        Unit aiUnit = addUnit("conf/gameconfs/units/wraithling.json", 1001, 2, 2, 2, 2, "AI");

        boolean ok = StunRules.applyStunToUnit(null, gameState, aiUnit);
        assertTrue(ok);

        gameState.activePlayer = "AI";
        gameState.aiMana = -1;

        SimpleAI.takeTurn(null, gameState);

        assertEquals("HUMAN", gameState.activePlayer);
        assertEquals(20, gameState.humanHealth);
        assertEquals(gameState.key(2, 2), gameState.unitPositionKey.get(aiUnit.getId()));
        assertEquals(aiUnit.getId(), gameState.boardUnits.get(gameState.key(2, 2)).getId());
        assertFalse(gameState.stunnedUntilEndOfOwnersTurn.containsKey(aiUnit.getId()));

        assertNotNull(humanAvatar);
        assertNotNull(aiAvatar);
    }

    private Unit addAvatar(String cfg, int id, int x, int y, String owner) {
        Unit avatar = BasicObjectBuilders.loadUnit(cfg, id, Unit.class);
        assertNotNull(avatar);

        Tile tile = BasicObjectBuilders.loadTile(x, y);
        avatar.setPositionByTile(tile);

        gameState.boardUnits.put(gameState.key(x, y), avatar);
        gameState.uiUnitById.put(id, avatar);
        gameState.unitOwner.put(id, owner);
        gameState.unitPositionKey.put(id, gameState.key(x, y));

        gameState.unitAttack.put(id, 2);
        gameState.unitHealth.put(id, 20);
        gameState.unitMaxHealth.put(id, 20);

        return avatar;
    }

    private Unit addUnit(String cfg, int id, int x, int y, int atk, int hp, String owner) {
        Unit unit = BasicObjectBuilders.loadUnit(cfg, id, BetterUnit.class);
        assertNotNull(unit);

        Tile tile = BasicObjectBuilders.loadTile(x, y);
        unit.setPositionByTile(tile);
        unit.setAttack(atk);
        unit.setHealth(hp);

        gameState.boardUnits.put(gameState.key(x, y), unit);
        gameState.uiUnitById.put(id, unit);
        gameState.unitOwner.put(id, owner);
        gameState.unitPositionKey.put(id, gameState.key(x, y));

        gameState.unitAttack.put(id, atk);
        gameState.unitHealth.put(id, hp);
        gameState.unitMaxHealth.put(id, hp);

        gameState.unitHasMoved.put(id, false);
        gameState.unitHadAttacked.put(id, false);

        return unit;
    }
}
