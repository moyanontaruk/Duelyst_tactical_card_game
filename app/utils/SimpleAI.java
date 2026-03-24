package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import utils.StaticConfFiles;

public final class SimpleAI {

    private static final String AI_OWNER = "AI";
    private static final String HUMAN_OWNER = "HUMAN";
    private static final int MAX_SPELL_ACTIONS_PER_TURN = 10;
    private static final int MAX_SUMMON_ACTIONS_PER_TURN = 10;

    private SimpleAI() {}

    public static void takeTurn(ActorRef out, GameState gameState) {
        // Basic safety checks first.
        // If the game is already over, or this is not actually the AI turn,
        // we should quietly do nothing.
        if (gameState == null || gameState.gameOver) return;
        if (!AI_OWNER.equals(gameState.activePlayer)) return;

        try {
            // Tiny pause so the turn feels less abrupt in the UI.
            sleep(100);

            // Step 1:
            // Let the AI spend mana on useful spells first.
            // The hard cap is important so we never get stuck in a bad loop.
            playAffordableSpells(out, gameState);

            // Step 2:
            // After spells, try to put creatures on the board.
            // Same idea here: keep it productive, but always bounded.
            playAffordableUnits(out, gameState);

            // Step 3:
            // Finally let the units already on the board do their attacks/moves.
            playUnits(out, gameState);
            sleep(100);

        } catch (Exception e) {
            // If anything unexpected happens, fail safely and end the AI turn
            // rather than leaving the match hanging.
            e.printStackTrace();
            BasicCommands.addPlayer1Notification(out, "AI error - ending turn", 2);
        } finally {
            // No matter what happens above, return the game to the normal turn flow
            // as long as the match is still alive and it is still the AI turn.
            if (gameState != null && !gameState.gameOver && AI_OWNER.equals(gameState.activePlayer)) {
                new events.EndTurnClicked().processEvent(out, gameState, null);
            }
        }
    }

    // ------------------------------------------------------------
    // MAIN UNIT PLAY LOOP
    // ------------------------------------------------------------

    private static void playUnits(ActorRef out, GameState gameState) {
        // First pass:
        // any AI unit that is already beside an enemy gets a chance to attack now.
        attackWithAdjacentUnits(out, gameState);

        // Small pause so animations and state updates settle cleanly.
        sleep(200);

        // Second pass:
        // move the remaining units that are still allowed to move this turn.
        moveUnitsTowardEnemies(out, gameState);

        // Give movement animations enough time to finish before checking attacks again.
        sleep(500);

        // Third pass:
        // after movement, some units may now be adjacent to enemies,
        // so we run the same attack check one more time.
        attackWithAdjacentUnits(out, gameState);
    }

    private static void attackWithAdjacentUnits(ActorRef out, GameState gameState) {
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, AI_OWNER);

