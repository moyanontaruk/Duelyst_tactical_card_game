import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;

public class DamageHealingTest {
  private GameState gameState;

  @Before
  public void setup(){
    BasicCommands.altTell = new CheckMessageIsNotNullOnTell();

    // create new game state for each test 
    gameState = new GameState();

    //assign ids to avatars
    gameState.humanAvatarId = 100;
    gameState.aiAvatarId = 200;

    // original unit health
    gameState.unitHealth.put(100, 20);
    gameState.unitHealth.put(200, 20);

    //original player health 
    gameState.humanHealth = 20;
    gameState.aiHealth = 20;
  }

  // dealing damage to human avatar
  @Test 
  public void damageToHumanAvatar(){
    gameState.applyDamageToUnit(100, 7);
    assertEquals(13, gameState.humanHealth);
  }

  // dealing damamge to ai avatar
  @Test 
  public void damageToAiAvatar(){
    gameState.applyDamageToUnit(200, 10);
    assertEquals(10, gameState.aiHealth);
  }

  //dealing damage then healing human avatar
  @Test 
  public void damageThenHealingHumanAvatar(){
    gameState.applyDamageToUnit(100, 10);
    gameState.applyHealingToUnit(100, 5);
    assertEquals(15, gameState.humanHealth);
  }

  // damage to regular unit should not affect player health
  @Test 
  public void regularUnitDamageNoEffectOnPlayer(){
    int regularId = gameState.allocateUnitId();
    gameState.unitHealth.put(regularId, 20);
    gameState.applyDamageToUnit(regularId, 5);
    assertEquals(20, gameState.humanHealth);
  }
}
