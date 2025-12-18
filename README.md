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

Register the autofarm voice command in the VoicedCommandHandler.java
```java
registerHandler(new AutoFarm());
```