        for (Unit unit : aiUnits) {
            if (unit == null) continue;

            // Stop immediately if the turn or game state changed mid-loop.
            if (gameState.gameOver) return;
            if (!AI_OWNER.equals(gameState.activePlayer)) return;

            int unitId = unit.getId();

            // Stunned units cannot act this turn.
            if (StunRules.isStunnedThisTurn(gameState, unitId)) continue;

            // A unit only gets one attack.
            if (gameState.unitHadAttacked.getOrDefault(unitId, false)) continue;

            // Only attack if there is actually a legal adjacent enemy target.
            Unit target = findAdjacentEnemy(gameState, unit, HUMAN_OWNER);
            if (target == null) continue;

            doAttack(out, gameState, unit, target);
            sleep(180);
        }
    }

    private static void moveUnitsTowardEnemies(ActorRef out, GameState gameState) {
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, AI_OWNER);

        for (Unit unit : aiUnits) {
            if (unit == null) continue;

            // As above, if the turn stopped being valid while we were iterating,
            // just stop cleanly.
            if (gameState.gameOver) return;
            if (!AI_OWNER.equals(gameState.activePlayer)) return;

            int unitId = unit.getId();

            // These checks mirror the existing behaviour exactly:
            // - stunned units do nothing
            // - units that have already attacked do not move here
            // - units that already moved do not move again
            if (StunRules.isStunnedThisTurn(gameState, unitId)) continue;
            if (gameState.unitHadAttacked.getOrDefault(unitId, false)) continue;
            if (gameState.unitHasMoved.getOrDefault(unitId, false)) continue;

            // If the unit is already next to an enemy, leave it in place for the attack phase.
            Unit adjacentEnemy = findAdjacentEnemy(gameState, unit, HUMAN_OWNER);
            if (adjacentEnemy != null) continue;

            // If the unit is provoked and not already in attack range, it cannot move away.
            if (HighlightUtils.isProvoked(gameState, unit)) continue;

            int[] bestMove = chooseBestMoveTile(gameState, unit);
            if (bestMove == null) continue;

            boolean moved = moveUnit(out, gameState, unit, bestMove[0], bestMove[1]);
            if (moved) {
                gameState.unitHasMoved.put(unitId, true);

                // Slight pause after a successful move so the board state does not feel rushed.
                sleep(350);
            }
        }
    }

    // ------------------------------------------------------------
    // SPELLS
    // ------------------------------------------------------------

    private static boolean tryCastBestAffordableSpell(ActorRef out, GameState gameState) {
        // Work on a copy of the hand so iteration stays safe even if the real hand changes.
        List<String> aiCards = new ArrayList<>(gameState.aiHand);
        if (aiCards.isEmpty()) return false;

        List<String> affordableSpellCfgs = new ArrayList<>();
        List<Card> affordableSpells = new ArrayList<>();

        // Collect only spell cards the AI can currently afford.
        for (String cfg : aiCards) {
            Card card = BasicObjectBuilders.loadCard(cfg, 9100, Card.class);
            if (card == null) continue;
            if (card.isCreature()) continue;
            if (card.getManacost() > gameState.aiMana) continue;

            affordableSpellCfgs.add(cfg);
            affordableSpells.add(card);
        }

        if (affordableSpells.isEmpty()) return false;

        // Check affordable spells in hand order and play the first one
        // that has a valid target and succeeds.
        for (int i = 0; i < affordableSpells.size(); i++) {
            String cfg = affordableSpellCfgs.get(i);
            Card card = affordableSpells.get(i);
            String cardName = normalize(card.getCardname());

            if (tryCastBeamShock(out, gameState, cfg, card, cardName)) {
                return true;
            }

            if (tryCastTruestrike(out, gameState, cfg, card, cardName)) {
                return true;
            }

            if (tryCastSundropElixir(out, gameState, cfg, card, cardName)) {
                return true;
            }
        }

        return false;
    }

    private static void playAffordableSpells(ActorRef out, GameState gameState) {
        int spellActions = 0;

        while (!gameState.gameOver
                && AI_OWNER.equals(gameState.activePlayer)
                && spellActions < MAX_SPELL_ACTIONS_PER_TURN) {

            boolean playedSpell = tryCastBestAffordableSpell(out, gameState);
            if (!playedSpell) {
                break;
            }

            spellActions++;
            sleep(100);
        }
    }

    private static void playAffordableUnits(ActorRef out, GameState gameState) {
        int summonActions = 0;

        while (!gameState.gameOver
                && AI_OWNER.equals(gameState.activePlayer)
                && summonActions < MAX_SUMMON_ACTIONS_PER_TURN) {

            boolean summonedUnit = trySummonBestAffordableUnit(out, gameState);
            if (!summonedUnit) {
                break;
            }

            summonActions++;
            sleep(120);
        }
    }

    private static boolean tryCastBeamShock(
            ActorRef out,
            GameState gameState,
            String cfg,
            Card card,
            String cardName
    ) {
        if (!cardName.equals("beam shock") && !cardName.equals("beamshock")) {
            return false;
        }

        Unit target = chooseBestAiBeamShockTarget(gameState);
        if (target == null) return false;

        boolean applied = StunRules.applyStunToUnit(out, gameState, target);
        if (!applied) return false;

        spendAiManaAndRemoveCard(out, gameState, cfg, card);
        return true;
    }

    private static boolean tryCastTruestrike(
            ActorRef out,
            GameState gameState,
            String cfg,
            Card card,
            String cardName
    ) {
        if (!cardName.equalsIgnoreCase("Truestrike") && !cardName.equalsIgnoreCase("true strike")) {
            return false;
        }

        Unit target = chooseBestAiEnemyUnitTarget(gameState);
        if (target == null) return false;

        int currentHp = gameState.unitHealth.getOrDefault(target.getId(), 0);
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, currentHp - 2);

        spendAiManaAndRemoveCard(out, gameState, cfg, card);
        return true;
    }

    private static boolean tryCastSundropElixir(
            ActorRef out,
            GameState gameState,
            String cfg,
            Card card,
            String cardName
    ) {
        if (!cardName.equals("sundrop elixir")) {
            return false;
        }

        Unit target = chooseBestAiHealTarget(gameState);
        if (target == null) return false;

        boolean healed = HealSpellUtils.healUnit(
                out,
                gameState,
                target,
                5,          // heal amount stays exactly the same
                AI_OWNER,
                false,
                null
        );

        if (!healed) return false;

        spendAiManaAndRemoveCard(out, gameState, cfg, card);
        return true;
    }

    private static void spendAiManaAndRemoveCard(
            ActorRef out,
            GameState gameState,
            String cfg,
            Card card
    ) {
        // Shared post-cast housekeeping:
        // spend mana, refresh UI mana display, and remove the used card from hand.
        gameState.aiMana -= card.getManacost();
        BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));
        gameState.aiHand.remove(cfg);
    }

    // ------------------------------------------------------------
    // SUMMONING
    // ------------------------------------------------------------

    private static boolean trySummonBestAffordableUnit(ActorRef out, GameState gameState) {
        // Work on a copy of the hand so we can inspect cards safely
        // even if the real hand changes later in the method.
        List<String> aiCards = new ArrayList<>(gameState.aiHand);
        if (aiCards.isEmpty()) return false;

        // First gather only creature cards that the AI can actually afford right now.
        List<String> affordableUnits = collectAffordableUnitCards(gameState, aiCards);
        if (affordableUnits.isEmpty()) return false;

        // Keep the existing behaviour:
        // higher-cost creatures are tried first.
        sortUnitsByDescendingManaCost(affordableUnits);

        // Try each affordable creature until one is successfully summoned.
        for (String cfg : affordableUnits) {
            Card card = BasicObjectBuilders.loadCard(cfg, 9003, Card.class);
            if (!isSummonableUnitCard(card)) continue;

            int[] tile = chooseBestSummonTile(gameState);
            if (tile == null) continue;

            String summonKey = gameState.key(tile[0], tile[1]);
            if (gameState.boardUnits.containsKey(summonKey)) continue;

            Unit summonedUnit = summonAiUnitAtTile(out, gameState, card, tile[0], tile[1]);
            if (summonedUnit == null) continue;

            // Same post-summon housekeeping as before:
            // spend mana, update mana UI, remove card from hand,
            // then resolve opening gambit if that card has one.
            gameState.aiMana -= card.getManacost();
            BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));
            gameState.aiHand.remove(cfg);

            OpeningGambitResolver.onSummoned(out, gameState, summonedUnit, card.getCardname());
            return true;
        }

        return false;
    }

    private static List<String> collectAffordableUnitCards(GameState gameState, List<String> aiCards) {
        List<String> affordableUnits = new ArrayList<>();

        for (String cfg : aiCards) {
            Card card = BasicObjectBuilders.loadCard(cfg, 9000, Card.class);
            if (card == null) continue;
            if (!card.isCreature()) continue;
            if (card.getManacost() > gameState.aiMana) continue;

            affordableUnits.add(cfg);
        }

        return affordableUnits;
    }

    private static void sortUnitsByDescendingManaCost(List<String> affordableUnits) {
        affordableUnits.sort((a, b) -> {
            Card cardA = BasicObjectBuilders.loadCard(a, 9001, Card.class);
            Card cardB = BasicObjectBuilders.loadCard(b, 9002, Card.class);

            int manaA = (cardA == null) ? -1 : cardA.getManacost();
            int manaB = (cardB == null) ? -1 : cardB.getManacost();

            return Integer.compare(manaB, manaA);
        });
    }

    private static boolean isSummonableUnitCard(Card card) {
        // These checks were already present in the original method.
        // Keeping them grouped makes it clearer what a "usable summon card" means here.
        return card != null
                && card.getBigCard() != null
                && card.getUnitConfig() != null;
    }

    private static Unit summonAiUnitAtTile(
            ActorRef out,
            GameState gameState,
            Card card,
            int tileX,
            int tileY
    ) {
        Tile summonTile = BasicObjectBuilders.loadTile(tileX, tileY);
        EffectAnimation summonFx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);

        // Play the summon effect first if it exists
        if (summonFx != null) {
            BasicCommands.playEffectAnimation(out, summonFx, summonTile);
            sleep(250);
        }

        Unit unit = SummonUtils.spawnUnit(
                out,
                gameState,
                card.getUnitConfig(),
                tileX,
                tileY,
                card.getBigCard().getAttack(),
                card.getBigCard().getHealth(),
                "AI"
        );

        sleep(150);
        return unit;
    }

