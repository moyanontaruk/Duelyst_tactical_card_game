package test;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import akka.actor.ActorRef;
import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;
import structures.basic.Unit;
import utils.SummonUtils;
import utils.HighlightUtils;

public class RockPulveriserMovementTest {
    private GameState gameState;
    private ActorRef out;

    @Before
    public void setup() {
        BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
        gameState = new GameState();
        out = null; // BasicCommands handles null ActorRef if altTell is set
    }

    @Test
    public void testRockPulveriserProvokeRegistration() {
        Unit unit = SummonUtils.spawnUnit(out, gameState, 
            "conf/gameconfs/units/rock_pulveriser.json", 2, 2, 1, 4, "HUMAN");
        
        assertNotNull("Unit should be spawned", unit);
        assertTrue("Rock Pulveriser should be in provokeUnitIds", 
            gameState.provokeUnitIds.contains(unit.getId()));
    }

    @Test
    public void testRockPulveriserMovementFlags() {
        Unit unit = SummonUtils.spawnUnit(out, gameState, 
            "conf/gameconfs/units/rock_pulveriser.json", 2, 2, 1, 4, "HUMAN");
        
        assertTrue("Unit should have canMove = true", unit.isCanMove());
        assertTrue("Unit should have canAttack = true", unit.isCanAttack());
    }

    @Test
    public void testRockPulveriserSummoningSickness() {
        Unit unit = SummonUtils.spawnUnit(out, gameState, 
            "conf/gameconfs/units/rock_pulveriser.json", 2, 2, 1, 4, "HUMAN");
        
        assertTrue("Should have moved flag on summon turn", 
            gameState.unitHasMoved.getOrDefault(unit.getId(), false));
    }

    @Test
    public void testRockPulveriserProvokeLogic() {
        // Summon Rock Pulveriser (HUMAN) at (2,2)
        Unit rockPulveriser = SummonUtils.spawnUnit(out, gameState, 
            "conf/gameconfs/units/rock_pulveriser.json", 2, 2, 1, 4, "HUMAN");
        
        // Summon an enemy unit (AI) at (3,2) - adjacent to Rock Pulveriser
        Unit enemy = SummonUtils.spawnUnit(out, gameState, 
            "conf/gameconfs/units/wraithling.json", 3, 2, 1, 1, "AI");
        
        // Enemy should be provoked by Rock Pulveriser
        assertTrue("Enemy should be provoked", HighlightUtils.isProvoked(gameState, enemy));
        
        // Rock Pulveriser should NOT be provoked by itself
        assertFalse("Rock Pulveriser should not be provoked by itself", 
            HighlightUtils.isProvoked(gameState, rockPulveriser));
    }
}
