package gameserver.custom.model;

import commons.logging.CLogger;
import gameserver.enums.TeamType;
import gameserver.enums.ZoneId;
import gameserver.geoengine.GeoEngine;
import gameserver.model.WorldObject;
import gameserver.model.actor.Creature;
import gameserver.model.actor.Player;
import gameserver.model.actor.ai.type.PlayerAI;
import gameserver.model.actor.instance.Chest;
import gameserver.model.actor.instance.Monster;
import gameserver.model.actor.status.PlayerStatus;
import gameserver.model.group.Party;
import gameserver.model.location.Location;
import gameserver.skills.L2Skill;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class HunterImpl implements Hunter {

    protected static final CLogger LOGGER = new CLogger(HunterImpl.class.getName());
    public static final int MAX_SKILLS = 6;
    private static final int SWEEPER_ID = 42;

    private final Player player;
    private volatile Map<Integer, Integer> skills;
    volatile boolean active;
    private HunterType hunterType;
    private int radius;
    private int minMp;
    private int minHp;
    private final AtomicInteger remainingMinutes = new AtomicInteger();

    private boolean keepStartLocation;
    private boolean onlyRespectedTargets;
    private boolean followLeaderParty;
    private boolean assistLeaderParty;
    private boolean onlyTargetsSpoiled;

    private EditType editingType;
    private boolean hasDeadTargetSkill;

    private final HunterMovement hunterMovement;
    private int minRange;


    public HunterImpl(Player player, HunterType hunterType, int radius, boolean keepStartLocation, boolean onlyRespectedTargets, boolean followLeaderParty, boolean assistLeaderParty, boolean onlyTargetsSpoiled, int remainingMinutes, int minMp, int minHp) {
        this.player = player;
        this.hunterType = hunterType;
        this.radius = radius;
        this.keepStartLocation = keepStartLocation;
        this.onlyRespectedTargets = onlyRespectedTargets;
        this.followLeaderParty = followLeaderParty;
        this.assistLeaderParty = assistLeaderParty;
        this.onlyTargetsSpoiled = onlyTargetsSpoiled;
        this.remainingMinutes.set(remainingMinutes);
        this.minMp = minMp;
        this.minHp = minHp;
        this.skills = new HashMap<>(MAX_SKILLS);
        this.hunterMovement = new HunterMovement(this);
        calculateRange();
    }

    public HunterImpl(Player player, int remainingMinutes) {
        this(player, player.isMageClass() ? HunterType.MAGE : HunterType.FIGHTER, 1200, true, true, false, false, false, remainingMinutes, 50, 50);
    }


    @Override
    public void executeRoutine() {
        if (hunterMovement.isInMovement()) {
            hunterMovement.backStartLocation();
            return;
        }

        if (followLeaderParty) followPartyLeader();

        final WorldObject target = player.getTarget();
        if (target == null) castSelfSkills();

        final Monster currentMonster = selectTargetMonster(target);
        if (currentMonster == null) return;

        if (target != currentMonster) player.setTarget(currentMonster);
        if (onlyTargetsSpoiled && !currentMonster.getSpoilState().isSpoiled()) return;

        attackMonster(currentMonster);
    }

    @Override
    public void start() {
        if (player.isInsideZone(ZoneId.PVP) || player.isInsideZone(ZoneId.CASTLE)) {
            player.sendMessage("It's not possible to do that in this location.");
            return;
        }
        if (player.isDead()) {
            player.sendMessage("You can't do that right now.");
            return;
        }
        hunterMovement.setStartLocation(new Location(player.getPosition()));
        this.active = true;
        player.setTeam(hunterType == HunterType.FIGHTER ? TeamType.BLUE : TeamType.RED);
        player.broadcastUserInfo();
        player.sendMessage("Your automatic farm system has been enabled.");
    }

    @Override
    public void stop() {
        this.active = false;
        player.setTeam(TeamType.NONE);
        player.broadcastUserInfo();
        player.sendMessage("Your automatic farm system has been disabled.");
    }

    @Override
    public void resetEditing() {
        setEditingType(null);
    }

    @Override
    public void removeSkillFromSlot(int slot) {
        modifySkills(skills -> {
            final Integer skillId = skills.remove(slot);
            if (skillId != null && skillId == SWEEPER_ID) hasDeadTargetSkill = false;
        });
        calculateRange();
    }

    @Override
    public void changeHunterType() {
        final HunterType newHunterType = hunterType == HunterType.FIGHTER ? HunterType.MAGE : HunterType.FIGHTER;
        this.hunterType = newHunterType;
        if (active) {
            player.setTeam(newHunterType == HunterType.FIGHTER ? TeamType.BLUE : TeamType.RED);
            player.broadcastUserInfo();
        }
        player.sendMessage("Your class has been successfully changed.");
        calculateRange();
    }

    @Override
    public void changeState() {
        if (!active) {
            start();
            return;
        }
        stop();
    }

    @Override
    public void addMinutes(int minutes) {
        remainingMinutes.addAndGet(minutes);
    }

    @Override
    public void removeMinutes(int minutes) {
        this.remainingMinutes.updateAndGet(v -> Math.max(0, v - minutes));
    }

    @Override
    public void attackMonster(Monster monster) {
        if (monster == null) return;

        if (hunterMovement.isFarFromStartLocation() && keepStartLocation) {
            hunterMovement.backStartLocation();
            return;
        }
        if (hunterMovement.checkTargetIsStuck(monster)) {
            hunterMovement.resetTargetStuck();
            hunterMovement.backStartLocation();
            return;
        }
        if (monster.isDead()) return;

        final PlayerStatus status = player.getStatus();
        final PlayerAI ai = player.getAI();

        final int attackRange = Math.max(40, minRange);
        if (player.distance2D(monster) <= attackRange) {
            if (hunterType == HunterType.FIGHTER) ai.tryToAttack(monster);

            if (skills.isEmpty()) return;

            if (status.getMpRatio() <= (double) minMp / 100) {
                player.sendMessage("You have reached your MP limit, use mana potion.");
                return;
            }

            final boolean lowHp = status.getHpRatio() < minHp / 100.0;

            for (final int skillId : skills.values()) {
                final L2Skill skill = player.getSkill(skillId);
                if (skill == null) continue;

                final Creature target = switch (skill.getSkillType()) {
                    case SWEEP -> null;
                    case HEAL -> lowHp ? player : null;
                    case BUFF -> player.getFirstEffect(skill) == null ? player : null;
                    case DEBUFF -> monster.getFirstEffect(skill) == null ? monster : null;
                    case SPOIL -> !monster.getSpoilState().isSpoiled() ? monster : null;
                    default -> monster;
                };

                if (target == null) continue;

                if (player.getTarget() != target) player.setTarget(target);
                ai.tryToCast(target, skill);
            }
            return;
        }
        ai.tryToMoveTo(monster.getPosition(), null);
    }

    @Override
    public void castSelfSkills() {
        if (skills.isEmpty()) return;

        final PlayerAI ai = player.getAI();
        final PlayerStatus status = player.getStatus();

        if (status.getMpRatio() <= (double) minMp / 100) return;

        final boolean lowHp = status.getHpRatio() < minHp / 100.0;

        for (final int skillId : skills.values()) {
            final L2Skill skill = player.getSkill(skillId);
            if (skill == null) continue;

            final boolean shouldCast = switch (skill.getSkillType()) {
                case HEAL -> lowHp;
                case BUFF -> player.getFirstEffect(skill) == null;
                default -> false;
            };
            if (!shouldCast) continue;

            if (player.getTarget() != player) player.setTarget(player);

            ai.tryToCast(player, skill);
        }
    }

    @Override
    public void followPartyLeader() {
        final Party party = player.getParty();
        if (party == null || party.isLeader(player)) return;

        player.getAI().tryToFollow(party.getLeader(), false);
    }

    @Override
    public void calculateRange() {
        int maxRange = player.getStatus().getPhysicalAttackRange();

        for (int skillId : skills.values()) {
            final L2Skill skill = player.getSkill(skillId);
            if (skill == null) continue;

            final int castRange = skill.getCastRange();
            if (castRange > maxRange) {
                maxRange = castRange;
            }
        }

        this.minRange = maxRange;
    }

    @Override
    public HunterType getTypeHunter() {
        return hunterType;
    }

    @Override
    public EditType getEditingType() {
        return editingType;
    }

    @Override
    public Map<Integer, Integer> getSkills() {
        return skills;
    }

    @Override
    public int getSkillBySlot(int slot) {
        final Integer skillId = skills.get(slot);
        return skillId == null ? -1 : skillId;
    }

    @Override
    public int getSlotBySkill(int skillId) {
        for (Integer slot : skills.keySet()) {
            final int id = skills.get(slot);
            if (slot != null && skillId == id) return slot;
        }
        return -1;
    }

    @Override
    public int findFirstEmptySlot() {
        for (int slot = 0; slot < MAX_SKILLS; slot++) {
            if (!skills.containsKey(slot)) return slot;
        }
        return -1;
    }

    @Override
    public int slotsInUse() {
        return skills.size();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public int getId() {
        return player.getObjectId();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public int getRadius() {
        return radius;
    }

    @Override
    public int getMpLimit() {
        return minMp;
    }

    @Override
    public int getHpLimit() {
        return minHp;
    }

    @Override
    public int getRemainingMinutes() {
        return remainingMinutes.get();
    }

    @Override
    public Monster findNearestMonster() {
        final GeoEngine geoEngine = GeoEngine.getInstance();
        final Nearest nearest = new Nearest(null, radius);

        player.forEachKnownTypeInRadius(Monster.class, radius, monster -> {
            if (monster == null || monster.isDead() || monster.isRaidRelated() || monster instanceof Chest) return;

            if (!geoEngine.canSeeTarget(player, monster)) return;

            if (onlyRespectedTargets) {
                final WorldObject monsterTarget = monster.getTarget();
                if (monsterTarget != null && monsterTarget != player) return;
            }

            final double dist = player.distance2D(monster);
            if (dist < nearest.radius) {
                nearest.monster = monster;
                nearest.radius = dist;
            }

        });

        return nearest.monster;
    }

    @Override
    public void setEditingType(EditType editType) {
        this.editingType = editType;
    }

    @Override
    public void assignSkillToSlot(int slot, int skillId) {
        modifySkills(skills -> skills.put(slot, skillId));

        if (skillId == SWEEPER_ID) hasDeadTargetSkill = true;
        calculateRange();
    }

    @Override
    public void modifySkills(Consumer<Map<Integer, Integer>> updater) {
        final Map<Integer, Integer> newMap = new HashMap<>(this.skills);
        updater.accept(newMap);
        this.skills = newMap;
    }

    @Override
    public void setRadius(int radius) {
        this.radius = radius;
    }

    @Override
    public void setKeepStartLocation(boolean startLocation) {
        if (!startLocation) hunterMovement.setMoving(false);
        this.keepStartLocation = startLocation;
    }

    @Override
    public void setOnlyRespectedTargets(boolean onlyRespectedTargets) {
        this.onlyRespectedTargets = onlyRespectedTargets;
    }

    @Override
    public void setAssistLeaderParty(boolean assistLeaderParty) {
        this.assistLeaderParty = assistLeaderParty;
    }

    @Override
    public void setFollowLeaderParty(boolean followLeaderParty) {
        this.followLeaderParty = followLeaderParty;
    }

    @Override
    public void setOnlyTargetsSpoiled(boolean onlyTargetsSpoiled) {
        this.onlyTargetsSpoiled = onlyTargetsSpoiled;
    }

    @Override
    public void setMinMp(int minMp) {
        this.minMp = minMp;
    }

    @Override
    public void setMinHp(int minHp) {
        this.minHp = minHp;

    }

    @Override
    public boolean isHasDeadTargetSkill() {
        return hasDeadTargetSkill;
    }

    @Override
    public boolean skillIsAlready(int skillId) {
        for (Integer id : skills.values()) {
            if(id == skillId) return true;
        }
        return false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isDead() {
        return player.isDead();
    }

    @Override
    public boolean isOffline() {
        return !player.isOnline();
    }

    @Override
    public boolean isKeepStartLocation() {
        return keepStartLocation;
    }

    @Override
    public boolean isOnlyRespectedTargets() {
        return onlyRespectedTargets;
    }

    @Override
    public boolean isAssistLeaderParty() {
        return assistLeaderParty;
    }

    @Override
    public boolean isFollowLeaderParty() {
        return followLeaderParty;
    }

    @Override
    public boolean isOnlyTargetsSpoiled() {
        return onlyTargetsSpoiled;
    }

    private Monster selectTargetMonster(WorldObject target) {
        if (target instanceof Monster monster && GeoEngine.getInstance().canSeeTarget(player, monster)) {
            if (!monster.isDead()) return monster;

            if (hasDeadTargetSkill && monster.getSpoilState().isSpoiled()) {
                final L2Skill sweeper = player.getSkill(SWEEPER_ID);
                if (sweeper != null) {
                    player.getAI().tryToCast(monster, sweeper);
                    return monster;
                }
            }
        }
        return assistLeaderParty ? getTargetLeader() : findNearestMonster();
    }

    private Monster getTargetLeader() {
        final Party party = player.getParty();
        if (party == null || party.isLeader(player)) return null;

        final Player leader = party.getLeader();
        final WorldObject target = leader.getTarget();
        if (!(target instanceof Monster monster)) return null;

        return monster;
    }

    private static final class Nearest {
        private Monster monster;
        private double radius;

        public Nearest(Monster monster, double radius) {
            this.monster = monster;
            this.radius = radius;
        }
    }
}