private static int[] chooseBestSummonTile(GameState gameState) {
    List<int[]> candidates = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (Unit unit : gameState.boardUnits.values()) {
        if (unit == null) continue;

        String owner = gameState.unitOwner.get(unit.getId());
        if (!"AI".equals(owner)) continue;

        int unitX = unit.getPosition().getTilex();
        int unitY = unit.getPosition().getTiley();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int x = unitX + dx;
                int y = unitY + dy;

                if (!isOnBoard(x, y)) continue;
                if (occupied(gameState, x, y)) continue;

                String key = gameState.key(x, y);
                if (!seen.add(key)) continue;

                candidates.add(new int[]{x, y});
            }
        }
    }

    if (candidates.isEmpty()) return null;

    int[] best = null;
    int bestScore = Integer.MIN_VALUE;

    for (int[] pos : candidates) {
        int x = pos[0];
        int y = pos[1];

        int score = scoreSummonTile(gameState, x, y);

        if (score > bestScore) {
            bestScore = score;
            best = pos;
        } else if (score == bestScore && best != null) {
            int bestCenterDist = Math.abs(best[1] - 2);
            int newCenterDist = Math.abs(y - 2);
            if (newCenterDist < bestCenterDist) {
                best = pos;
            }
        }
    }

    return best;
}

