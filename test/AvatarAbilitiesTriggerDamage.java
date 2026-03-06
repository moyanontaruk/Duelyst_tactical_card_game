import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;

import structures.GameState;
import structures.basic.Unit;
import structures.basic.UnitAnimationSet;
import structures.basic.ImageCorrection;

public class AvatarAbilitiesTriggerDamage {
  
  //creating a game state for testing
  private GameState gameState;

  @Before
  public void setup(){
    BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
    gameState = new GameState();

    //assign avatar ID
    gameState.humanAvatarId = 100;
    gameState.aiAvatarId = 200;

    //starting health for both avatars
    gameState.unitHealth.put(100, 20);
    gameState.unitHealth.put(200, 20);
    gameState.humanHealth = 20;
    gameState.aiHealth = 20;
  }

  // --- test 1: Zeal unit shouldn't +2 attack when owner is not attacked ---
  @Test 
  public void zealNoDamageTriggerOnHumanAvatar() {
    //register Zeal unit owned by Human
    int zealId = gameState.allocateUnitId();
    gameState.unitAttack.put(zealId, 1);
    gameState.unitOwner.put(zealId, "HUMAN");
    gameState.zealUnitIds.add(zealId);

    //Ai avatar takes damage 
    gameState.damageOnAvatarTrigger(null, 200, 5);

    //Zeal shouldn't  +2 bc Human Avatar was not hit
    assertEquals(1, (int) gameState.unitAttack.get(zealId));
  }

  // --- test 2: Zeal triggers when owner is hit ---
  @Test
  public void zealDamageTriggerOnAiAvatar() {
    //register Zeal unit owned by Human
    int zealId = gameState.allocateUnitId();
    gameState.unitAttack.put(zealId, 1);
    gameState.unitOwner.put(zealId, "HUMAN");
    gameState.zealUnitIds.add(zealId);

    //Human avatar takes damage 
    gameState.damageOnAvatarTrigger(null, 100, 5);

    //Zeal should +2 bc Human Avatar was hit
    assertEquals(3, (int) gameState.unitAttack.get(zealId));
  }

  // --- test 3: Zeal does not trigger when non-avatar takes damage ---
  @Test
  public void zealNoTriggerOnNonAvatarDamage() {
    //register Zeal unit owned by AI
    int zealId = gameState.allocateUnitId();
    gameState.unitAttack.put(zealId, 1);
    gameState.unitOwner.put(zealId, "HUMAN");
    gameState.zealUnitIds.add(zealId);

    //Non-avatar unit takes damage
    int nonAvatarId = gameState.allocateUnitId();
    gameState.damageOnAvatarTrigger(null, nonAvatarId, 5);

    //Zeal shouldn't +2 bc damage was not on avatar
    assertEquals(1, (int) gameState.unitAttack.get(zealId));
  }

  // --- test 4: Horn Robustness decreases when human takes damage ---
  @Test
  public void hornRobustnessDecreasesOnHumanAvatarDamage() {
    //starting robustness for Horn of the Forsaken
    gameState.hornOfForsaken = true;
    gameState.hornRobustness = 3;

    //Human avatar takes damage
    gameState.damageOnAvatarTrigger(null, 100, 5);

    //Horn robustness should decrease by 1
    assertEquals(2, gameState.hornRobustness);
    assertTrue(gameState.hornOfForsaken);
  }

  // test 5: horn is destroyed when robustness reaches 0
  @Test
  public void hornDestroyedWhenRobustnessZero() {
    //starting robustness for Horn of the Forsaken
    gameState.hornOfForsaken = true;
    gameState.hornRobustness = 1;

    //Human avatar takes damage
    gameState.damageOnAvatarTrigger(null, 100, 5);

    //Horn should be destroyed
    assertFalse(gameState.hornOfForsaken);
    assertEquals(0, gameState.hornRobustness);
  }

  // test 6: horn does not decrease robustness when AI takes damage
  @Test
  public void hornNoRobustnessDecreaseOnAiAvatarDamage() {
    //starting robustness for Horn of the Forsaken
    gameState.hornOfForsaken = true;
    gameState.hornRobustness = 3;

    //AI avatar takes damage
    gameState.damageOnAvatarTrigger(null, 200, 5);

    //Horn robustness should not decrease
    assertEquals(3, gameState.hornRobustness);
    assertTrue(gameState.hornOfForsaken);
  }

}

