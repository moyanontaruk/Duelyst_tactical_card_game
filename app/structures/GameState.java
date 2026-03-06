package structures;

import java.util.*;

import structures.basic.Unit;

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

	// ---- Units on board ----
	// key = "x,y"
	public final Map<String, Unit> boardUnits = new HashMap<>();

	// ---- Unit stats tracking (server-side) ----
	// Note: the provided Unit class is primarily a UI representation.

	public final Map<Integer, Integer> unitHealth = new HashMap<>();
	public final Map<Integer, Integer> unitAttack = new HashMap<>();
	// unitId -> board key ("x,y") to allow fast removal from boardUnits when a unit dies
	public final Map<Integer, String> unitPositionKey = new HashMap<>();

	// ---- Story Card #29: stun tracking ----
	// unitId -> owner whose next turn is blocked by stun ("HUMAN" or "AI")
	public final Map<Integer, String> stunnedUntilEndOfOwnersTurn = new HashMap<>();

	// ---- Unit id generator ----
	public int nextUnitId = 1000;
	public int allocateUnitId() {
		return nextUnitId++;
	}

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

	public final Map<Integer, Integer> unitMaxHealth = new HashMap<>();
	public final Map<Integer, String> unitOwner = new HashMap<>();

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
	public Integer humanAvatarId = null;
	public Integer aiAvatarId = null;

	// apply damage to unit
	public void applyDamageToUnit(int unitId, int amount){
		//get health value from unit id, if not there default to 0
		int current = unitHealth.getOrDefault(unitId, 0);
		int newHp = current - amount;
		unitHealth.put(unitId, newHp);

		// applying damage to human/ai player if unit is avatar
		if (unitId == humanAvatarId){
			humanHealth = newHp;
		} else if (unitId == aiAvatarId){
			aiHealth = newHp;
		}
	}

	//apply healing to unit
	public void applyHealingToUnit(int unitId, int amount){
		int current = unitHealth.getOrDefault(unitId, 0);
		int newHp = current + amount;
		unitHealth.put(unitId, newHp);

		//apply healing to human/ai player if unit if avatar
		if (unitId == humanAvatarId){
			humanHealth = newHp;
		} else if (unitId == aiAvatarId){
			aiHealth = newHp;
		}
	}
}