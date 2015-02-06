package com.kampusku.machinelearning;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;

/**
 *
 * @author MAHAR
 */
public class NeuralNetwork {
    public static LinkedList<NodeGene> inputNodeLibrary;
    public static LinkedList<NodeGene> outputNodeLibrary;

    public static double minWeightValue = -1;
    public static double maxWeightValue = 1;
    
    public static int totalInput = 46;
    public static int totalOutput = 5;
    
    public NeuralNetwork() {
        
    }
    
    public static void initGeneLibrary() {
        inputNodeLibrary = new LinkedList<NodeGene>();
        outputNodeLibrary = new LinkedList<NodeGene>();
        
        for (int i = 0; i < totalInput; i++) {
            inputNodeLibrary.add(new NodeGene(0, Gene.NodeType.INPUT));
        }

        for (int i = 0; i < totalOutput; i++) {
            outputNodeLibrary.add(new NodeGene(-1, Gene.NodeType.OUTPUT));
        }
    }
    
    public static LinkedList<Double> createLinkedList(double[] list) {
        LinkedList<Double> resultList = new LinkedList<Double>();
        for (int i = 0; i < list.length; i++) resultList.add(list[i]);        
        return resultList;
    }
    
    public static void saveGenotype(Genotype genotype, String name) {
        try{  // Catch errors in I/O if necessary.
            // Open a file to write to, named SavedObj.sav.
            FileOutputStream saveFile=new FileOutputStream("save\\"+name+".sav");

            // Create an ObjectOutputStream to put objects into save file.
            ObjectOutputStream save = new ObjectOutputStream(saveFile);

            // Now we do the save.
            save.writeObject(genotype);

            // Close the file.
            save.close(); // This also closes saveFile.
            System.out.println("Save "+name+" Success");
        }
        catch(Exception exc){
            exc.printStackTrace(); // If there was an error, print the info.
        }
    }
    
    public static Genotype loadGenotype(String name) {
        Genotype genotypeResult = new Genotype();
        
        // Wrap all in a try/catch block to trap I/O errors.
        try{
            // Open file to read from, named SavedObj.sav.
            FileInputStream saveFile = new FileInputStream("save\\"+name+".sav");

            // Create an ObjectInputStream to get objects from save file.
            ObjectInputStream save = new ObjectInputStream(saveFile);

            // Now we do the restore.
            // readObject() returns a generic Object, we cast those back
            // into their original class type.
            // For primitive types, use the corresponding reference class.
            genotypeResult = (Genotype) save.readObject();

            // Close the file.
            save.close(); // This also closes saveFile.
            System.out.println("Load "+name+" Success");
        }
        catch(Exception exc){
            exc.printStackTrace(); // If there was an error, print the info.
        }    
        
        return genotypeResult;
    }
    
    public static void main(String args[]) {
        NeuralNetwork.initGeneLibrary();
        
        Genotype genotype = new Genotype();
        
        // mutasi
        /*
        for (int i = 0; i < 1000; i++) {
            int x = Genotype.random(0,9);
            
            if (x < 7) {
                genotype.mutationAddNode(Genotype.random(1,genotype.nodeGeneList.size()-1));            
            } else if (x < 8) {
                if (genotype.nodeGeneList.size() > 2) {
                    int layer = Genotype.random(1,genotype.nodeGeneList.size()-2);
                    genotype.mutationRemoveNode(layer,Genotype.random(0,genotype.nodeGeneList.get(layer).size()-1));                
                }
            } else {
                genotype.mutationWeight(Genotype.random(0, genotype.getTotalConnector()));
            }
        }
        
        genotype.printNeuralNetwork();
        NeuralNetwork.saveGenotype(genotype,"_Genotype");
        */
        
        double[] inputList = {0,-1,1,-1,0,0,-1,1,1,0,-1,0,0,1,0,0,1,1,1,0,-1,-1,-1,0,-1,0,0,-1,1,1,1,-1,0,0,1,0,1,1,1,1,0,1,-1,-1,1,0};
        genotype.calculateGenotype(createLinkedList(inputList));
        System.out.println("output : "+genotype.getOutputResult());
    }
}
