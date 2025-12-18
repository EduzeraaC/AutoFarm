package gameserver.custom.manager;

import commons.pool.ThreadPool;
import gameserver.custom.dao.HunterDAO;
import gameserver.custom.model.Hunter;
import gameserver.custom.model.HunterImpl;
import gameserver.model.actor.Player;
import config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AutoFarmManager implements Runnable {

    private int tickCount = 0;

    protected final Map<Integer, Hunter> hunters = new ConcurrentHashMap<>();
    protected final Queue<Hunter> pendingUpdates = new ConcurrentLinkedQueue<>();
    protected final Queue<Integer> toRemove = new ConcurrentLinkedQueue<>();

    private final HunterDAO hunterDAO;

    public AutoFarmManager() {
        this.hunterDAO = new HunterDAO();
        ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
    }

    public void add(Hunter hunter) {
        add(hunter.getId(), hunter);
    }

    public void add(int id, Hunter hunter) {
        hunters.put(id, hunter);
    }

    public Hunter get(Player player) {
        return hunters.get(player.getObjectId());
    }

    public Hunter getOrDefault(Player player) {
        Hunter hunter = get(player);
        if (hunter != null) return hunter;

        hunter = hunterDAO.load(player);

        if (hunter == null) {
            hunter = new HunterImpl(player, 0);
        }

        add(hunter);
        return hunter;
    }

    public void remove(Player player) {
        remove(player.getObjectId());
    }

    public void remove(Hunter hunter) {
        remove(hunter.getId());
    }

    public void remove(int id) {
        hunters.remove(id);
    }

    public void save() {
        hunterDAO.updateBatch(hunters.values());
    }

    public void addMinutesAndSave(Hunter hunter, int minutes) {
        hunter.addMinutes(minutes);
        hunterDAO.upsert(hunter);
    }

    @Override
    public void run() {
        if (hunters.isEmpty()) return;

        tickCount++;

        hunters.forEach(this::processHunter);

        if (tickCount >= Config.AUTO_FARM_SAVE_INTERVAL) {
            flushPendingUpdates();
            tickCount = 0;
        }

        flushRemoving();
    }

    private void processHunter(int id, Hunter hunter) {
        if (hunter == null) return;

        if (hunter.isOffline()) {
            pendingUpdates.add(hunter);
            toRemove.add(id);
            return;
        }
        if (!hunter.isActive()) return;

        if (hunter.isDead() || hunter.getRemainingMinutes() <= 0) {
            hunter.stop();
            pendingUpdates.add(hunter);
            return;
        }
        hunter.executeRoutine();

        if (tickCount >= Config.AUTO_FARM_SAVE_INTERVAL) {
            hunter.removeMinutes(1);
            pendingUpdates.add(hunter);
        }
    }

    private void flushPendingUpdates() {
        final List<Hunter> batch = new ArrayList<>(pendingUpdates.size());

        while (!pendingUpdates.isEmpty()) {
            batch.add(pendingUpdates.poll());
        }

        if (batch.isEmpty()) return;

        hunterDAO.updateBatch(batch);
    }

    private void flushRemoving() {
        Integer id;
        while ((id = toRemove.poll()) != null) {
            remove(id);
        }
    }

    public static AutoFarmManager getInstance()
    {
        return AutoFarmManager.SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        protected static final AutoFarmManager INSTANCE = new AutoFarmManager();
    }
}
