package com.kampusku.npc;

import com.kampusku.machinelearning.Genotype;
import com.kampusku.machinelearning.NeuralNetwork;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.io.File;

import cz.cuni.amis.introspection.java.JProp;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathExecutorState;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.TabooSet;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathAutoFixer;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.stuckdetector.UT2004DistanceStuckDetector;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.stuckdetector.UT2004PositionStuckDetector;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.stuckdetector.UT2004TimeStuckDetector;
import cz.cuni.amis.pogamut.ut2004.bot.command.Action;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.*;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.*;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.pogamut.ut2004.utils.UnrealUtils;
import cz.cuni.amis.utils.collections.MyCollections;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;
import java.util.Map;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

@AgentScoped
public class NpcTA extends UT2004BotModuleController<UT2004Bot> {
    
    protected static enum State {
        OTHER,
        UNSTUCK,
        GET_DROPPED_WEAPON, 
        GET_IMPORTANT_ITEM,
        GET_GOOD_WEAPON,
        BATTLE,
        CHASE,
        PATH
    }
    
    protected static enum VDirection {
        TOP, NORMAL, BOTTOM
    } 
    
    protected static enum HDirection {
        FRONT, FRONT_RIGHT, RIGHT, BACK_RIGHT, BACK, BACK_LEFT, LEFT, FRONT_LEFT
    }
    
    protected static enum MirroringState {
        JUMP, CROUCH, MOVEBY, SHOOT, WEAPON
    }

    protected static String botName = "I'm your TA";    
    
    private AutoTraceRay[][] directionRay = new AutoTraceRay[3][8];
    
    protected int notMoving = 0;
    protected int healthLevel = 90;
    protected State previousState = State.OTHER;
    protected Player enemy = null;
    protected TabooSet<Item> tabooItems = null;
    protected TabooSet<NavPoint> tabooNavPoints = null;
    protected List<Item> itemsToRunAround = null;    
    private UT2004PathAutoFixer autoFixer;
    protected Item item = null;
    protected boolean runningToPlayer = false;
    protected int chaseCount = 0;    
    protected boolean goToItemFromBattle = false;
    protected boolean shooting = false;
    
    protected double[] networkInput;
    protected Genotype genotype;
    
    protected Location lastPositionEnemy;
    
    protected List<MirroringState> shortTermMemory;
    protected int maxShortTermMemory = 5;
    protected boolean isMirroring = false;
    
    @EventListener(eventClass = Bumped.class)
    protected void bumped(Bumped event) {
        Location v = event.getLocation().sub(bot.getLocation()).scale(5);
        Location target = bot.getLocation().sub(v);

        // make the bot to go to the computed location while facing the bump source
        move.strafeTo(target, event.getLocation());
        
        // count fitness
        genotype.fitnessList.get(0).addTotalBumpPlayer(1);
        //print("[FITNESS] : totalBumpPlayer : "+genotype.fitnessList.get(0).getTotalBumpPlayer());
    }    
    
    @EventListener(eventClass = BotDamaged.class)
    protected void botDamaged(BotDamaged event) {
        // count fitness
        genotype.fitnessList.get(0).addTotalDamageReceived(event.getDamage());
        //print("[FITNESS] : totalDamageReceived : "+genotype.fitnessList.get(0).getTotalDamageReceived());
    }
    
    @EventListener(eventClass = PlayerDamaged.class)
    protected void playerDamaged(PlayerDamaged event) {
        // count fitness
        genotype.fitnessList.get(0).addTotalDamageGiven(event.getDamage());
        genotype.fitnessList.get(0).addAccurationTotalHit(1);
        //print("[FITNESS] : totalDamageGiven : "+genotype.fitnessList.get(0).getTotalDamageGiven());
    }    
    
    @EventListener(eventClass = WallCollision.class)
    protected void wallCollision(WallCollision event) {
        // count fitness
        genotype.fitnessList.get(0).addTotalBumpEnvironment(1);
        //print("[FITNESS] : totalBumpEnvironment : "+genotype.fitnessList.get(0).getTotalBumpEnvironment());        
    }
    
    @EventListener(eventClass = JumpPerformed.class)
    protected void jumpPerformed(JumpPerformed event) {
        print("[MIRROR] : Jumping");      
        shortTermMemory.add(MirroringState.CROUCH);
    }
    
