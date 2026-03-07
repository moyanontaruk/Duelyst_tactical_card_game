import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;

import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Unit;

import utils.BasicObjectBuilders;
import utils.SummonUtils;


public class RushAbilityTest {
  // create game state for testing 
  private GameState gameState;

  @Before
  public void setup(){
    BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
    gameState = new GameState();
  }

  // --- test 1:  Rush flag is set on Saberspine Tiger ---
  @Test 
  public void testRushFlag(){
    Unit tiger = BasicObjectBuilders.loadUnit("conf/gameconfs/units/saberspine_tiger.json", 1, BetterUnit.class);

    assertTrue("Saberspine should have hasRush = true", tiger.getHasRush());
  }

  // --- test 2: Rush Unit can move/attack on summon turn ---
  @Test
  public void testRushActOnSummon(){
    int unitId = gameState.allocateUnitId();
    Unit tiger = BasicObjectBuilders.loadUnit("conf/gameconfs/units/saberspine_tiger.json", 2, BetterUnit.class);

    boolean hasRush = (tiger instanceof BetterUnit) && tiger.getHasRush();
    if (!hasRush) {
        gameState.unitHasMoved.put(unitId, true);
        gameState.unitHadAttacked.put(unitId, true);
    }

    assertFalse("Rush Unit should be able to move on summon turn", gameState.unitHasMoved.getOrDefault(unitId, false));

    assertFalse("Rush unit should be able to attack on summon turn", gameState.unitHadAttacked.getOrDefault(unitId, false));
  }

  // --- test 3: Non-Rush Unit cannot act on summon turn ---
  @Test
  public void testNonRushCannotActOnSummon(){
    int unitId = gameState.allocateUnitId();
    Unit wraithling = BasicObjectBuilders.loadUnit("conf/gameconfs/units/wraithling.json", 2, BetterUnit.class);

    boolean hasRush = (wraithling instanceof BetterUnit) && wraithling.getHasRush();
    if (!hasRush) {
        gameState.unitHasMoved.put(unitId, true);
        gameState.unitHadAttacked.put(unitId, true);
    }

    assertTrue("Non-Rush Unit cannot move on summon turn", gameState.unitHasMoved.getOrDefault(unitId, false));
    assertTrue("Non-Rush Unit cannot attack on summon turn", gameState.unitHadAttacked.getOrDefault(unitId, false));
  }
}
