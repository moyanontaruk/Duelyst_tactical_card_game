package events;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;
import utils.HighlightUtils;
import utils.UnitDeathUtils;
import utils.DestroySpellUtils;
import utils.StunRules;
import utils.SummonUtils;
import utils.OpeningGambitResolver;
import utils.SpellTargetRules;
import utils.DirectDamageSpellUtils;
import utils.HealSpellUtils;
import utils.MovementUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TileClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        if (gameState.gameOver) return;
        if (!"HUMAN".equals(gameState.activePlayer)) return;

        int tilex = message.get("tilex").asInt();
        int tiley = message.get("tiley").asInt();

        // SC 6
        boolean noCardSelected = (
                gameState.selectedHandPos == null || gameState.selectedCardConfig == null);

        if (noCardSelected) {
            String clickedKey = gameState.key(tilex, tiley);

            if (gameState.selectUnitId != null) {

                Unit selectedUnit = gameState.uiUnitById.get(gameState.selectUnitId);

                if (selectedUnit == null) {
                    gameState.selectUnitId = null;
                    clearMoveHighlights(out, gameState);
                    HighlightUtils.clearHighlightedTiles(out, gameState);
                    return;
                }

                // move clicked
                if (gameState.highlightedMovedTiles.contains(clickedKey)) {
                    Tile target = BasicObjectBuilders.loadTile(tilex, tiley);

                    int x1 = selectedUnit.getPosition().getTilex();
                    int y1 = selectedUnit.getPosition().getTiley();
                    boolean yFirst = MovementUtils.decideMoveOrder(gameState, x1, y1, tilex, tiley);
                    BasicCommands.moveUnitToTile(out, selectedUnit, target, yFirst);
                    sleep(MovementUtils.estimateMoveDurationMs(x1, y1, tilex, tiley));
                    selectedUnit.setPositionByTile(target);
                    MovementUtils.syncUnitBoardPosition(gameState, selectedUnit, tilex, tiley);

                    gameState.unitHasMoved.put(selectedUnit.getId(), true);

                    gameState.selectUnitId = null;

                    clearMoveHighlights(out, gameState);
                    HighlightUtils.clearHighlightedTiles(out, gameState);
                    return;
                }
                else if (gameState.highlightedTargetTiles.contains(clickedKey)) {

                    Unit enemy = gameState.boardUnits.get(clickedKey);

                    if (enemy != null) {
                        int ux = selectedUnit.getPosition().getTilex();
                        int uy = selectedUnit.getPosition().getTiley();
                        int ex = enemy.getPosition().getTilex();
                        int ey = enemy.getPosition().getTiley();

                        // already adjacent -> attack now
                        if (isAdjacent(ux, uy, ex, ey)) {
                            selectedUnit.attack(gameState, out, enemy);
                            if (selectedUnit.getId() == gameState.humanAvatarId) {
                                sleep(600);
                            }
                            triggerHornOnHit(out, gameState, selectedUnit);

                            gameState.selectUnitId = null;
                            clearMoveHighlights(out, gameState);
                            HighlightUtils.clearHighlightedTiles(out, gameState);
                            return;
                        }

                        // not adjacent yet -> move first, then attack
                        boolean alreadyMoved = gameState.unitHasMoved.getOrDefault(selectedUnit.getId(), false);

                        if (!alreadyMoved) {
                            List<int[]> reachable = getValidMoveTiles(gameState, ux, uy, selectedUnit);
                            int[] moveTile = findAdjacentMoveTile(reachable, ex, ey);

                            if (moveTile != null) {
                                Tile target = BasicObjectBuilders.loadTile(moveTile[0], moveTile[1]);

                                gameState.pendingAttackAfterMove.put(selectedUnit.getId(), enemy.getId());
                                int x1 = selectedUnit.getPosition().getTilex();
                                int y1 = selectedUnit.getPosition().getTiley();
                                boolean yFirst = MovementUtils.decideMoveOrder(
                                        gameState,
                                        x1,
                                        y1,
                                        moveTile[0],
                                        moveTile[1]
                                );
                                BasicCommands.moveUnitToTile(out, selectedUnit, target, yFirst);
                                sleep(MovementUtils.estimateMoveDurationMs(x1, y1, moveTile[0], moveTile[1]));

                                selectedUnit.setPositionByTile(target);
                                MovementUtils.syncUnitBoardPosition(
                                        gameState,
                                        selectedUnit,
                                        moveTile[0],
                                        moveTile[1]
                                );
                                gameState.unitHasMoved.put(selectedUnit.getId(), true);

                                gameState.selectUnitId = null;
                                clearMoveHighlights(out, gameState);
                                HighlightUtils.clearHighlightedTiles(out, gameState);
                                return;
                            }
                        }
                    }

                    gameState.selectUnitId = null;
                    clearMoveHighlights(out, gameState);
                    HighlightUtils.clearHighlightedTiles(out, gameState);
                    return;
                }

                gameState.selectUnitId = null;
                clearMoveHighlights(out, gameState);
                HighlightUtils.clearHighlightedTiles(out, gameState);
                return;
            }
            else {
                clearMoveHighlights(out, gameState);
                HighlightUtils.clearHighlightedTiles(out, gameState);

                Unit clickedUnit = gameState.boardUnits.get(clickedKey);
                if (clickedUnit == null) return;

                String owner = gameState.unitOwner.get(clickedUnit.getId());
                if (!"HUMAN".equals(owner)) return;

                int unitId = clickedUnit.getId();

                boolean alreadyAttacked = gameState.unitHadAttacked.getOrDefault(unitId, false);
                if (alreadyAttacked) {
                    BasicCommands.addPlayer1Notification(out, "This unit already attacked.", 2);
                    return;
                }

                if (StunRules.rejectIfStunned(out, gameState, unitId)) {
                    return;
                }

                gameState.selectUnitId = unitId;

                boolean alreadyMoved = gameState.unitHasMoved.getOrDefault(unitId, false);

                boolean provoked = HighlightUtils.isProvoked(gameState, clickedUnit);

                if (!alreadyMoved && !provoked) {
                    List<int[]> reachable = getValidMoveTiles(gameState, tilex, tiley, clickedUnit);
                    highlightMoveTilesWhite(out, gameState, reachable);

                    List<int[]> attackableAfterMove = getAttackableAfterMoveTiles(gameState, reachable);
                    HighlightUtils.highlightTilesRed(out, gameState, attackableAfterMove);
                }

                List<Unit> adjacentProvokers = HighlightUtils.getAdjacentEnemyProvokers(gameState, clickedUnit);

                if (!adjacentProvokers.isEmpty()) {
                    List<int[]> provokeTargets = new ArrayList<>();
                    for (Unit provokeUnit : adjacentProvokers) {
                        provokeTargets.add(new int[]{
                                provokeUnit.getPosition().getTilex(),
                                provokeUnit.getPosition().getTiley()
                        });
                    }
                    HighlightUtils.highlightTilesRed(out, gameState, provokeTargets);
                } else {
                    List<int[]> attackable = HighlightUtils.getEnemyTiles(tilex, tiley, true, gameState);
                    HighlightUtils.highlightTilesRed(out, gameState, attackable);
                }
                return;
            }
        }

        // Must have selected a card
        if (gameState.selectedHandPos == null) return;
        if (gameState.selectedCardConfig == null) return;

        int selectedPos = gameState.selectedHandPos;

        if (selectedPos < 1 || selectedPos > gameState.humanHand.size()) {
            HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
        }

        String currentCfg = gameState.humanHand.get(selectedPos - 1);
        if (currentCfg == null || !currentCfg.equals(gameState.selectedCardConfig)) {
            HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
        }

        Card card = BasicObjectBuilders.loadCard(currentCfg, 1000 + selectedPos, Card.class);
        if (card == null) {
            HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
        }

        // Unit card summon