    @Override
    public void prepareBot(UT2004Bot bot) {                
        tabooItems = new TabooSet<Item>(bot);       
        tabooNavPoints = new TabooSet<NavPoint>(bot);
        
        // add stuck detector
        pathExecutor.addStuckDetector(new UT2004TimeStuckDetector(bot, 3000, 10000)); // if the bot does not move for 3 seconds, considered that it is stuck
        pathExecutor.addStuckDetector(new UT2004PositionStuckDetector(bot)); // watch over the position history of the bot, if the bot does not move sufficiently enough, consider that it is stuck
        pathExecutor.addStuckDetector(new UT2004DistanceStuckDetector(bot)); // watch over distances to target

        autoFixer = new UT2004PathAutoFixer(bot, pathExecutor, fwMap, navBuilder); // auto-removes wrong navigation links between navpoints

        // listeners        
        pathExecutor.getState().addListener(new FlagListener<IPathExecutorState>() {

            @Override
            public void flagChanged(IPathExecutorState changedValue) {
                switch (changedValue.getState()) {
                    case PATH_COMPUTATION_FAILED:
                        break;
                    case STUCK:
                        if (item != null) {
                            tabooItems.add(item, 10);
                        }
                        resetStatus();
                        break;
                    case TARGET_REACHED:
                        resetStatus();
                        break;
                }
            }
        });        
        
        // DEFINE WEAPON PREFERENCES
        weaponPrefs.addGeneralPref(ItemType.LINK_GUN, false);
        weaponPrefs.addGeneralPref(ItemType.LIGHTNING_GUN, true);
        weaponPrefs.addGeneralPref(ItemType.SHOCK_RIFLE, true);
        weaponPrefs.addGeneralPref(ItemType.ROCKET_LAUNCHER, true);
        weaponPrefs.addGeneralPref(ItemType.LINK_GUN, true);
        weaponPrefs.addGeneralPref(ItemType.ASSAULT_RIFLE, true);
        weaponPrefs.addGeneralPref(ItemType.FLAK_CANNON, false);
        weaponPrefs.addGeneralPref(ItemType.FLAK_CANNON, true);
        weaponPrefs.addGeneralPref(ItemType.BIO_RIFLE, true);        
        
        // RANGED PREFERENCES
        weaponPrefs.newPrefsRange(80)
                .add(ItemType.SHIELD_GUN, true);

        weaponPrefs.newPrefsRange(1000)
                .add(ItemType.FLAK_CANNON, true)
                .add(ItemType.MINIGUN, true)
                .add(ItemType.LINK_GUN, false)
                .add(ItemType.BIO_RIFLE, true)
                .add(ItemType.ASSAULT_RIFLE, true);        

        weaponPrefs.newPrefsRange(4000)
                .add(ItemType.SHOCK_RIFLE, true)
                .add(ItemType.MINIGUN, false);

        weaponPrefs.newPrefsRange(100000)
                .add(ItemType.LIGHTNING_GUN, true)
                .add(ItemType.ROCKET_LAUNCHER, true)
                .add(ItemType.SNIPER_RIFLE, true);           
    }
    
    @Override
    public Initialize getInitializeCommand() {
        return new Initialize().setName(botName).setDesiredSkill(4);
    }

    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
        // initialize short term memory
        shortTermMemory = new LinkedList<MirroringState>();
        // initialize rays for raycasting
        final int rayLength = (int) (UnrealUtils.CHARACTER_COLLISION_RADIUS * 20);
        // settings for the rays
        boolean fastTrace = true;        // perform only fast trace == we just need true/false information
        boolean floorCorrection = false; // provide floor-angle correction for the ray (when the bot is running on the skewed floor, the ray gets rotated to match the skew)
        boolean traceActor = false;      // whether the ray should collid with other actors == bots/players as well

        // 1. remove all previous rays, each bot starts by default with three
        // rays, for educational purposes we will set them manually
        getAct().act(new RemoveRay("All"));

