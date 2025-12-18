All files (Java and HTML) are available in this repository.

Insert this line of code into your GameServer.java
```java

StringUtil.printSection("Auto Farm");
AutoFarmManager.getInstance();
LOGGER.info("Auto Farm is Started.");
```

Register the handler in the BypassHandler.java
```java
registerHandler(new AutoFarmHandler());
```

Register the Auto Farm voice command in the VoicedCommandHandler.java
```java
registerHandler(new AutoFarm());
```


Insert the constants and initialize them in some method.
```java
	public static boolean AUTOFARM_ENABLED;
	public static int AUTO_FARM_BATCH_SIZE;
	public static int AUTO_FARM_SAVE_INTERVAL;
	public static int AUTO_FARM_MIN_RADIUS_DISABLE;
	public static int AUTO_FARM_TOLERABLE_DELAY_HIT;
	public static int AUTO_FARM_TOLERABLE_SAME_TARGET;
	public static int AUTO_FARM_ITEM_ID_2_HOURS;
	public static int AUTO_FARM_ITEM_ID_4_HOURS;
	public static int AUTO_FARM_ITEM_ID_10_HOURS;

  		// Auto Farm
		AUTOFARM_ENABLED = develop.getProperty("AutoFarmEnabled", true);
		AUTO_FARM_BATCH_SIZE = develop.getProperty("BatchSize", 200);
		AUTO_FARM_SAVE_INTERVAL = develop.getProperty("SaveInterval", 60);
		AUTO_FARM_MIN_RADIUS_DISABLE = develop.getProperty("MinRadiusDisableMovement", 40);
		AUTO_FARM_TOLERABLE_DELAY_HIT = develop.getProperty("TolerableDelayHitTime", 15000);
		AUTO_FARM_TOLERABLE_SAME_TARGET = develop.getProperty("TolerableSameTarget", 30000);
		AUTO_FARM_ITEM_ID_2_HOURS = develop.getProperty("ItemId2h", 3470);
		AUTO_FARM_ITEM_ID_4_HOURS = develop.getProperty("ItemId4h", 3470);
		AUTO_FARM_ITEM_ID_10_HOURS = develop.getProperty("ItemId10h", 3470);
```

Insert this information in your config.yml file.
```yml
#=============================================================
#                            Auto Farm
#=============================================================

# If true auto farm will be enabled
# Default: true
AutoFarmEnabled = True

# Define which items will be IDs for the auto farm.
# Default: 3470 = GOLD BAR
# 2 hours
ItemId2h = 3470
# 4 hours
ItemId4h = 3470
# 10 hours
ItemId10h = 3470

# Maximum size of a batch block executed in a database.
# Default: 200
BatchSize = 200
# The period during which information is saved in the database.
# Default: 60
SaveInterval = 60

# Minimum radius required to disable movement and attack monsters.
# Default: 40
MinRadiusDisableMovement = 40

# Maximum tolerable time between the last attack on monsters.
# Default: 15000 (15 seconds)
TolerableDelayHitTime = 15000
# Maximum time to stay on the same target.
# Default: 30000 (30 seconds)
TolerableSameTarget = 30000
```
