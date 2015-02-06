package com.kampusku.machinelearning;

/**
 *
 * @author MAHAR
 */
public class ConnectGene extends Gene {
    protected double weight;

    public ConnectGene(double weight) {
        this.weight = weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getWeight() {
        return weight;
    }
}
