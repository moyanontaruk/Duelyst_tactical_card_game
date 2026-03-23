package structures.basic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import commands.BasicCommands;
import structures.GameState;
import akka.actor.ActorRef;
import utils.UnitDeathUtils;

/**
 * This is a representation of a Unit on the game board.
 * A unit has a unique id (this is used by the front-end.
 * Each unit has a current UnitAnimationType, e.g. move,
 * or attack. The position is the physical position on the
 * board. UnitAnimationSet contains the underlying information
 * about the animation frames, while ImageCorrection has
 * information for centering the unit on the tile. 
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class Unit {

	@JsonIgnore
	protected static ObjectMapper mapper = new ObjectMapper(); // Jackson Java Object Serializer, is used to read java objects from a file
	
	int id;
	UnitAnimationType animation;
	Position position;
	UnitAnimationSet animations;
	ImageCorrection correction;
	int attack;
	int health;
	boolean canMove;
	boolean canAttack;
	boolean attackAfterMove;
	
	public Unit() {}
	
	public Unit(int id, UnitAnimationSet animations, ImageCorrection correction) {
		super();
		this.id = id;
		this.animation = UnitAnimationType.idle;
		
		position = new Position(0,0,0,0);
		this.correction = correction;
		this.animations = animations;
	}
	
	public Unit(int id, UnitAnimationSet animations, ImageCorrection correction, Tile currentTile) {
		super();
		this.id = id;
		this.animation = UnitAnimationType.idle;
		
		position = new Position(currentTile.getXpos(),currentTile.getYpos(),currentTile.getTilex(),currentTile.getTiley());
		this.correction = correction;
		this.animations = animations;
	}
	
	public Unit(int id, UnitAnimationType animation, Position position, UnitAnimationSet animations,
			ImageCorrection correction) {
		super();
		this.id = id;
		this.animation = animation;
		this.position = position;
		this.animations = animations;
		this.correction = correction;
	}

	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public UnitAnimationType getAnimation() {
		return animation;
	}
	public void setAnimation(UnitAnimationType animation) {
		this.animation = animation;
	}

	public ImageCorrection getCorrection() {
		return correction;
	}

	public void setCorrection(ImageCorrection correction) {
		this.correction = correction;
	}

	public Position getPosition() {
		return position;
	}

	public void setPosition(Position position) {
		this.position = position;
	}

	public UnitAnimationSet getAnimations() {
		return animations;
	}

	public void setAnimations(UnitAnimationSet animations) {
		this.animations = animations;
	}

	// ---- story card 23: rush ability ----
	private boolean hasRush = false;

	public boolean getHasRush(){
		return hasRush;
	}

	public void setHasRush(boolean hasRush){
		this.hasRush = hasRush;
	}
	public void setAttack(int attack) {
    this.attack = attack;
}

	public void setHealth(int health) {
    this.health = health;
}
	
	/**
	 * This command sets the position of the Unit to a specified
	 * tile.
	 * @param tile
	 */
	@JsonIgnore
	public void setPositionByTile(Tile tile) {
		position = new Position(tile.getXpos(),tile.getYpos(),tile.getTilex(),tile.getTiley());
	}

	public boolean isCanMove() {
		return canMove;
	}

	public void setCanMove(boolean canMove) {
		this.canMove = canMove;
	}

	public boolean isCanAttack() {
		return canAttack;
	}

	public void setCanAttack(boolean canAttack) {
		this.canAttack = canAttack;
	}

	public boolean isAttackAfterMove() {
		return attackAfterMove;
	}

	public void setAttackAfterMove(boolean attackAfterMove) {
		this.attackAfterMove = attackAfterMove;
	}

	// correction to attack () needed -- adding UnitDeathUtils - Maggie
	public void attack(GameState gameState, ActorRef out, Unit enemy) {

		if (gameState == null || enemy == null) return;

		int attackerId = this.id;
		int defenderId = enemy.id;

		Integer attackerHpObj = gameState.unitHealth.get(attackerId);
		Integer defenderHpObj = gameState.unitHealth.get(defenderId);
		Integer attackerAtkObj = gameState.unitAttack.get(attackerId);
		Integer defenderAtkObj = gameState.unitAttack.get(defenderId);

		if (attackerHpObj == null || defenderHpObj == null || attackerAtkObj == null || defenderAtkObj == null) {
			return;
		}

		int attackerHp = attackerHpObj;
		int defenderHp = defenderHpObj;
		int attackerAtk = attackerAtkObj;
		int defenderAtk = defenderAtkObj;

		// mark attacker already attacked this turn
		gameState.unitHadAttacked.put(attackerId, true);

		// attack animation
		int attackDelay = BasicCommands.playUnitAnimation(out, this, UnitAnimationType.attack);
		try {
    	Thread.sleep(attackDelay);
		} catch (InterruptedException e) {
    	Thread.currentThread().interrupt();
	}
		BasicCommands.playUnitAnimation(out, this, UnitAnimationType.idle);

		// attacker deals damage
		int defenderNewHp = defenderHp - attackerAtk;
		//play hit animation on defender BEFORE checking death
		int counterDelay = BasicCommands.playUnitAnimation(out, enemy, UnitAnimationType.hit);
		try {
    	Thread.sleep(counterDelay);
		} catch (InterruptedException e) {
    	Thread.currentThread().interrupt();
}
		BasicCommands.playUnitAnimation(out, enemy, UnitAnimationType.idle);

		UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, enemy, defenderNewHp);

		// if defender died, stop
		if (!gameState.uiUnitById.containsKey(defenderId)) {
			return;
		}

		// counter attack
		BasicCommands.playUnitAnimation(out, enemy, UnitAnimationType.attack);
		try {
			Thread.sleep(600);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		BasicCommands.playUnitAnimation(out, enemy, UnitAnimationType.idle);

		int attackerNewHp = attackerHp - defenderAtk;

		//play hit on animation BEFORE checking death
		BasicCommands.playUnitAnimation(out, this, UnitAnimationType.hit);
		try {
			//give animation time to play
			Thread.sleep(400);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		BasicCommands.playUnitAnimation(out, this, UnitAnimationType.idle);

		UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, this, attackerNewHp);
	}
}