if (gameState.selectedCardIsUnit) {

    if (!card.isCreature()) return;
    if (gameState.boardUnits.containsKey(gameState.key(tilex, tiley))) return;
    if (!gameState.highlightedMovedTiles.contains(gameState.key(tilex, tiley))) return;

    int cost = card.getManacost();
    if (gameState.humanMana < cost) {
        BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
        return;
    }

    if (card.getBigCard() == null || card.getUnitConfig() == null) {
        BasicCommands.addPlayer1Notification(out, "Unit data is invalid", 2);
        HighlightUtils.clearSelectionAndHighlights(out, gameState);
        return;
    }

    Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);


    if (!spendHumanMana(out, gameState, cost)) return;

    int atk = card.getBigCard().getAttack();
    int hp = card.getBigCard().getHealth();

    Unit unit = SummonUtils.spawnUnit(
            out,
            gameState,
            card.getUnitConfig(),
            tilex,
            tiley,
            atk,
            hp,
            "HUMAN"
    );

    if (unit == null) {
        BasicCommands.addPlayer1Notification(out, "Failed to summon unit", 2);
        HighlightUtils.clearSelectionAndHighlights(out, gameState);
        return;
    }

    // keep card-name registration for deathwatch/opening-gambit logic
    gameState.unitName.put(unit.getId(), card.getCardname());

    OpeningGambitResolver.onSummoned(out, gameState, unit, card.getCardname());

    consumeSelectedCardAndClear(out, gameState, selectedPos);
    return;
}

        // Spell card logic
        if (card.isCreature()) return;

        String name = (card.getCardname() == null) ? "" : card.getCardname().trim().toLowerCase();

        int cost = card.getManacost();
        if (gameState.humanMana < cost) {
            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            return;
        }

        if (!gameState.highlightedTargetTiles.contains(gameState.key(tilex, tiley))) {
            return;
        }

        if (!isValidSpellTarget(gameState, card, tilex, tiley)) {
            return;
        }

        // Wraithling Swarm
        // Wraithling Swarm
        if (name.equals("wraithling swarm")) {

            if (gameState.boardUnits.containsKey(gameState.key(tilex, tiley))) return;
            if (!gameState.highlightedTargetTiles.contains(gameState.key(tilex, tiley))) return;

            if (!spendHumanMana(out, gameState, cost)) return;

            Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
            EffectAnimation fx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);
            if (fx != null) BasicCommands.playEffectAnimation(out, fx, tile);

            // First summon is the clicked tile
            SummonUtils.spawnWraithling(out, gameState, tilex, tiley, "HUMAN");

            int summoned = 1;

            // Then summon the remaining Wraithlings in sequence:
            // each next one can appear on any empty tile adjacent to ANY friendly unit,
            // including Wraithlings summoned earlier in this same spell.
            while (summoned < 3) {
                int[] next = null;

                outer:
                for (Unit u : gameState.boardUnits.values()) {
                    if (u == null) continue;

                    String owner = gameState.unitOwner.get(u.getId());
                    if (!"HUMAN".equals(owner)) continue;

                    int ux = u.getPosition().getTilex();
                    int uy = u.getPosition().getTiley();

                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;

                            int sx = ux + dx;
                            int sy = uy + dy;

                            if (!isOnBoard(sx, sy)) continue;
                            if (gameState.boardUnits.containsKey(gameState.key(sx, sy))) continue;

                            next = new int[]{sx, sy};
                            break outer;
                        }
                    }
                }

                if (next == null) break; // no more legal summon spaces

                Tile nextTile = BasicObjectBuilders.loadTile(next[0], next[1]);
                if (fx != null) BasicCommands.playEffectAnimation(out, fx, nextTile);

                SummonUtils.spawnWraithling(out, gameState, next[0], next[1], "HUMAN");
                summoned++;
            }

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // Truestrike
        if (name.equals("truestrike")) {
            Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
            if (target == null) return;

            if (!spendHumanMana(out, gameState, cost)) return;

            boolean applied = DirectDamageSpellUtils.dealDamageToUnit(
                    out,
                    gameState,
                    target,
                    2,
                    "AI",
                    false,
                    StaticConfFiles.f1_martyrdom
            );

            if (!applied) return;

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // Sundrop Elixir
        if (name.equals("sundrop elixir")) {
            Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
            if (target == null) return;

            if (!spendHumanMana(out, gameState, cost)) return;

            boolean applied = HealSpellUtils.healUnit(
                    out,
                    gameState,
                    target,
                    4,
                    null,
                    true,
                    StaticConfFiles.f1_buff
            );

            if (!applied) return;

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // Beam Shock
        if (name.equals("beam shock") || name.equals("beamshock")) {
        Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
        if (target == null) return;

        int targetId = target.getId();

        if (targetId == gameState.humanAvatarId || targetId == gameState.aiAvatarId) return;

        String owner = gameState.unitOwner.get(targetId);
        if (!"AI".equals(owner)) return;

        if (!spendHumanMana(out, gameState, cost)) return;

        Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
        EffectAnimation fx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_buff);
        if (fx != null) {
        BasicCommands.playEffectAnimation(out, fx, tile);
        sleep(120);
    }

    if (!StunRules.applyStunToUnit(out, gameState, target)) return;

    consumeSelectedCardAndClear(out, gameState, selectedPos);
    return;
}

        // Dark Terminus
        if (name.equals("dark terminus")) {
            Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
            if (target == null) return;

            if (target.getId() == gameState.aiAvatarId) return;

            String owner = gameState.unitOwner.get(target.getId());
            if (!"AI".equals(owner)) return;

            if (!spendHumanMana(out, gameState, cost)) return;

            if (!DestroySpellUtils.destroyNonAvatarUnit(out, gameState, target)) return;

            SummonUtils.spawnWraithling(out, gameState, tilex, tiley, "HUMAN");

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // Horn of the Forsaken
        if (name.equals("horn of the forsaken")) {
            int[] avatarPos = gameState.getAvatarPosition("HUMAN");
            if (tilex != avatarPos[0] || tiley != avatarPos[1]) return;

            if (!spendHumanMana(out, gameState, cost)) return;

            Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
            EffectAnimation fx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_buff);
            if (fx != null) BasicCommands.playEffectAnimation(out, fx, tile);

            gameState.hornOfForsaken = true;
            gameState.hornRobustness = 3;

            BasicCommands.addPlayer1Notification(out, "Horn of the Forsaken equipped with 3 robustness.", 3);

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        BasicCommands.addPlayer1Notification(out, "Spell not implemented", 2);
    }

    private boolean isValidSpellTarget(GameState gameState, Card card, int tilex, int tiley) {
        List<int[]> validTargets = SpellTargetRules.getValidTargetTiles(gameState, card);
        for (int[] xy : validTargets) {
            if (xy != null && xy.length >= 2 && xy[0] == tilex && xy[1] == tiley) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }

    private boolean spendHumanMana(ActorRef out, GameState gameState, int cost) {
        if (gameState.humanMana < cost) {
            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            return false;
        }

        gameState.humanMana -= cost;
        BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));
        return true;
    }

    private void consumeSelectedCardAndClear(ActorRef out, GameState gameState, int selectedPos) {
        if (selectedPos >= 1 && selectedPos <= gameState.humanHand.size()) {
            gameState.humanHand.remove(selectedPos - 1);
        }

        for (int pos = 1; pos <= 6; pos++) {
            BasicCommands.deleteCard(out, pos);
        }

        for (int i = 0; i < gameState.humanHand.size() && i < 6; i++) {
            String cfg = gameState.humanHand.get(i);
            int handPos = i + 1;
            Card c = BasicObjectBuilders.loadCard(cfg, 1000 + handPos, Card.class);
            if (c != null) {
                BasicCommands.drawCard(out, c, handPos, 0);
            }
        }

        clearMoveHighlights(out, gameState);
        HighlightUtils.clearHighlightedTiles(out, gameState);

        gameState.selectedHandPos = null;
        gameState.selectedCardConfig = null;
        gameState.selectedCardIsUnit = false;
    }

    private boolean isAdjacent(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x1 - x2);
        int dy = Math.abs(y1 - y2);
        return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
    }

    private int[] findAdjacentMoveTile(List<int[]> reachableTiles, int enemyX, int enemyY) {
        for (int[] tile : reachableTiles) {
            if (tile == null || tile.length < 2) continue;

            if (isAdjacent(tile[0], tile[1], enemyX, enemyY)) {
                return tile;
            }
        }
        return null;
    }

    private void clearMoveHighlights(ActorRef out, GameState gameState) {
        if (gameState.highlightedMovedTiles.isEmpty()) return;

        for (String key : gameState.highlightedMovedTiles) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);

            Tile tile = BasicObjectBuilders.loadTile(x, y);
            BasicCommands.drawTile(out, tile, 0);
        }

        gameState.highlightedMovedTiles.clear();
    }

    private void highlightMoveTilesWhite(
            ActorRef out,
            GameState gameState,
            List<int[]> tiles) {

        for (int[] xy : tiles) {
            int x = xy[0];
            int y = xy[1];
            Tile boardTile = BasicObjectBuilders.loadTile(x, y);

            BasicCommands.drawTile(out, boardTile, 1);
            gameState.highlightedMovedTiles.add(gameState.key(x, y));
        }
    }

    private List<int[]> getAttackableAfterMoveTiles(GameState gameState, List<int[]> reachableTiles) {
        List<int[]> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int[] xy : reachableTiles) {
            if (xy == null || xy.length < 2) continue;

            List<int[]> adjacentEnemies = HighlightUtils.getEnemyTiles(xy[0], xy[1], true, gameState);
            for (int[] enemyTile : adjacentEnemies) {
                String key = gameState.key(enemyTile[0], enemyTile[1]);
                if (seen.add(key)) {
                    result.add(enemyTile);
                }
            }
        }

        return result;
    }

    private List<int[]> getValidMoveTiles(
            GameState gameState,
            int startX,
            int startY,
            Unit unit) {

        List<int[]> validTiles = new ArrayList<>();
        boolean isFlying = false;

        //  FIX: flying detection by unit name
        String name = gameState.unitName.get(unit.getId());
        if (name != null && name.toLowerCase().contains("flamewing")) {
            isFlying = true;
        }

        if (isFlying) {
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 5; y++) {
                    if (x == startX && y == startY) continue;
                    if (gameState.boardUnits.containsKey(gameState.key(x, y))) continue;
                    validTiles.add(new int[]{x, y});
                }
            }
            return validTiles;
        }

        int[][] cardinalsDir = new int[][]{
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };

        for (int[] direction : cardinalsDir) {
            int dirX = direction[0];
            int dirY = direction[1];

            int step1X = startX + dirX;
            int step1Y = startY + dirY;

            if (!isOnBoard(step1X, step1Y)) continue;

            String step1Key = gameState.key(step1X, step1Y);
            if (gameState.boardUnits.containsKey(step1Key)) continue;

            validTiles.add(new int[]{step1X, step1Y});

            int step2X = startX + 2 * dirX;
            int step2Y = startY + 2 * dirY;

            if (!isOnBoard(step2X, step2Y)) continue;

            String step2Key = gameState.key(step2X, step2Y);
            if (gameState.boardUnits.containsKey(step2Key)) continue;

            validTiles.add(new int[]{step2X, step2Y});
        }

        int[][] diagonalDir = new int[][]{
                {1, 1},
                {1, -1},
                {-1, 1},
                {-1, -1}
        };

        for (int[] direction : diagonalDir) {
            int diagX = startX + direction[0];
            int diagY = startY + direction[1];

            if (!isOnBoard(diagX, diagY)) continue;

            String key = gameState.key(diagX, diagY);
            if (gameState.boardUnits.containsKey(key)) continue;

            validTiles.add(new int[]{diagX, diagY});
        }

        return validTiles;
    }

private void triggerHornOnHit(ActorRef out, GameState gameState, Unit attacker) {
    if (attacker == null) return;
    if (attacker.getId() != gameState.humanAvatarId) return;
    if (!gameState.hornOfForsaken) return;
    if (gameState.hornRobustness <= 0) return;

    int[] avatarPos = gameState.getAvatarPosition("HUMAN");
    int px = avatarPos[0];
    int py = avatarPos[1];

    List<int[]> emptyAdjacent = new ArrayList<>();

    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;

            int tx = px + dx;
            int ty = py + dy;

            if (tx < 0 || tx >= 9 || ty < 0 || ty >= 5) continue;

            String key = gameState.key(tx, ty);
            if (!gameState.boardUnits.containsKey(key)) {
                emptyAdjacent.add(new int[]{tx, ty});
            }
        }
    }

    if (emptyAdjacent.isEmpty()) return;

    int idx = (int) (Math.random() * emptyAdjacent.size());
    int[] chosen = emptyAdjacent.get(idx);

    SummonUtils.spawnWraithling(out, gameState, chosen[0], chosen[1], "HUMAN");
}
    // small UI sync delay
    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
