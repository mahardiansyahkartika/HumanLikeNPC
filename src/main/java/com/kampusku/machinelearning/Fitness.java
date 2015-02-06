package com.kampusku.machinelearning;

import java.io.Serializable;

/**
 *
 * @author MAHAR
 */
public class Fitness implements Serializable {
    
    public class Accuration implements Serializable {
        public int totalShoot;
        public int totalHit;
        
        public Accuration() {
            totalShoot = 0;
            totalHit = 0;
        }
        
        public double countAccuration() {
            if (totalShoot == 0) return 0;
            return ((double)totalHit/(double)totalShoot);
        }
    }    
    
    protected int totalDamageReceived;
    protected int totalDamageGiven;
    protected int totalBumpPlayer;
    protected int totalBumpEnvironment;
    protected Accuration accuration;
    
    public Fitness() {
        // initialize fitness point
        totalDamageReceived = 0;
        totalDamageGiven = 0;
        totalBumpPlayer = 0;
        totalBumpEnvironment = 0;    
        accuration = new Accuration();
    }
    
    public int getTotalDamageReceived() {
        return totalDamageReceived;
    }
    
    public int getTotalDamageGiven() {
        return totalDamageGiven;
    }
    
    public int getTotalBumpPlayer() {
        return totalBumpPlayer;
    }
    
    public int getTotalBumpEnvironment() {
        return totalBumpEnvironment;
    }
    
    public Accuration getAccuration() {
        return accuration;
    }
    
    public void addTotalDamageReceived(int i) {
        this.totalDamageReceived += i;
    } 
    
    public void addTotalDamageGiven(int i) {
        this.totalDamageGiven += i;
    }
    
    public void addTotalBumpPlayer(int i) {
        this.totalBumpPlayer += i;
    }
    
    public void addTotalBumpEnvironment(int i) {
        this.totalBumpEnvironment += i;
    }
    
    public void addAccurationTotalHit(int i) {
        this.accuration.totalHit += i;
    }
    
    public void addAccurationTotalShoot(int i) {
        this.accuration.totalShoot += i;
    }
    
    public double getSurvivalValue() {
        return accuration.countAccuration() + ((double)totalDamageGiven/(double)totalDamageReceived);
    }
}