        // 2. create new rays
        for (int i = 0; i < 3; i++) {
            int z = 0; // z for VDirection.NORMAL = 0
            if (VDirection.values()[i] == VDirection.TOP) z = 1;
            else if (VDirection.values()[i] == VDirection.BOTTOM) z = -1;
            
            raycasting.createRay(VDirection.values()[i].name() + HDirection.FRONT.name(), new Vector3d(1, 0, z), rayLength, fastTrace, floorCorrection, traceActor);
            raycasting.createRay(VDirection.values()[i].name() + HDirection.FRONT_RIGHT.name(), new Vector3d(1, 1, z), rayLength, fastTrace, floorCorrection, traceActor);
            raycasting.createRay(VDirection.values()[i].name() + HDirection.RIGHT.name(), new Vector3d(0, 1, z), rayLength, fastTrace, floorCorrection, traceActor);
            raycasting.createRay(VDirection.values()[i].name() + HDirection.BACK_RIGHT.name(), new Vector3d(-1, 1, z), rayLength, fastTrace, floorCorrection, traceActor);
            raycasting.createRay(VDirection.values()[i].name() + HDirection.BACK.name(), new Vector3d(-1, 0, z), rayLength, fastTrace, floorCorrection, traceActor);
            raycasting.createRay(VDirection.values()[i].name() + HDirection.BACK_LEFT.name(), new Vector3d(-1, -1, z), rayLength, fastTrace, floorCorrection, traceActor);
            raycasting.createRay(VDirection.values()[i].name() + HDirection.LEFT.name(), new Vector3d(0, -1, z), rayLength, fastTrace, floorCorrection, traceActor);
            raycasting.createRay(VDirection.values()[i].name() + HDirection.FRONT_LEFT.name(), new Vector3d(1, -1, z), rayLength, fastTrace, floorCorrection, traceActor);                    
        }

        // register listener called when all rays are set up in the UT engine
        raycasting.getAllRaysInitialized().addListener(new FlagListener<Boolean>() {

            @Override
            public void flagChanged(Boolean changedValue) {
                // store the AutoTraceRay objects
                for (int i=0; i<3; i++)
                    for (int j = 0; j<8; j++)
                        directionRay[i][j] = raycasting.getRay(VDirection.values()[i].name() + HDirection.values()[j].name());
            }
        });

        // 3. we are not going to setup any other rays
        raycasting.endRayInitSequence();

