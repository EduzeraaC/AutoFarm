package gameserver.custom.handler;

import commons.logging.CLogger;
import gameserver.custom.manager.AutoFarmManager;
import gameserver.custom.model.EditType;
import gameserver.custom.model.Hunter;
import gameserver.custom.view.AutoFarmView;
import gameserver.handler.IBypassHandler;
import gameserver.model.actor.Creature;
import gameserver.model.actor.Player;
import gameserver.model.itemcontainer.PcInventory;
import gameserver.network.serverpackets.ItemList;
import config.Config;

import static commons.math.MathUtil.parseInt;
import static gameserver.custom.model.HunterImpl.MAX_SKILLS;

public class AutoFarmHandler extends AutoFarmView implements IBypassHandler {

    protected static final CLogger LOGGER = new CLogger(AutoFarmHandler.class.getName());
    private static final String[] VIEW = { "autofarm" };

    @Override
    public boolean useBypass(String command, Player player, Creature creature) {
        final String[] args = command.split(" ");
        if (args.length < 2) {
            LOGGER.info("An error occurred while running the Auto Farm View Handler.");
            return false;
        }

        final AutoFarmManager autoFarmManager = AutoFarmManager.getInstance();
        final Hunter hunter = autoFarmManager.getOrDefault(player);
        final String arg = args[1].toLowerCase();


        switch (arg) {
            case "index" -> showIndex(hunter);

            case "state" -> {
                if (hunter.getRemainingMinutes() <= 0) {
                    player.sendMessage("You did not have available auto farm time.");
                    return false;
                }
                hunter.changeState();
                showIndex(hunter);
            }

            case "switch" -> {
                hunter.changeHunterType();
                showIndex(hunter);
            }

            case "buytime" -> {
                if (args.length > 2) {
                    final PcInventory inventory = player.getInventory();
                    final int time = parseInt(args[2]);
                    final int itemId = identifyItemTime(time);

                    if (inventory.getItemCount(itemId) <= 0) {
                        player.sendMessage("You did not have this auto farm amulet in your inventory.");
                        return false;
                    }

                    player.getInventory().destroyItemByItemId(3470, 1);
                    player.sendPacket(new ItemList(player, true));

                    autoFarmManager.addMinutesAndSave(hunter, time * 60);
                    player.sendMessage("The time was added successfully.");

                    showIndex(hunter);
                    return false;
                }
                showBuyTime(hunter);
            }

            case "edit" -> {
                handleEditCommand(hunter, args[2]);
                showIndex(hunter);
            }

            case "save" -> {
                if (args.length == 4) {
                    handleSaveCommand(hunter, args[2], args[3]);
                }
                showIndex(hunter);
            }

            case "skills" -> showSkills(hunter, parseInt(args[2]));

            case "page" -> {
                final int page = parseInt(args[2]);
                final int currentPage = parseInt(args[3]);
                if (page == currentPage) return false;

                showSkills(hunter, page);
            }

            case "select" -> {
                final int firstEmpty = hunter.findFirstEmptySlot();
                if (firstEmpty == -1) {
                    player.sendMessage("You have reached the limit of " + MAX_SKILLS + " skills.");
                    showIndex(hunter);
                    return false;
                }
                final int skillId = parseInt(args[2]);
                final int currentPage = parseInt(args[3]);

                hunter.assignSkillToSlot(firstEmpty, skillId);
                showSkills(hunter, currentPage);
            }

            case "remove" -> {
                final int slot = parseInt(args[2]);

                hunter.removeSkillFromSlot(slot);
                showIndex(hunter);
            }
        }
        return true;
    }

    @Override
    public String[] getBypassList() {
        return VIEW;
    }

    private void handleEditCommand(Hunter hunter, String arg) {
        switch (arg.toLowerCase()) {
            case "radius" -> hunter.setEditingType(EditType.EDIT_RADIUS);
            case "hp"     -> hunter.setEditingType(EditType.EDIT_HP);
            case "mp"     -> hunter.setEditingType(EditType.EDIT_MP);
            case "follow" -> hunter.setFollowLeaderParty(!hunter.isFollowLeaderParty());
            case "assist" -> hunter.setAssistLeaderParty(!hunter.isAssistLeaderParty());
            case "keep"   -> hunter.setKeepStartLocation(!hunter.isKeepStartLocation());
            case "respect"-> hunter.setOnlyRespectedTargets(!hunter.isOnlyRespectedTargets());
            case "spoiled"-> hunter.setOnlyTargetsSpoiled(!hunter.isOnlyTargetsSpoiled());
        }
    }

    private void handleSaveCommand(Hunter hunter, String arg, String value) {
        hunter.resetEditing();
        final int parsedValue = parseInt(value);
        if (parsedValue <= 0) return;

        switch (arg.toLowerCase()) {
            case "radius" -> hunter.setRadius(Math.min(3000, parsedValue));
            case "hp" -> hunter.setMinHp(Math.max(10, Math.min(parsedValue, 90)));
            case "mp" -> hunter.setMinMp(Math.max(10, Math.min(parsedValue, 90)));
        }
    }

    private int identifyItemTime(int time) {
        return switch (time) {
            case 2 -> Config.AUTO_FARM_ITEM_ID_2_HOURS;
            case 4 -> Config.AUTO_FARM_ITEM_ID_4_HOURS;
            case 10 -> Config.AUTO_FARM_ITEM_ID_10_HOURS;
            default -> -1;
        };
    }
}
