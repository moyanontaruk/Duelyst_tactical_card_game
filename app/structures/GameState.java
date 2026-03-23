package structures;

import java.util.*;

import structures.basic.Unit;
import commands.BasicCommands;
import akka.actor.ActorRef;
import utils.UnitDeathUtils;


public class GameState {

	// ---- Template flags ----
	public boolean gameInitialized = false;
	public boolean something = false;

	// ---- Game end state ----
	public boolean gameOver = false;
	public String winner = null; // "HUMAN" or "AI"

	// ---- Players ----
	public int humanHealth = 20;
	public int aiHealth = 20;

	public int humanMana = 0;
	public int aiMana = 0;

	// ---- Turn ----
	public int turnNumber = 1;
	public String activePlayer = "HUMAN";
	public boolean aiTurnPending = false;
	public boolean aiTurnRunning = false;

	// ---- Units on board ----
	// key = "x,y"
	public final Map<String, Unit> boardUnits = new HashMap<>();

	// ---- Unit stats tracking (server-side) ----
	// Note: the provided Unit class is primarily a UI representation.

	public final Map<Integer, Integer> unitHealth = new HashMap<>();
	public final Map<Integer, Integer> unitAttack = new HashMap<>();
	public final Set<Integer> provokeUnitIds = new HashSet<>();
	// unitId -> board key ("x,y") to allow fast removal from boardUnits when a unit dies
	public final Map<Integer, String> unitPositionKey = new HashMap<>();

	// ---- Story Card #29: stun tracking ----
	// unitId -> owner whose next turn is blocked by stun ("HUMAN" or "AI")
	public final Map<Integer, String> stunnedUntilEndOfOwnersTurn = new HashMap<>();

	// pending "move first, then attack" action
	// attacker unit id -> defender unit id
	public final Map<Integer, Integer> pendingAttackAfterMove = new HashMap<>();

	public List<String> aiDeck = new ArrayList<>();
	public List<String> aiHand = new ArrayList<>();


	// ---- Unit id generator ----
	public int nextUnitId = 1000;
	public int allocateUnitId() {
		return nextUnitId++;
	}

	// ---- Runtime deck/hand tracking (human) ----
	// Stores card config paths in current order.
	public final List<String> humanDeck = new ArrayList<>();
	public final List<String> humanHand = new ArrayList<>();


	// ---- Card selection ----
	public Integer selectedHandPos = null;
	public String selectedCardConfig = null;
	public boolean selectedCardIsUnit = false;

	// ---- UI Unit mapping ----
	public final Map<Integer, Unit> uiUnitById = new HashMap<>();

	// ---- Highlighted tiles ----
	public final Set<String> highlightedTargetTiles = new HashSet<>();

	// ---- Helpers ----
	public String key(int x, int y) {
		return x + "," + y;
	}

	public int[] getUnitPosition(int unitId) {
		String pos = unitPositionKey.get(unitId);
		if (pos == null) return null;

		String[] parts = pos.split(",");
		if (parts.length != 2) return null;

		try {
			return new int[] {
				Integer.parseInt(parts[0]),
				Integer.parseInt(parts[1])
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public int[] getAvatarPosition(String owner) {
		int avatarId = "AI".equals(owner) ? aiAvatarId : humanAvatarId;
		int[] pos = getUnitPosition(avatarId);

		if (pos != null) return pos;

		// fallback to starting positions if something is missing
		return "AI".equals(owner) ? new int[]{7, 2} : new int[]{1, 2};
	}


	public final Map<Integer, Integer> unitMaxHealth = new HashMap<>();
	public final Map<Integer, String> unitOwner = new HashMap<>();
	
	// SC 18:Specifically designed to record the names of monsters on the field
    public final Map<Integer, String> unitName = new HashMap<>();

	// SB6, SB7, SB9
	//storing which unit the player has selected currently
	public Integer selectUnitId = null;

	//storing board title key (x,y) that are highlight for that movement
	public final Set<String> highlightedMovedTiles = new HashSet<>();

	//for tracking if a unit has already moved during the turn
	public final Map<Integer, Boolean> unitHasMoved = new HashMap<>();

	// for tracking if unit already attacked during the turn
	public final Map<Integer, Boolean> unitHadAttacked = new HashMap<>();

// ---- Story Card 14 damage/healing ----
	// avatar tracking
	public Integer humanAvatarId = 0;
	public Integer aiAvatarId = 1;

	// apply damage to unit
	public void applyDamageToUnit(int unitId, int amount){
		Unit unit = uiUnitById.get(unitId);
		if (unit == null) return;

		int current = unitHealth.getOrDefault(unitId, 0);
		int newHp = current - amount;

		UnitDeathUtils.setUnitHealthAndCheckDeath(null, this, unit, newHp);
	}

	//apply healing to unit
	public void applyHealingToUnit(int unitId, int amount){
		Unit unit = uiUnitById.get(unitId);
		if (unit == null) return;

		int current = unitHealth.getOrDefault(unitId, 0);
		int newHp = current + amount;

		UnitDeathUtils.setUnitHealthAndCheckDeath(null, this, unit, newHp);
	}


	// --- story card 19: damage trigger abilities ---
	// Zeal and Horn of the Forsaken
	public final Set<Integer> zealUnitIds = new HashSet<>();
	public final Set<Integer> zealBuffApplied = new HashSet<>();
	public boolean hornOfForsaken = false;
	public int hornRobustness = 0;

	public void damageOnAvatarTrigger(ActorRef out, int unitId, int damage) {
		//only trigger on damage
		if (damage <= 0) return;
		String damagedOwner = (unitId == humanAvatarId) ? "HUMAN" : (unitId == aiAvatarId) ? "AI" : null;
		if (damagedOwner == null) return;

		//Zeal trigger (Silvergaurd Knight)
		for (int zealUnitId : zealUnitIds){
			if (!damagedOwner.equals(unitOwner.get(zealUnitId))) continue;

			// only apply zeal buff once
			if (zealBuffApplied.contains(zealUnitId)) continue;

			int currentAttack = unitAttack.getOrDefault(zealUnitId, 0);
			int newAttack = currentAttack + 2;

			unitAttack.put(zealUnitId, newAttack);
			zealBuffApplied.add(zealUnitId);

			Unit zealUnit = uiUnitById.get(zealUnitId);
			if (zealUnit != null && out != null){
				BasicCommands.setUnitAttack(out, zealUnit, newAttack);
			}
		}

	// Horn of the Forsaken trigger
if (unitId == humanAvatarId && hornOfForsaken) {
    hornRobustness--;

    if (hornRobustness <= 0) {
        hornRobustness = 0;
        hornOfForsaken = false;
        if (out != null) {
            BasicCommands.addPlayer1Notification(out, "Horn of the Forsaken destroyed!", 3);
        }
    } else {
        if (out != null) {
            BasicCommands.addPlayer1Notification(
                out,
                "Horn of the Forsaken lost 1 robustness (" + hornRobustness + " left).",
                2
            );
        }
    }
}
	}
}
