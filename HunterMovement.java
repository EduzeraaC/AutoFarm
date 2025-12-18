package gameserver.custom.model;

import gameserver.model.actor.Player;
import gameserver.model.actor.instance.Monster;
import gameserver.model.location.Location;
import config.Config;

public class HunterMovement {

    private final Hunter hunter;

    protected boolean isMoving;
    private Location startLocation;

    // Stuck
    private long targetLastHitTime;
    private long targetLockedAt;
    private Monster lockedTarget;


    public HunterMovement(Hunter hunter) {
        this.hunter = hunter;
    }

    public boolean isInMovement() {
        return isMoving;
    }

    public boolean isFarFromStartLocation() {
        return !hunter.getPlayer().isIn2DRadius(startLocation, Math.max(hunter.getRadius(), 500));
    }

    public boolean isAtDestination() {
        return hunter.getPlayer().isIn2DRadius(startLocation, Config.AUTO_FARM_MIN_RADIUS_DISABLE);
    }

    public void setStartLocation(Location startLocation) {
        this.startLocation = startLocation;
    }

    public void setMoving(boolean moving) {
        this.isMoving = moving;
    }

    public void backStartLocation() {
        final Player player = hunter.getPlayer();

        if (isAtDestination()) {
            setMoving(false);
            player.setTarget(null);
            return;
        }

        player.getAI().tryToMoveTo(startLocation, null);
        if (!isMoving) {
            setMoving(true);
            player.setTarget(null);
        }
    }

    public boolean checkTargetIsStuck(Monster target) {
        if (target == null) return false;

        final long now = System.currentTimeMillis();
        if (lockedTarget == null || lockedTarget != target) {
            this.lockedTarget = target;
            this.targetLockedAt = now;
            this.targetLastHitTime = now;
            return false;

        }
        if (target.getStatus().getHpRatio() != 1) this.targetLastHitTime = now;

        final long timeLastHit = now - this.targetLastHitTime;
        final long timeLocked = now - this.targetLockedAt;

        return timeLastHit > Config.AUTO_FARM_TOLERABLE_DELAY_HIT || timeLocked > Config.AUTO_FARM_TOLERABLE_SAME_TARGET;
    }

    public void resetTargetStuck() {
        this.lockedTarget = null;
        this.targetLockedAt = 0L;
        this.targetLastHitTime = 0L;
    }
}