        // trace-lines feature activated
        //getAct().act(new Configuration().setDrawTraceLines(true).setAutoTrace(true));
        getAct().act(new Configuration().setDrawTraceLines(false).setAutoTrace(true));
    }

    @Override
    public void botFirstSpawn(GameInfo gameInfo, ConfigChange config, InitedMessage init, Self self) {
        body.getCommunication().sendGlobalTextMessage("Hello world! I am alive!");
        act.act(new SendMessage().setGlobal(true).setText("And I can speak! Hurray!"));
    }
    
    @Override
    public void beforeFirstLogic() {
        networkInput = new double[NeuralNetwork.totalInput];
        for (int i = 0; i < NeuralNetwork.totalInput; i++) networkInput[i] = 0;
        
        genotype = NeuralNetwork.loadGenotype("_Genotype");
        
        //genotype = new Genotype();
        //NeuralNetwork.saveGenotype(genotype,"Genotype2");
    }
    
    @Override
    public void botKilled(BotKilled event) {
        body.getCommunication().sendGlobalTextMessage("I was KILLED!");
        itemsToRunAround = null;        
        enemy = null;    
    }

    @Override
    public void logic() throws PogamutException {
              
        // 1.UNSTUCK
        if (!info.isMoving()) {
            ++notMoving;
            if (notMoving > 5) {
                // reset the bot's mind
                resetStatus();
                return;
            }
        }
        // are you still shoot? stop shooting, you've lost your target
        if (info.isShooting() || info.isSecondaryShooting()) {
            getAct().act(new StopShooting());
        }
        // are you being shot? go to HIT (turn around - try to find your enemy)
        if (senses.isBeingDamaged()) {
            stateHit();
            return;
        }	
        // if bot has got the item from battle
        if (!navigation.isNavigating() && goToItemFromBattle) goToItemFromBattle = false;        
        
        // 2.GET DROPPED WEAPON
        getDroppedWeapon();
        
        // 3.BATTLE
        if (players.canSeeEnemies() && weaponry.hasLoadedWeapon() && !goToItemFromBattle) {
            battleController();
            return;
        }        
        
        // 4.IMPORTANT ITEM
        if (info.getHealth() < healthLevel && isMedkitExist() && !goToItemFromBattle) {
            getImportantItem();
            return;
        }
        
        // 5.GET GOOD WEAPON
        if (!items.getVisibleItems().isEmpty() && !goToItemFromBattle) {
            item = getNearestVisibleItem();
            if (item != null && fwMap.getDistance(info.getNearestNavPoint(), item.getNavPoint()) < 500) {
                stateSeeItem();
                previousState = State.GET_GOOD_WEAPON;
                return;
            }
        }
        
        // 6.CHASE
        if (enemy != null && weaponry.hasLoadedWeapon() && !goToItemFromBattle) {  // !enemy.isVisible() because of 2)
            chaseController();
            return;
        }
        
        // 7.PATH
        if (!goToItemFromBattle) {
            runAround();
        }
        
        countFitness();
    }
    
    /* UNSTUCK */
    protected void resetStatus() {
        print("try to unstuck");
        
        notMoving = 0;
        enemy = null;     
        item = null;
        itemsToRunAround = null;
        
        isMirroring = false;
        shortTermMemory.clear();
        
        //getAct().act(new Move().setFocusTarget(UnrealId.NONE));
        goToItemFromBattle = false;
        
        stateHit();        
        previousState = State.UNSTUCK;
    }
    
    /* GET IMPORTANT ITEM */
    protected void getImportantItem() {
        print("try to get important item");

        if (previousState != State.GET_IMPORTANT_ITEM) {
            List<Item> healths = new LinkedList();
            healths.addAll(items.getSpawnedItems(ItemType.HEALTH_PACK).values());
            if (healths.isEmpty()) {
                healths.addAll(items.getSpawnedItems(ItemType.MINI_HEALTH_PACK).values());
            }
            Set<Item> okHealths = tabooItems.filter(healths);
            if (!okHealths.isEmpty()) {
                item = fwMap.getNearestItem(okHealths, info.getNearestNavPoint());
                navigation.navigate(item);
            }
        }   
        previousState = State.GET_IMPORTANT_ITEM;
    }
    
    /* GET GOOD WEAPON */
    protected void stateSeeItem() {
        print("try to get good weapon");
        
        if (item != null && item.getLocation().getDistance(info.getLocation()) < 80) {
            resetStatus();
        }

        if (item != null && previousState != State.GET_GOOD_WEAPON) {
            if (item.getLocation().getDistance(info.getLocation()) < 300) {
            	getAct().act(new Move().setFirstLocation(item.getLocation()));
            } else {
            	navigation.navigate(item);
            }             
        }
    }    
    
    /* BATTLE */
    protected void battleController() {
        print("Battle State");
        
        shooting = false;
        double distance = Double.MAX_VALUE;
        
        // 1) pick new enemy if the old one has been lost
        if (previousState != State.BATTLE || enemy == null || !enemy.isVisible()) {
            // pick new enemy
            enemy = players.getNearestVisiblePlayer(players.getVisibleEnemies().values());
            lastPositionEnemy = enemy.getLocation();
            
            if (enemy == null) {
                log.info("Can't see any enemies... ???");
                return;
            }
            if (info.isShooting()) {
                // stop shooting
                getAct().act(new StopShooting());
            }
            runningToPlayer = false;
        }

        if (enemy != null) {        
            if (!isMirroring) mirroring();
            
            // 2) if not shooting at enemyID - start shooting
            distance = info.getLocation().getDistance(enemy.getLocation());
            
            // focus to enemy
            getAct().act(new Move().setFocusTarget(players.getNearestVisibleEnemy().getId()));
            
            // do mirroring
            if (shortTermMemory.size() >= maxShortTermMemory || isMirroring) {
                print("[doing Mirroring]");
                
                isMirroring = true;
                switch (shortTermMemory.get(0)) {
                    case CROUCH : print("crouch"); break;
                    case JUMP : print ("jump"); break;
                    case MOVEBY : print ("move by"); break;
                    case SHOOT : print ("shoot"); break;
                    case WEAPON : print ("weapon"); break;
                }
                shortTermMemory.remove(0);
                
                if (shortTermMemory.isEmpty()) isMirroring = false;
            }
            
            if (!goToItemFromBattle) {
                //int randNum = (int)(Math.random() * 6);
                //switch(randNum) {

                setNetworkInput();
                genotype.calculateGenotype(NeuralNetwork.createLinkedList(networkInput));
                
                switch(genotype.getOutputResult()) {
                case 0:
                    print("STAND STILL");
                    shootAtEnemy(enemy);
                    break;
                case 1:
                    print("ADVANCE");                
                    //if (info.isShooting()) { getAct().act(new StopShooting());}                
                    shootAtEnemy(enemy);
                    
                    getAct().act(new Move().setFirstLocation(enemy.getLocation()));
                    //if ((int)(Math.random() * 10) >= 6) move.jump();
                    break;
                case 2:
                    print("RETREAT");
                    shootAtEnemy(enemy);
                    
                    move.dodgeBack(info.getLocation(), enemy.getLocation());
                    break;
                case 3:
                    print("STRAFE LEFT");
                    //if (info.isShooting()) { getAct().act(new StopShooting());}                
                    shootAtEnemy(enemy);
                    
                    NavPoint nav = DistanceUtils.getSecondNearest(getWorldView().getAll(NavPoint.class).values(), info.getLocation());
                    if (nav != null) 
                        move.moveTo(nav);            
                    else
                        move.strafeLeft(50, players.getRandomVisibleEnemy().getLocation());
                    break;
                case 4:
                    print("STRAFE RIGHT");
                    //if (info.isShooting()) { getAct().act(new StopShooting());}                
                    shootAtEnemy(enemy);
                    
                    NavPoint _nav = DistanceUtils.getSecondNearest(getWorldView().getAll(NavPoint.class).values(), info.getLocation());
                    if (_nav != null) 
                        move.moveTo(_nav);            
                    else
                        move.strafeRight(50, players.getRandomVisibleEnemy().getLocation());
                    break;
                case 5:
                    if (info.getHealth() < 20) {
                        print("GO TO ITEM");

                        List<Item> healths = new LinkedList();
                        healths.addAll(items.getSpawnedItems(ItemType.HEALTH_PACK).values());
                        if (healths.isEmpty()) {
                            healths.addAll(items.getSpawnedItems(ItemType.MINI_HEALTH_PACK).values());
                        }
                        Set<Item> okHealths = tabooItems.filter(healths);
                        if (!okHealths.isEmpty()) {
                            //item = fwMap.getNearestItem(okHealths, info.getNearestNavPoint());
                            item = fwMap.getSecondNearestItem(okHealths, info.getNearestNavPoint());
                            navigation.navigate(item);
                        }                

                        //if (info.isShooting()) { getAct().act(new StopShooting());}                

                        goToItemFromBattle = true;                    
                    }
                    else {
                        shootAtEnemy(enemy);
                    }
                    break;
                }            
            }
        } 
        
        if (!goToItemFromBattle) {    
            // 4) if enemy is far - run to him
            int decentDistance = Math.round(random.nextFloat() * 800) + 200;
            if (!enemy.isVisible() || !shooting || decentDistance < distance) {
                if (!runningToPlayer) {
                    navigation.navigate(enemy);
                    runningToPlayer = true;
                }
            } else {
                runningToPlayer = false;
                navigation.stopNavigation();
                getAct().act(new Stop());
            }            
        }

        previousState = State.BATTLE;
    }
    
    protected void stateHit() {
        print("try respond from hit");
        
        Player nearestEnemy = players.getNearestVisibleEnemy();
        
        NavPoint nav = DistanceUtils.getSecondNearest(getWorldView().getAll(NavPoint.class).values(), info.getLocation());
        if (nav == null || nearestEnemy == null) 
            getAct().act(new Rotate().setAmount(32000));
        else {        
            if (nearestEnemy != null) {
                shootAtEnemy(nearestEnemy);
                move.dodgeBack(info.getLocation(), nearestEnemy.getLocation());            
            } else {
                move.moveTo(nav);
            }
        }
                
        previousState = State.OTHER;
    }    
    
    /* CHASE */
    protected void chaseController() {
        print("try to chase");

        if (previousState != State.CHASE) {
            chaseCount = 0;
            navigation.navigate(enemy);
        }
        ++chaseCount;
        if (chaseCount > 30) {
            resetStatus();
        } else {
            previousState = State.CHASE;
        }
    }
    
    /* PATH */
    protected void runAround() {
        print("try to run around map");
        
        if (previousState != State.PATH) {
            itemsToRunAround = new LinkedList<Item>(items.getSpawnedItems().values());
            Set<Item> _items = tabooItems.filter(itemsToRunAround);
            if (_items.isEmpty()) {
                print("No item to run for...");
                
                //resetStatus();

                if (players.canSeePlayers() || navigation.getCurrentTargetPlayer() != null) {
                    handlePlayerNavigation();
                } else {
                    handleNavPointNavigation();
                }                
                return;
            }
            item = _items.iterator().next();
            navigation.navigate(item);
        }
        previousState = State.PATH;
    }
    
    //// SUPPORT FUNCTION ////
    protected void countFitness() {
        // kerusakan yang diterima (v)
        // kerusakan pada musuh (v)
        // akurasi (v)
            //print("[FITNESS] accuration : "+genotype.fitnessList.get(0).getAccuration().countAccuration()+" %");
        // benturan dengan lingkungan (v)
        // benturan dengan musuh (v)
    }
    
    protected void mirroring() {
        if (enemy != null) {
            // movement
            Location currentEnemyPos = enemy.getLocation();
            currentEnemyPos.asVector3d().sub(lastPositionEnemy.asVector3d());
            Vector3d deltaPos = new Vector3d(currentEnemyPos.asVector3d());
            lastPositionEnemy = currentEnemyPos;
            print("[MIRROR] : MoveBy("+deltaPos.x+", "+deltaPos.y+", "+deltaPos.z+")");
            shortTermMemory.add(MirroringState.MOVEBY);
            
            // shooting
            switch(enemy.getFiring()) {
            case 1 : 
                print("[MIRROR] : Firing Primary Mode"); 
                shortTermMemory.add(MirroringState.SHOOT);
                break;
            case 2 : 
                print("[MIRROR] : Firing Secondary Mode"); 
                shortTermMemory.add(MirroringState.SHOOT);
                break;
            }

            // weapon choice
            print("[MIRROR] : Enemy weapon "+enemy.getWeapon());
            shortTermMemory.add(MirroringState.WEAPON);

            // jumping
            // look at jumpPerformed
            
            // crouching
            if (enemy.isCrouched()) {
                print("[MIRROR] : Crouching");
                shortTermMemory.add(MirroringState.CROUCH);
            }        
        }
    }
    
    protected void setNetworkInput() {
        // init default input
        for (int i = 0; i < NeuralNetwork.totalInput; i++) networkInput[i] = -1;
        
        //Check for network input 
        //1. Ten Pie Slice Enemy Sensors

        //2. Twenty-four Ray-Tracing Level Geometry Sensors        
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 8; j++) {
                if (directionRay[i][j].isResult()) networkInput[(i*8)+j] = 1;
                else networkInput[(i*8)+j] = 0;
            }
        //3. One Damage Sensor
        if (senses.isBeingDamaged()) networkInput[24] = 1;
        else networkInput[24] = 0;
        //4. One Movement Sensor
        if (info.isMoving()) networkInput[25] = 1;
        else networkInput[25] = 0;
        //5. One Shooting Sensor
        if (info.isShooting()) networkInput[26] = 1;
        else networkInput[26] = 0;
        //6. One Enemy Shooting Sensor
        if (enemy.getFiring() != 0) networkInput[27] = 1;
        else networkInput[27] = 0;
        //7. Eight Current Weapon Sensor
        ItemType currentWeaponType = info.getCurrentWeaponType();
        if (currentWeaponType == ItemType.FLAK_CANNON) networkInput[28] = 1; else networkInput[28] = 0;
        if (currentWeaponType == ItemType.MINIGUN) networkInput[29] = 1; else networkInput[29] = 0;
        if (currentWeaponType == ItemType.ROCKET_LAUNCHER) networkInput[30] = 1; else networkInput[30] = 0;
        if (currentWeaponType == ItemType.ASSAULT_RIFLE) networkInput[31] = 1; else networkInput[31] = 0;
        if (currentWeaponType == ItemType.SHOCK_RIFLE) networkInput[32] = 1; else networkInput[32] = 0;
        if (currentWeaponType == ItemType.BIO_RIFLE) networkInput[33] = 1; else networkInput[33] = 0;
        if (currentWeaponType == ItemType.LIGHTNING_GUN) networkInput[34] = 1; else networkInput[34] = 0;
        if (currentWeaponType == ItemType.SNIPER_RIFLE) networkInput[35] = 1; else networkInput[35] = 0;
        //8. Six Nearest Item Sensors
        Item nearestItem = info.getNearestItem();
        if (nearestItem.isVisible()) networkInput[36] = 1; else networkInput[36] = 0;
        if (nearestItem.getType().getCategory() == ItemType.Category.HEALTH) networkInput[37] = 1; else networkInput[37] = 0;
        if (nearestItem.getType().getCategory() == ItemType.Category.ARMOR) networkInput[38] = 1; else networkInput[38] = 0;
        if (nearestItem.getType().getCategory() == ItemType.Category.SHIELD) networkInput[39] = 1; else networkInput[39] = 0;
        if (nearestItem.getType().getCategory() == ItemType.Category.WEAPON) networkInput[40] = 1; else networkInput[40] = 0;
        if (nearestItem.getType().getCategory() == ItemType.Category.ADRENALINE) networkInput[41] = 1; else networkInput[41] = 0;
        //9. Four Nearest Health Item Sensors
        List<Item> _healths = new LinkedList();
        _healths.addAll(items.getSpawnedItems(ItemType.HEALTH_PACK).values());
        if (_healths.isEmpty()) _healths.addAll(items.getSpawnedItems(ItemType.MINI_HEALTH_PACK).values());
        Set<Item> _okHealths = tabooItems.filter(_healths);
        if (!_okHealths.isEmpty()) item = fwMap.getNearestItem(_okHealths, info.getNearestNavPoint());
        
        if (item != null && info != null) {
            double deltaX = item.getLocation().getX() - info.getLocation().getX();
            double deltaY = item.getLocation().getY() - info.getLocation().getY();
            double deltaZ = item.getLocation().getZ() - info.getLocation().getZ();
            double delta = Math.sqrt(Math.pow(deltaX,2)+Math.pow(deltaY,2)+Math.pow(deltaZ,2));

            networkInput[42] = deltaX;
            networkInput[43] = deltaY;
            networkInput[44] = deltaZ;
            networkInput[45] = delta;        
        }
    }    
    
    protected void shootAtEnemy(Player target) {
        if (shoot.shoot(weaponPrefs, target) != null) {
            shooting = true;
            
            // count fitness
            genotype.fitnessList.get(0).addAccurationTotalShoot(1);
        } 
    }
    
    protected void getDroppedWeapon() {
        
    }
    
    protected void print(String text) {
        body.getCommunication().sendGlobalTextMessage(text);
        log.info(text);
    }
    
    protected boolean isMedkitExist() {
        return (!items.getSpawnedItems(ItemType.HEALTH_PACK).isEmpty() || !items.getSpawnedItems(ItemType.MINI_HEALTH_PACK).isEmpty());
    }    
    
    protected Item getNearestVisibleItem() {
    	final NavPoint nearestNavPoint = info.getNearestNavPoint();
    	List<Item> itemsDistanceSortedAscending = 
            DistanceUtils.getDistanceSorted(
                items.getVisibleItems().values(), 
                info.getLocation(), 
                new DistanceUtils.IGetDistance<Item>() {
                    @Override
                    public double getDistance(Item object, ILocated target) {
                        return fwMap.getDistance(nearestNavPoint, object.getNavPoint());
                    }
                }
            );
    	if (itemsDistanceSortedAscending.isEmpty()) return null;
    	return itemsDistanceSortedAscending.get(0);
    }    

    private void handlePlayerNavigation() {
        if (navigation.isNavigating() && navigation.getCurrentTargetPlayer() != null)
            return;        

        //navigation stop
        //choose another player to navigate
        Player player = players.getNearestVisiblePlayer();
        if (player == null) {
            // no player at sight, navigate to random navpoint
            handleNavPointNavigation();
            return;
        }

        navigation.navigate(player);
    }    
    
    protected void handleNavPointNavigation() {
        if (navigation.isNavigating()) {
            // jika target sudah dekat dan tujuan nav tidak ada
            while (navigation.getContinueTo() == null && navigation.getRemainingDistance() < 400) {
                navigation.setContinueTo(getRandomNavPoint());
            }
            return;
        }
        
        //navigation stop
        //choose another nav point to navigate
        NavPoint targetNavPoint = getRandomNavPoint();
        if (targetNavPoint == null) {
            resetStatus();
            return;
        }

        navigation.navigate(targetNavPoint);
    }
    
    protected NavPoint getRandomNavPoint() {
        // choose one feasible navpoint (not belonging to tabooNavPoints) randomly
        NavPoint chosen = MyCollections.getRandomFiltered(getWorldView().getAll(NavPoint.class).values(), tabooNavPoints);

        if (chosen != null)
            return chosen;

        // all navpoints have been visited, pick a new one at random
        return MyCollections.getRandom(getWorldView().getAll(NavPoint.class).values());
    }    
    
    //// MAIN FUNCTION ////
    public static void main(String args[]) throws PogamutException {
        new UT2004BotRunner(NpcTA.class, botName).setMain(true).startAgent();
    }
}
