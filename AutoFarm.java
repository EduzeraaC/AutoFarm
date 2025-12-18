package gameserver.handler.voicedcommandhandlers;

import gameserver.custom.manager.AutoFarmManager;
import gameserver.custom.model.Hunter;
import gameserver.custom.view.AutoFarmView;
import gameserver.handler.IVoicedCommandHandler;
import gameserver.model.actor.Player;
import config.Config;

public class AutoFarm extends AutoFarmView implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = { "autofarm" };
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target) {
		if (!Config.AUTOFARM_ENABLED) {
			player.sendMessage(player.getSysString(10_200));
			return false;
		}
		final Hunter hunter = AutoFarmManager.getInstance().getOrDefault(player);
		if (hunter.getEditingType() != null) {
			hunter.resetEditing();
		}
		showIndex(hunter);
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList() {
		return VOICED_COMMANDS;
	}
}