private static int scoreSummonTile(GameState gameState, int x, int y) {
    int score = 0;

    score += (8 - x) * 2;

    int dist = distanceToClosestEnemy(gameState, x, y, "HUMAN");
    if (dist != Integer.MAX_VALUE) {
        score += (20 - dist);
    }

    if (wouldBeAdjacentToEnemy(gameState, x, y, "HUMAN")) {
        score += 6;
    }

    score -= Math.abs(y - 2);

    score += summonShapeScore(gameState, x, y);

    return score;
}

private static int summonShapeScore(GameState gameState, int x, int y) {
    int score = 0;

    for (Unit unit : uniqueBoardUnits(gameState)) {
        String owner = gameState.unitOwner.get(unit.getId());
        if (!"AI".equals(owner)) continue;

        int ux = unit.getPosition().getTilex();
        int uy = unit.getPosition().getTiley();

        int dx = x - ux;
        int dy = y - uy;

        // discourage stacking straight in front of an existing AI unit
        if (dx == -1 && dy == 0) {
            score -= 8;
        }

        // slight preference for side support
        if (dx == 0 && Math.abs(dy) == 1) {
            score += 3;
        }

        // small bonus for diagonal support
        if (dx == -1 && Math.abs(dy) == 1) {
            score += 2;
        }
    }

    return score;
}

    // ------------------------------------------------------------
    // MOVEMENT
    // ------------------------------------------------------------

    private static int[] chooseBestMoveTile(GameState gameState, Unit unit) {
        List<int[]> legalMoves = getValidMoveTilesLikeHuman(gameState, unit);
        if (legalMoves.isEmpty()) return null;

        int[] best = null;
        int bestScore = Integer.MIN_VALUE;

        // Check every legal move and keep the highest-scoring one.
        for (int[] pos : legalMoves) {
            int x = pos[0];
            int y = pos[1];

            int score = evaluateMove(gameState, unit, x, y);
            if (score > bestScore) {
                bestScore = score;
                best = pos;
            }
        }

        return best;
    }

    private static int evaluateMove(GameState gameState, Unit unit, int x, int y) {
        int score = 0;

        int currentX = unit.getPosition().getTilex();
        int currentY = unit.getPosition().getTiley();

        Unit nearest = nearestEnemyUnit(gameState, currentX, currentY, "HUMAN");
        if (nearest != null) {
            int enemyX = nearest.getPosition().getTilex();
            int enemyY = nearest.getPosition().getTiley();

            int oldDist = manhattan(currentX, currentY, enemyX, enemyY);
            int newDist = manhattan(x, y, enemyX, enemyY);

            score += (oldDist - newDist) * 10;
        }

        // Strong bonus if this move puts the unit beside an enemy straight away.
        if (wouldBeAdjacentToEnemy(gameState, x, y, "HUMAN")) {
            score += 100;
        }

        // Mild push toward the human side.
        score += (8 - x) * 2;

        // Small central-lane preference.
        score -= Math.abs(y - 2);

        return score;
    }

    private static boolean moveUnit(ActorRef out, GameState gameState, Unit unit, int toX, int toY) {
        if (unit == null) return false;
        if (!isOnBoard(toX, toY)) return false;
        if (!canMoveToTile(gameState, unit, toX, toY)) return false;

        int unitId = unit.getId();
        if (StunRules.isStunnedThisTurn(gameState, unitId)) return false;

        int fromX = unit.getPosition().getTilex();
        int fromY = unit.getPosition().getTiley();
        if (toX == fromX && toY == fromY) return false;

        String oldKey = gameState.unitPositionKey.get(unitId);
        String newKey = gameState.key(toX, toY);

        Unit destOccupant = gameState.boardUnits.get(newKey);
        if (destOccupant != null && destOccupant.getId() != unitId) return false;

        Tile destinationTile = BasicObjectBuilders.loadTile(toX, toY);
        boolean yFirst = decideMoveOrder(gameState, fromX, fromY, toX, toY);

        // Remove the old board mapping first, but only if that mapping still points
        // to this exact unit. This avoids accidentally deleting something else.
        removeUnitFromOldTileIfNeeded(gameState, unitId, oldKey);

        BasicCommands.moveUnitToTile(out, unit, destinationTile, yFirst);
        waitForMoveAnimation(toX, toY, fromX, fromY);

        unit.setPositionByTile(destinationTile);
        sleep(100);

        // Safety re-check:
        // if something else somehow reached that tile first, restore old mapping and stop.
        Unit checkAgain = gameState.boardUnits.get(newKey);
        if (checkAgain != null && checkAgain.getId() != unitId) {
            restoreOldBoardPositionIfNeeded(gameState, unit, unitId, oldKey);
            return false;
        }

        gameState.boardUnits.put(newKey, unit);
        gameState.unitPositionKey.put(unitId, newKey);

        return true;
    }

    private static void removeUnitFromOldTileIfNeeded(GameState gameState, int unitId, String oldKey) {
        if (oldKey == null) return;

        Unit oldOccupant = gameState.boardUnits.get(oldKey);
        if (oldOccupant != null && oldOccupant.getId() == unitId) {
            gameState.boardUnits.remove(oldKey);
        }
    }

    private static void restoreOldBoardPositionIfNeeded(
            GameState gameState,
            Unit unit,
            int unitId,
            String oldKey
    ) {
        if (oldKey == null) return;

        gameState.boardUnits.put(oldKey, unit);
        gameState.unitPositionKey.put(unitId, oldKey);
    }

    private static void waitForMoveAnimation(int toX, int toY, int fromX, int fromY) {
        int moveDistance = Math.abs(toX - fromX) + Math.abs(toY - fromY);

        // Keep the exact same timings as before.
        if (moveDistance >= 2) {
            sleep(900);
        } else {
            sleep(700);
        }
    }

    private static List<int[]> getValidMoveTilesLikeHuman(GameState gameState, Unit unit) {
        List<int[]> validTiles = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int startX = unit.getPosition().getTilex();
        int startY = unit.getPosition().getTiley();

        // Flying unit handling stays exactly the same:
        // Flamewing is allowed to move to any empty tile on the board.
        if (isFlyingUnit(gameState, unit)) {
            addAllFlyingMoves(gameState, validTiles, seen, startX, startY);
            return validTiles;
        }

        // Otherwise use the same move pattern the human player gets:
        // one or two tiles straight, or one tile diagonally if legal.
        addCardinalMoves(gameState, unit, validTiles, seen, startX, startY);
        addDiagonalMoves(gameState, unit, validTiles, seen, startX, startY);

        return validTiles;
    }

    private static boolean isFlyingUnit(GameState gameState, Unit unit) {
        String name = gameState.unitName.get(unit.getId());
        if (name == null) return false;

        name = name.toLowerCase();
        return name.contains("flamewing");
    }

    private static void addAllFlyingMoves(
            GameState gameState,
            List<int[]> validTiles,
            Set<String> seen,
            int startX,
            int startY
    ) {
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                if (x == startX && y == startY) continue;
                if (occupied(gameState, x, y)) continue;

                addUniqueTile(validTiles, seen, x, y, gameState);
            }
        }
    }

    private static void addCardinalMoves(
            GameState gameState,
            Unit unit,
            List<int[]> validTiles,
            Set<String> seen,
            int startX,
            int startY
    ) {
        int[][] cardinalDirs = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };

        for (int[] dir : cardinalDirs) {
            int x1 = startX + dir[0];
            int y1 = startY + dir[1];

            if (canMoveToTile(gameState, unit, x1, y1)) {
                addUniqueTile(validTiles, seen, x1, y1, gameState);
            }

            int x2 = startX + (2 * dir[0]);
            int y2 = startY + (2 * dir[1]);

            if (canMoveToTile(gameState, unit, x2, y2)) {
                addUniqueTile(validTiles, seen, x2, y2, gameState);
            }
        }
    }

    private static void addDiagonalMoves(
            GameState gameState,
            Unit unit,
            List<int[]> validTiles,
            Set<String> seen,
            int startX,
            int startY
    ) {
        int[][] diagonalDirs = {
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] dir : diagonalDirs) {
            int x = startX + dir[0];
            int y = startY + dir[1];

            if (canMoveToTile(gameState, unit, x, y)) {
                addUniqueTile(validTiles, seen, x, y, gameState);
            }
        }
    }

    private static void addUniqueTile(List<int[]> tiles, Set<String> seen, int x, int y, GameState gameState) {
        String key = gameState.key(x, y);
        if (seen.add(key)) {
            tiles.add(new int[]{x, y});
        }
    }

    // ------------------------------------------------------------
    // ATTACKING
    // ------------------------------------------------------------

    private static void doAttack(ActorRef out, GameState gameState, Unit attacker, Unit target) {
        if (attacker == null || target == null) return;
        if (gameState.gameOver) return;

        int attackerId = attacker.getId();

        // stunned units cannot attack, and units only attack once.
        if (StunRules.isStunnedThisTurn(gameState, attackerId)) return;
        if (gameState.unitHadAttacked.getOrDefault(attackerId, false)) return;

        // Only attack if the target is still adjacent at the moment the attack happens.
        if (!isAdjacent(attacker, target)) return;

        attacker.attack(gameState, out, target);
        gameState.unitHadAttacked.put(attackerId, true);
    }


      // ------------------------------------------------------------
    // TARGETING HELPERS
    // ------------------------------------------------------------

    private static Unit findAdjacentEnemy(GameState gameState, Unit unit, String enemyOwner) {
        int x = unit.getPosition().getTilex();
        int y = unit.getPosition().getTiley();

        List<Unit> adjacentEnemies = new ArrayList<>();
        List<Unit> adjacentProvokers = new ArrayList<>();

        // Check all 8 surrounding tiles around the current unit.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int tx = x + dx;
                int ty = y + dy;

                if (!isOnBoard(tx, ty)) continue;

                Unit other = gameState.boardUnits.get(gameState.key(tx, ty));
                if (other == null) continue;

                String owner = gameState.unitOwner.get(other.getId());
                if (!enemyOwner.equals(owner)) continue;

                adjacentEnemies.add(other);

                // Keep provoke targets in their own list so they can be preferred first
                if (gameState.provokeUnitIds.contains(other.getId())) {
                    adjacentProvokers.add(other);
                }
            }
        }

        List<Unit> candidates = adjacentProvokers.isEmpty() ? adjacentEnemies : adjacentProvokers;
        return getLowestHealthUnit(gameState, candidates, 999);
    }

    private static Unit nearestEnemyUnit(GameState gameState, int x, int y, String enemyOwner) {
        Unit best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Unit unit : uniqueBoardUnits(gameState)) {
            String owner = gameState.unitOwner.get(unit.getId());
            if (!enemyOwner.equals(owner)) continue;

            int enemyX = unit.getPosition().getTilex();
            int enemyY = unit.getPosition().getTiley();
            int dist = manhattan(x, y, enemyX, enemyY);

            if (dist < bestDist) {
                bestDist = dist;
                best = unit;
            }
        }

        return best;
    }

    private static Unit chooseBestAiEnemyUnitTarget(GameState gameState) {
        List<Unit> validTargets = new ArrayList<>();

        for (Unit unit : uniqueBoardUnits(gameState)) {
            int id = unit.getId();
            if (isAvatar(gameState, id)) continue;

            String owner = gameState.unitOwner.get(id);
            if (!HUMAN_OWNER.equals(owner)) continue;

            validTargets.add(unit);
        }

        return getLowestHealthUnit(gameState, validTargets, 999);
    }

    private static Unit chooseBestAiBeamShockTarget(GameState gameState) {
        Unit best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Unit unit : uniqueBoardUnits(gameState)) {
            int id = unit.getId();
            if (isAvatar(gameState, id)) continue;

            String owner = gameState.unitOwner.get(id);
            if (!HUMAN_OWNER.equals(owner)) continue;

            int score = scoreBeamShockTarget(gameState, id);

            if (score > bestScore) {
                bestScore = score;
                best = unit;
            }
        }

        return best;
    }

    private static Unit chooseBestAiHealTarget(GameState gameState) {
        Unit best = null;
        int bestMissingHealth = 0;

        for (Unit unit : uniqueBoardUnits(gameState)) {
            int id = unit.getId();
            if (isAvatar(gameState, id)) continue;

            String owner = gameState.unitOwner.get(id);
            if (!AI_OWNER.equals(owner)) continue;

            int hp = gameState.unitHealth.getOrDefault(id, 0);
            int maxHp = gameState.unitMaxHealth.getOrDefault(id, hp);
            int missing = maxHp - hp;

            if (missing > bestMissingHealth) {
                bestMissingHealth = missing;
                best = unit;
            }
        }

        return best;
    }

    private static Unit getLowestHealthUnit(GameState gameState, List<Unit> units, int fallbackHp) {
        Unit best = null;
        int bestHp = Integer.MAX_VALUE;

        for (Unit unit : units) {
            int hp = gameState.unitHealth.getOrDefault(unit.getId(), fallbackHp);
            if (hp < bestHp) {
                bestHp = hp;
                best = unit;
            }
        }

        return best;
    }

    private static int scoreBeamShockTarget(GameState gameState, int unitId) {
        int score = 0;

        if (!gameState.unitHasMoved.getOrDefault(unitId, false)) {
            score += 2;
        }

        if (!gameState.unitHadAttacked.getOrDefault(unitId, false)) {
            score += 2;
        }

        score += gameState.unitAttack.getOrDefault(unitId, 0);
        return score;
    }

    private static boolean isAvatar(GameState gameState, int unitId) {
        return unitId == gameState.humanAvatarId || unitId == gameState.aiAvatarId;
    }

    // ------------------------------------------------------------
    // COLLECTION HELPERS
    // ------------------------------------------------------------

    private static List<Unit> getUnitsOwnedBy(GameState gameState, String owner) {
        List<Unit> units = new ArrayList<>();

        for (Unit unit : uniqueBoardUnits(gameState)) {
            String unitOwner = gameState.unitOwner.get(unit.getId());
            if (owner.equals(unitOwner)) {
                units.add(unit);
            }
        }

        return units;
    }

    private static List<Unit> uniqueBoardUnits(GameState gameState) {
        List<Unit> units = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        // boardUnits can be read through tile keys, so the same unit should only be returned once.
        for (Unit unit : gameState.boardUnits.values()) {
            if (unit == null) continue;

            if (seenIds.add(unit.getId())) {
                units.add(unit);
            }
        }

        return units;
    }

    // ------------------------------------------------------------
    // GEOMETRY / BOARD HELPERS
    // ------------------------------------------------------------

    private static boolean wouldBeAdjacentToEnemy(GameState gameState, int x, int y, String enemyOwner) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int tx = x + dx;
                int ty = y + dy;

                if (!isOnBoard(tx, ty)) continue;

                Unit other = gameState.boardUnits.get(gameState.key(tx, ty));
                if (other == null) continue;

                String owner = gameState.unitOwner.get(other.getId());
                if (enemyOwner.equals(owner)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean canMoveToTile(GameState gameState, Unit unit, int toX, int toY) {
        if (unit == null) return false;
        if (!isOnBoard(toX, toY)) return false;

        int fromX = unit.getPosition().getTilex();
        int fromY = unit.getPosition().getTiley();

        if (fromX == toX && fromY == toY) return false;

        int unitId = unit.getId();

        // Destination has to be free unless it somehow points back to this same unit.
        Unit destOccupant = gameState.boardUnits.get(gameState.key(toX, toY));
        if (destOccupant != null && destOccupant.getId() != unitId) {
            return false;
        }

        int dx = toX - fromX;
        int dy = toY - fromY;

        int absDx = Math.abs(dx);
        int absDy = Math.abs(dy);

        boolean diagonalOne = (absDx == 1 && absDy == 1);
        boolean straightOne = ((absDx == 1 && absDy == 0) || (absDx == 0 && absDy == 1));
        boolean straightTwo = ((absDx == 2 && absDy == 0) || (absDx == 0 && absDy == 2));

        if (!(diagonalOne || straightOne || straightTwo)) {
            return false;
        }

        if (straightOne) {
            return true;
        }

        // For a diagonal step, at least one corner route must be open.
        if (diagonalOne) {
            Unit xFirstBlocker = gameState.boardUnits.get(gameState.key(toX, fromY));
            Unit yFirstBlocker = gameState.boardUnits.get(gameState.key(fromX, toY));

            boolean xFirstFree = (xFirstBlocker == null || xFirstBlocker.getId() == unitId);
            boolean yFirstFree = (yFirstBlocker == null || yFirstBlocker.getId() == unitId);

            return xFirstFree || yFirstFree;
        }

        // For a two-tile straight move, the middle tile must be open.
        int midX = fromX + Integer.signum(dx);
        int midY = fromY + Integer.signum(dy);

        Unit middleOccupant = gameState.boardUnits.get(gameState.key(midX, midY));
        return middleOccupant == null || middleOccupant.getId() == unitId;
    }

    private static int distanceToClosestEnemy(GameState gameState, int x, int y, String enemyOwner) {
        int best = Integer.MAX_VALUE;

        for (Unit enemy : uniqueBoardUnits(gameState)) {
            String owner = gameState.unitOwner.get(enemy.getId());
            if (!enemyOwner.equals(owner)) continue;

            int ex = enemy.getPosition().getTilex();
            int ey = enemy.getPosition().getTiley();
            int dist = manhattan(x, y, ex, ey);

            if (dist < best) {
                best = dist;
            }
        }

        return best;
    }

    private static boolean decideMoveOrder(GameState gameState, int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;

        // Straight movement has no real pathing choice, so just return the default.
        if (dx == 0 || dy == 0) {
            return true;
        }

        Unit xFirstBlocker = gameState.boardUnits.get(gameState.key(x2, y1));
        Unit yFirstBlocker = gameState.boardUnits.get(gameState.key(x1, y2));

        boolean xFirstFree = (xFirstBlocker == null);
        boolean yFirstFree = (yFirstBlocker == null);

        // true means Y-first
        if (yFirstFree && !xFirstFree) return true;
        if (xFirstFree && !yFirstFree) return false;

        return true;
    }

    private static boolean occupied(GameState gameState, int x, int y) {
        return gameState.boardUnits.containsKey(gameState.key(x, y));
    }

    private static boolean isAdjacent(Unit a, Unit b) {
        int ax = a.getPosition().getTilex();
        int ay = a.getPosition().getTiley();
        int bx = b.getPosition().getTilex();
        int by = b.getPosition().getTiley();

        int dx = Math.abs(ax - bx);
        int dy = Math.abs(ay - by);

        return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
    }

    private static boolean isOnBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static String normalize(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}