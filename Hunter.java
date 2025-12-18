package gameserver.custom.model;

import gameserver.model.actor.Player;
import gameserver.model.actor.instance.Monster;

import java.util.Map;
import java.util.function.Consumer;

public interface Hunter {

    // Methods
    void executeRoutine();

    void start();

    void stop();

    void resetEditing();

    void removeSkillFromSlot(int slot);

    void changeHunterType();

    void changeState();

    void addMinutes(int minutes);

    void removeMinutes(int minutes);

    void attackMonster(Monster monster);

    void castSelfSkills();

    void followPartyLeader();


    // Getters

    HunterType getTypeHunter();

    EditType getEditingType();

    Map<Integer, Integer> getSkills();

    int getSkillBySlot(int slot);

    int getSlotBySkill(int skillId);

    int findFirstEmptySlot();

    int slotsInUse();

    String getName();

    int getId();

    Player getPlayer();

    int getRadius();

    int getMpLimit();

    int getHpLimit();

    int getRemainingMinutes();

    Monster findNearestMonster();

    // Setters

    void setEditingType(EditType editType);

    void assignSkillToSlot(int slot, int skillId);

    void modifySkills(Consumer<Map<Integer, Integer>> updater);

    void setRadius(int radius);

    void setKeepStartLocation(boolean startLocation);

    void setOnlyRespectedTargets(boolean onlyRespectedTargets);

    void setAssistLeaderParty(boolean assistLeaderParty);

    void setFollowLeaderParty(boolean followLeaderParty);

    void setOnlyTargetsSpoiled(boolean onlyTargetsSpoiled);

    void setMinMp(int minMp);

    void setMinHp(int minHp);

    void calculateRange();

    // Booleans

    boolean isHasDeadTargetSkill();

    boolean skillIsAlready(int skillId);

    boolean isActive();

    boolean isDead();

    boolean isOffline();

    boolean isKeepStartLocation();

    boolean isOnlyRespectedTargets();

    boolean isAssistLeaderParty();

    boolean isFollowLeaderParty();

    boolean isOnlyTargetsSpoiled();
}
