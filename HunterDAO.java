package gameserver.custom.dao;

import gameserver.custom.model.Hunter;
import gameserver.custom.model.HunterImpl;
import gameserver.model.actor.Player;
import config.Config;

import java.util.Collection;

public class HunterDAO extends BaseDAO {

    private static final String UPDATE = "UPDATE auto_farm SET remaining_minutes = ? WHERE player_id = ?";
    private static final String SELECT = "SELECT remaining_minutes FROM auto_farm WHERE player_id = ?";
    private static final String UPSERT = "INSERT INTO auto_farm (player_id, name, remaining_minutes) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), remaining_minutes = VALUES(remaining_minutes);";

    public Hunter load(Player player) {
        return query(
                SELECT,
                ps -> {
                    ps.setInt(1, player.getObjectId());
                    return ps.executeQuery();
                },
                rs -> rs.next() ? new HunterImpl(player, rs.getInt("remaining_minutes")) : null);
    }

    public void upsert(Hunter hunter) {
        execute(UPSERT, ps -> {
            ps.setInt(1, hunter.getId());
            ps.setString(2, hunter.getName());
            ps.setInt(3, hunter.getRemainingMinutes());
        });
    }

    public void updateBatch(Collection<Hunter> hunters) {
        if (hunters == null || hunters.isEmpty()) return;

        executeBatch(UPDATE, ps -> {
            int count = 0;

            for (Hunter hunter : hunters) {
                if (hunter == null) continue;

                ps.setInt(1, hunter.getRemainingMinutes());
                ps.setInt(2, hunter.getId());
                ps.addBatch();
                count++;

                if (count % Config.AUTO_FARM_BATCH_SIZE == 0) {
                    ps.executeBatch();
                    ps.clearBatch();
                }
            }

            if (count % Config.AUTO_FARM_BATCH_SIZE != 0) {
                ps.executeBatch();
                ps.clearBatch();
            }
        });
    }

}
