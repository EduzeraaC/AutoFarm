package gameserver.custom.view;

import gameserver.custom.model.EditType;
import gameserver.custom.model.Hunter;
import gameserver.custom.model.HunterType;
import gameserver.enums.skills.SkillTargetType;
import gameserver.enums.skills.SkillType;
import gameserver.model.actor.Player;
import gameserver.network.serverpackets.NpcHtmlMessage;
import gameserver.skills.L2Skill;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static gameserver.custom.model.HunterImpl.MAX_SKILLS;

public abstract class AutoFarmView {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.of("pt", "BR"));

    private static final int MAX_SKILLS_PAGE = 7;
    private static final String PATH_AUTO_FARM = "html/mods/autofarm/";
    private static final String MAGIC_ICON = "L2UI_CH3.party_styleicon5_3";
    private static final String FIGHTER_ICON = "L2UI_CH3.party_styleicon1_3";
    private static final String ON = "<font color=00FF00>ON</font>";
    private static final String OFF = "<font color=FF0000>OFF</font>";
    private static final String CHECKED = "l2ui.CheckBox_checked";
    private static final String NOT_CHECKED = "l2ui.CheckBox";

    private static final SkillComparator SKILL_COMPARATOR = new SkillComparator();

    public void showIndex(Hunter hunter) {
        final Player player = hunter.getPlayer();

        final NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setFile(player.getLocale(), PATH_AUTO_FARM + "index.htm");

        // Remaining time
        fillRemainingTime(html, hunter);

        // Basic replacements
        fillBasicInfo(html, hunter);

        // Handle edit/view sections for radius, HP, MP
        fillEditSections(html, hunter, hunter.getEditingType());

        // Checkbox states
        fillCheckboxes(html, hunter);

        // Skills display
        fillSkills(html, hunter);

        // Send HTML packet
        player.sendPacket(html);
    }

    public void showSkills(Hunter hunter, int page) {
        final Player player = hunter.getPlayer();

        final NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setFile(player.getLocale(), PATH_AUTO_FARM + "skills.htm");

        html.replace("%pagination%", getPage(hunter, page));
        html.replace("%page%", page);
        html.replace("%player%", hunter.getName());
        html.replace("%sizeskills%", hunter.slotsInUse());

        player.sendPacket(html);
    }

    public void showBuyTime(Hunter hunter) {
        final Player player = hunter.getPlayer();

        final NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setFile(player.getLocale(), PATH_AUTO_FARM + "buytime.htm");

        player.sendPacket(html);
    }

    private void fillRemainingTime(NpcHtmlMessage html, Hunter hunter) {
        html.replace("%time%", formatMinutes(hunter.getRemainingMinutes()));
    }

    private void fillBasicInfo(NpcHtmlMessage html, Hunter hunter) {
        html.replace("%player%", hunter.getName());
        html.replace("%state_autofarm%", hunter.isActive() ? ON : OFF);
        html.replace("%state_autofarmdesc%", hunter.isActive() ? "OFF" : "ON");

        boolean isFighter = hunter.getTypeHunter() == HunterType.FIGHTER;
        html.replace("%icon_autofarm%", isFighter ? FIGHTER_ICON : MAGIC_ICON);
        html.replace("%name_autofarm%", isFighter ? "Fighter" : "Magic");
    }

    private void fillEditSections(NpcHtmlMessage html, Hunter hunter, EditType editType) {
        applySection(html, "radius", String.valueOf(hunter.getRadius()), 50, editType == EditType.EDIT_RADIUS, false);
        applySection(html, "hp", hunter.getHpLimit() + "%", 48, editType == EditType.EDIT_HP, true);
        applySection(html, "mp", hunter.getMpLimit() + "%", 48, editType == EditType.EDIT_MP, true);
    }

    private void applySection(NpcHtmlMessage html, String field, String value, int size, boolean isEdit, boolean requiresResize) {
        if (isEdit) {
            applyEditSection(html, field, size);
            return;
        }
        applyViewSection(html, field, value, requiresResize);
    }

    private void applyEditSection(NpcHtmlMessage html, String field, int size) {
        if (field.equals("radius")) {
            html.replace("%" + field + "_state%", "<img height=\"3\"><edit width=\"" + size + "\" var=\"new" + field + "\">");
        } else {
            html.replace("%" + field + "_state%", "<edit width=\"" + size + "\" var=\"new" + field + "\"><img height=\"5\">");
        }

        html.replace("%" + field + "_action%", "bypass autofarm save " + field + " $new" + field);
        html.replace("%" + field + "_value%", "Save");
    }

    private void applyViewSection(NpcHtmlMessage html, String field, String value, boolean requiresResize) {
        html.replace("%" + field + "_action%", "bypass autofarm edit " + field);
        html.replace("%" + field + "_value%", "Edit");

        if (requiresResize) {
            html.replace("%" + field + "_state%", "<img height=\"5\"><font color=\"LEVEL\">" + value + "</font><img height=\"2\">");
            return;
        }
        html.replace("%" + field + "_state%", "<font color=\"LEVEL\">" + NUMBER_FORMAT.format(Integer.parseInt(value)) + "</font>");
    }

    private void fillCheckboxes(NpcHtmlMessage html, Hunter hunter) {
        html.replace("%follow_state%", hunter.isFollowLeaderParty() ? CHECKED : NOT_CHECKED);
        html.replace("%assist_state%", hunter.isAssistLeaderParty() ? CHECKED : NOT_CHECKED);
        html.replace("%keep_state%", hunter.isKeepStartLocation() ? CHECKED : NOT_CHECKED);
        html.replace("%respect_state%", hunter.isOnlyRespectedTargets() ? CHECKED : NOT_CHECKED);
        html.replace("%spoiled_state%", hunter.isOnlyTargetsSpoiled() ? CHECKED : NOT_CHECKED);
    }

    private void fillSkills(NpcHtmlMessage html, Hunter hunter) {
        boolean hasSkills = hunter.slotsInUse() > 0;

        for (int slot = 0; slot < MAX_SKILLS; slot++) {
            final int skillId = hunter.getSkillBySlot(slot);
            if (skillId >= 0) {
                hasSkills = true;
                applySkillSlot(html, slot, skillId);
            } else {
                applyEmptySlot(html, slot, hasSkills);
            }
        }

        html.replace("%fix%", hasSkills ? "" : "<img height=\"6\">");
    }

    private void applySkillSlot(NpcHtmlMessage html, int slot, int skillId) {
        html.replace("%1_negative_height%" + slot, -2);
        html.replace("%skill_icon%" + slot, getIconSkill(skillId));
        html.replace("%skill_width%" + slot, 31);
        html.replace("%skill_height%" + slot, 32);
        html.replace("%2_negative_height%" + slot, -48);
        html.replace("%bg_icon%" + slot, "L2UI_CH3.refineslot2");
        html.replace("%bg_width%" + slot, 36);
        html.replace("%bg_height%" + slot, 48);
        html.replace("%action%" + slot, "bypass autofarm remove " + slot);
    }

    private void applyEmptySlot(NpcHtmlMessage html, int slot, boolean hasSkills) {
        html.replace("%1_negative_height%" + slot, -12);
        html.replace("%skill_icon%" + slot, "L2UI_CH3.refineslot2");
        html.replace("%skill_width%" + slot, 36);
        html.replace("%skill_height%" + slot, 48);
        html.replace("%2_negative_height%" + slot, hasSkills ? -48 : -42);
        html.replace("%bg_icon%" + slot, "L2UI_CH3.multisell_plusicon");
        html.replace("%bg_width%" + slot, 31);
        html.replace("%bg_height%" + slot, 30);
        html.replace("%action%" + slot, "bypass autofarm skills 1");
    }

    private String getIconSkill(int skillId) {
        return (skillId < 10) ? "icon.skill000" + skillId : (skillId < 100) ? "icon.skill00" + skillId : (skillId < 1000) ? "icon.skill0" + skillId : "icon.skill" + skillId;
    }

    private String getPage(Hunter hunter, int page) {
        final Player player = hunter.getPlayer();
        final List<L2Skill> copyOfSkills = new ArrayList<>();

        for (L2Skill skill : player.getSkills().values()) {
            if (!isSkillCompatible(skill) || hunter.skillIsAlready(skill.getId())) continue;
            copyOfSkills.add(skill);
        }
        final int size = copyOfSkills.size();
        final int totalPages = (size + MAX_SKILLS_PAGE - 1) / MAX_SKILLS_PAGE;

        if (page > totalPages) page = totalPages;

        final int fromIndex = (page - 1) * MAX_SKILLS_PAGE;
        final int toIndex = Math.min(page * MAX_SKILLS_PAGE, size);

        copyOfSkills.sort(SKILL_COMPARATOR);

        final StringBuilder sb = new StringBuilder(size * 150);
        int row = 0;

        sb.append("<br><img src=\"L2UI.SquareGray\" width=280 height=1>");

        if (size != 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                final L2Skill skill = copyOfSkills.get(i);
                final int skillId = skill.getId();
                final String icon = getIconSkill(skillId);

                sb.append("<table width=280 bgcolor=000000><tr>");
                sb.append("<td height=32 width=32><img src=\"").append(icon)
                        .append("\" width=32 height=32></td>");
                sb.append("<td width=5></td>");
                sb.append("<td width=185><font color=\"B09878\">")
                        .append(skill.getName())
                        .append("</font></td>");
                sb.append("<td>");
                sb.append("<button action=\"bypass autofarm select ")
                        .append(skillId).append(' ').append(page)
                        .append("\" width=32 height=32 ")
                        .append("back=\"L2UI_CH3.mapbutton_zoomin2\" ")
                        .append("fore=\"L2UI_CH3.mapbutton_zoomin1\">");
                sb.append("</td></tr></table>");
                sb.append("<img src=\"L2UI.SquareGray\" width=280 height=1>");
                row++;
            }
        }

        sb.append("<img height=39>".repeat(Math.max(0, MAX_SKILLS_PAGE - row)));

        sb.append("<br><img src=\"L2UI.SquareGray\" width=280 height=1>");

        final int prevPage = page > 1 ? page - 1 : 1;
        final int nextPage = page < totalPages ? page + 1 : totalPages;

        sb.append("<table width=100% bgcolor=000000><tr>");

        sb.append("<td align=left width=70>")
                .append("<button action=\"bypass autofarm page ")
                .append(prevPage).append(' ').append(page)
                .append("\" back=\"L2UI_CH3.shortcut_prev_down\" ")
                .append("fore=\"L2UI_CH3.shortcut_prev\" width=16 height=16>")
                .append("</td>");

        sb.append("<td align=center width=115><font color=LEVEL>Page ")
                .append(page).append('/').append(totalPages)
                .append("</font></td>");

        sb.append("<td align=right width=70>")
                .append("<button action=\"bypass autofarm page ")
                .append(nextPage).append(' ').append(page)
                .append("\" back=\"L2UI_CH3.shortcut_next_down\" ")
                .append("fore=\"L2UI_CH3.shortcut_next\" width=16 height=16>")
                .append("</td>");

        sb.append("</tr></table>");
        sb.append("<img src=\"L2UI.SquareGray\" width=280 height=1>");

        return sb.toString();
    }

    private String formatMinutes(int totalMinutes) {
        final int hours = totalMinutes / 60;
        final int minutes = totalMinutes % 60;

        return String.format("%d hour%s and %d minutes",
                hours, (hours > 1 ? "s" : ""),
                minutes);
    }

    private boolean isSkillCompatible(L2Skill skill) {
        if (skill.getSkillType() == SkillType.SWEEP) return true;

        if (!skill.isActive() || skill.isSiegeSummonSkill())
            return false;

        if (skill.getTargetType().name().contains("CORPSE"))
            return false;

        if (skill.getSkillType() == SkillType.SLEEP && skill.getTargetType() == SkillTargetType.ONE)
            return false;

        switch (skill.getSkillType()) {
            case SUMMON:
            case SUMMON_CREATURE:
            case SUMMON_FRIEND:
            case SUMMON_PARTY:
            case COMMON_CRAFT:
            case DWARVEN_CRAFT:
            case TELEPORT:
            case RECALL:
            case RESURRECT:
            case TAKE_CASTLE:
            case SIEGE_FLAG:
            case SEED:
            case SIGNET:
            case SIGNET_CASTTIME:
            case FUSION:
            case STRIDER_SIEGE_ASSAULT:
            case UNLOCK:
                return false;
        }

        if (skill.getTargetType() == SkillTargetType.SUMMON) {
            return false;
        }

        return switch (skill.getName()) {
            case "Aura Symphony",
                    "Inferno",
                    "Blizzard",
                    "Demon Wind",
                    "Elemental Assault",
                    "Elemental Symphony",
                    "Elemental Storm",
                    "Harmony of Noblesse",
                    "Symphony of Noblesse",
                    "Clan Gate",
                    "Wyvern Aegis" -> false;

            default -> true;
        };

    }

    private static class SkillComparator implements Comparator<L2Skill> {

        @Override
        public int compare(L2Skill o1, L2Skill o2) {
            return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
        }
    }
}
