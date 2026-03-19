error id: file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/app/utils/UnitDeathUtils.java
file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/app/utils/UnitDeathUtils.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[131,31]

error in qdox parser
file content:
```java
offset: 4327
uri: file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/app/utils/UnitDeathUtils.java
text:
```scala
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

        // --- story card 19 damage abilities trigger ----
        int currentHealth = gameState.unitHealth.getOrDefault(unitId, 0);
        int damage = currentHealth - newHealth;
        if (damage > 0) {
            gameState.damageOnAvatarTrigger(out, unitId, damage);
        }

        // 1) Update server-side health state
        gameState.unitHealth.put(unitId, newHealth);

        // 2) Update UI health label
        if (out != null){
            BasicCommands.setUnitHealth(out, unit, newHealth);
        }

        // --- story card 14 damage/healing ----
        // 3) if unit is avatar, change health
        if (unitId == gameState.humanAvatarId){
            gameState.humanHealth = newHealth;
            if (out != null) {
                BasicCommands.setPlayer1Health(out, new Player(gameState.humanHealth, gameState.humanMana));
            }
        } else if (unitId == gameState.aiAvatarId){
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

        //lin-debug
        if (unitId == gameState.humanAvatarId) {
            gameState.gameOver = true;
            gameState.winner = "AI";
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Game Over! AI wins!", 5);
            }
            return;
        }

        if (unitId == gameState.aiAvatarId) {
            gameState.gameOver = true;
            gameState.winner = "HUMAN";
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Game Over! You win!", 5);
            }
            return;
        }


        // play death animation (returns an estimate of duration)
        if (out != null) {
            int delayMs = BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.death);
            //minimum dealth animation plays 
            sleep(Math.max(delayMs, 2000));

            // delete from UI
            BasicCommands.deleteUnit(out, unit);
        }

        // remove from server-side tracking
        gameState.uiUnitById.remove(unitId);
        gameState.unitHealth.remove(unitId);
        gameState.unitAttack.remove(unitId);
        
        //story card 19: remove from zeal on death
        gameState.zealUnitIds.remove(unitId);
        gameState.provokeUnitIds.remove(unitId);

        // clear Story #17 support maps
        if (gameState.unitMaxHealth != null) gameState.unitMaxHealth.remove(unitId);
        if (gameState.unitOwner != null) gameState.unitOwner.remove(unitId);

        String key = gameState.unitPositionKey.remove(unitId);
        if (key != null) {
            gameState.boardUnits.remove(key);
        } else {
            // fallback: scan board in case position map was not maintained
            gameState.boardUnits.entrySet().removeIf(
                    e -> e.getValue() != null && e.getValue().getId() == unitId
            );
        }
    }

        // SC18: Unit Death Trigger (Deathwatch)
        
        // Traverse all monsters still alive on the field
        for (Unit aliveUnit : g@@ameState.uiUnitById.values()) {
            if (aliveUnit == null) continue;
            
            int aliveId = aliveUnit.getId();
            
            // The living monster cannot be the one that just died
            if (aliveId == unitId) continue; 
            
            String name = gameState.unitName.get(aliveId);
            if (name == null) continue;
            name = name.toLowerCase();

            // 1. Bad Omen (Increase Attack Power by 1 point)
            if (name.equals("bad omen")) {
                int curAtk = gameState.unitAttack.getOrDefault(aliveId, 0);
                gameState.unitAttack.put(aliveId, curAtk + 1);
                if (out != null) BasicCommands.setUnitAttack(out, aliveUnit, curAtk + 1);
            }
            
            // 2. Shadow Watcher (Increase Attack by 1 point and Health by 1 point)
            else if (name.equals("shadow watcher")) {
                int curAtk = gameState.unitAttack.getOrDefault(aliveId, 0);
                gameState.unitAttack.put(aliveId, curAtk + 1);
                if (out != null) BasicCommands.setUnitAttack(out, aliveUnit, curAtk + 1);
                
                int curHp = gameState.unitHealth.getOrDefault(aliveId, 0);
                gameState.unitHealth.put(aliveId, curHp + 1);
                if (out != null) BasicCommands.setUnitHealth(out, aliveUnit, curHp + 1);
            }
            
            // 3. Shadowdancer
            else if (name.equals("shadowdancer")) {
                String owner = gameState.unitOwner.get(aliveId);
                if ("HUMAN".equals(owner)) {
                    // HM
                    int targetId = gameState.aiAvatarId;
                    int newHp = gameState.unitHealth.getOrDefault(targetId, 20) - 1;
                    UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(targetId), newHp);
                    
                    int myId = gameState.humanAvatarId;
                    int myNewHp = gameState.unitHealth.getOrDefault(myId, 20) + 1;
                    UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(myId), myNewHp);
                } else if ("AI".equals(owner)) {
                    // AI
                    int targetId = gameState.humanAvatarId;
                    int newHp = gameState.unitHealth.getOrDefault(targetId, 20) - 1;
                    UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(targetId), newHp);
                    
                    int myId = gameState.aiAvatarId;
                    int myNewHp = gameState.unitHealth.getOrDefault(myId, 20) + 1;
                    UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, gameState.uiUnitById.get(myId), myNewHp);
                }
            }
            
            // 4. Bloodmoon Priestess
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
        
        /* 

        DELETE IF HOPE FIXES DEATH ANIMATION BUG!!

        // remove from server-side tracking
        gameState.uiUnitById.remove(unitId);
        gameState.unitHealth.remove(unitId);
        gameState.unitAttack.remove(unitId);
        
        //story card 19: remove from zeal on death
        gameState.zealUnitIds.remove(unitId);
        gameState.provokeUnitIds.remove(unitId);

        // clear Story #17 support maps
        if (gameState.unitMaxHealth != null) gameState.unitMaxHealth.remove(unitId);
        if (gameState.unitOwner != null) gameState.unitOwner.remove(unitId);

        String key = gameState.unitPositionKey.remove(unitId);
        if (key != null) {
            gameState.boardUnits.remove(key);
        } else {
            // fallback: scan board in case position map was not maintained
            gameState.boardUnits.entrySet().removeIf(
                    e -> e.getValue() != null && e.getValue().getId() == unitId
            );
        }
    }
    */

    private static void sleep(int ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:560)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:691)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:688)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:688)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:940)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/app/utils/UnitDeathUtils.java