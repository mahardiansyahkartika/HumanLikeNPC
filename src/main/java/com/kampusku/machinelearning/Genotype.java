package com.kampusku.machinelearning;

import java.io.Serializable;
import java.util.LinkedList;

/**
 *
 * @author MAHAR
 */
public class Genotype implements Serializable {
    public LinkedList<LinkedList<NodeGene>> nodeGeneList;
    public LinkedList<Fitness> fitnessList;
    
    public Genotype() {
        // init network input and output
        NeuralNetwork.initGeneLibrary();
                
        nodeGeneList = new LinkedList<LinkedList<NodeGene>>();
        fitnessList = new LinkedList<Fitness>();
        
        // add first fitness
        fitnessList.add(new Fitness());
        
        // add input layer
        LinkedList<NodeGene> inputNodeList = new LinkedList<NodeGene>();
        for (NodeGene ng:NeuralNetwork.inputNodeLibrary) inputNodeList.add(ng);
        nodeGeneList.add(inputNodeList);

        // add output layer
        LinkedList<NodeGene> outputNodeList = new LinkedList<NodeGene>();
        for (NodeGene ng:NeuralNetwork.outputNodeLibrary) outputNodeList.add(ng);
        nodeGeneList.add(outputNodeList);
        
        // make connector
        for (int i = 0; i < nodeGeneList.getLast().size(); i++)
            for (int j = 0; j < nodeGeneList.getFirst().size(); j++)
                nodeGeneList.getLast().get(i).connectGeneList.add(new ConnectGene(getWeigth()));
    }
    
    public void mutationAddNode(int layer) {
        int count = layer - nodeGeneList.size();
        
        if (count == -1) { // make new layer            
            LinkedList<NodeGene> hiddenNodeList = new LinkedList<NodeGene>();
            hiddenNodeList.add(new NodeGene(layer, Gene.NodeType.HIDDEN));
            
            nodeGeneList.add(layer, hiddenNodeList);
            
            // SETTING CONNECTOR
            // set left connector
            for (int i = 0; i < nodeGeneList.get(layer-1).size(); i++)
                nodeGeneList.get(layer).getLast().connectGeneList.add(new ConnectGene(getWeigth()));
            // set right connector
            for (int i = 0; i < nodeGeneList.get(layer+1).size(); i++) {
                nodeGeneList.get(layer+1).get(i).connectGeneList.clear();            
                for (int j = 0; j < nodeGeneList.get(layer).size(); j++)
                    nodeGeneList.get(layer+1).get(i).connectGeneList.add(new ConnectGene(getWeigth()));
            }
        } else if (count < -1) { // add to existing hiddenNodeList
            nodeGeneList.get(layer).add(new NodeGene(layer, Gene.NodeType.HIDDEN));
            
            // SETTING CONNECTOR
            // set left connector
            for (int i = 0; i < nodeGeneList.get(layer-1).size(); i++)
                nodeGeneList.get(layer).getLast().connectGeneList.add(new ConnectGene(getWeigth()));
            // set right connector
            for (int i = 0; i < nodeGeneList.get(layer+1).size(); i++) {
                nodeGeneList.get(layer+1).get(i).connectGeneList.add(new ConnectGene(getWeigth()));            
            }            
        } else {
            System.out.println("layer "+layer+" FAILED to add. Possible max layer : "+(nodeGeneList.size()-1));
        }
    }
    
    public void mutationRemoveNode(int layer, int index) {
        if (layer == 0 || layer == nodeGeneList.size()-1) {
            System.out.println("layer "+layer+" try to access INPUT or OUTPUT layer");
        } else {
            nodeGeneList.get(layer).remove(index);
            if (nodeGeneList.get(layer).size() == 0) { // remove layer
                nodeGeneList.remove(layer);
                
                // set right connector
                for (int i = 0; i < nodeGeneList.get(layer).size(); i++) {
                    nodeGeneList.get(layer).get(i).connectGeneList.clear();            
                    for (int j = 0; j < nodeGeneList.get(layer-1).size(); j++)
                        nodeGeneList.get(layer).get(i).connectGeneList.add(new ConnectGene(getWeigth()));
                }
            } else { // remove node at layer
                // set right connector
                for (int i = 0; i < nodeGeneList.get(layer+1).size(); i++) {
                    nodeGeneList.get(layer+1).get(i).connectGeneList.remove(index);        
                }            
            }
        }
    }
    
    public void mutationWeight(int totalChangedNode) {
        for (int i = 0; i < totalChangedNode; i++) {
            int layer = random(1,nodeGeneList.size()-1);
            int indexNode = random(0,nodeGeneList.get(layer).size()-1);
            int indexConnector = random(0,nodeGeneList.get(layer).get(indexNode).connectGeneList.size()-1);

            nodeGeneList.get(layer).get(indexNode).connectGeneList.get(indexConnector).weight = getWeigth();
        }        
    }
    
    public void calculateGenotype(LinkedList<Double> inputList) {
        if (inputList.size() != NeuralNetwork.totalInput) {
            System.out.println("use the CORRECT input ("+inputList.size()+"/"+NeuralNetwork.totalInput+")");
        } else {
            // set input
            for (int i = 0; i < inputList.size(); i++)
                nodeGeneList.get(0).get(i).value = inputList.get(i);
            
            // calculate all
            for (int i = 1; i < nodeGeneList.size(); i++) {
                for (int j = 0; j < nodeGeneList.get(i).size(); j++) {
                    NodeGene nodeGene = nodeGeneList.get(i).get(j);
                    
                    // summing function
                    double sum = 0;
                    int totalConnector = nodeGene.connectGeneList.size();
                    
                    // print warning if totalConnector different with total prevLayer
                    if (totalConnector != nodeGeneList.get(i-1).size()) System.out.println("WARNING : totalConnector = "+totalConnector+", prevNodeLayer = "+nodeGeneList.get(i-1).size());
                    
                    for (int k = 0; k < totalConnector; k++) {
                        sum += (nodeGene.connectGeneList.get(k).weight * nodeGeneList.get(i-1).get(k).value);
                    }
                    
                    // activation function
                    nodeGene.value = Math.tanh(sum);
                }
            }
            
            // print result
            /*
            for (int i = 0; i < nodeGeneList.get(nodeGeneList.size()-1).size(); i++) {
                System.out.println("index : "+(i+1)+", value = "+nodeGeneList.get(nodeGeneList.size()-1).get(i).value);
            }
            System.out.println("");
            */
        }
    }
    
    public int getOutputResult() {
        int indexResult = 0;
        double highValue = nodeGeneList.get(nodeGeneList.size()-1).get(indexResult).value;
    
        for (int i = 1; i < NeuralNetwork.totalOutput; i++) {
            if (nodeGeneList.get(nodeGeneList.size()-1).get(i).value > highValue) {
                highValue = nodeGeneList.get(nodeGeneList.size()-1).get(i).value;
                indexResult = i;
            } 
        }
        
        return indexResult;
    }
    
    public int getTotalHiddenNode() {
        int result = 0;
        
        for (int i = 1; i < nodeGeneList.size()-1; i++)
            result += nodeGeneList.get(i).size();
        
        return result;
    }
    
    public int getTotalConnector() {
        int result = 0;
        
        for (int i = 1; i < nodeGeneList.size(); i++) {
            for (int j = 0; j < nodeGeneList.get(i).size(); j++)
                result += nodeGeneList.get(i).get(j).connectGeneList.size();
        }
        
        return result;
    }
    
    public void printNeuralNetwork() {
        for (int i = 0; i < nodeGeneList.size(); i++) {
            for (int j = 0; j < nodeGeneList.get(i).size(); j++) {
                if (i == 0) System.out.print("O ");
                else System.out.print("O("+nodeGeneList.get(i).get(j).connectGeneList.size()+") ");
            }
            System.out.println("= "+nodeGeneList.get(i).size());
        }
    }
    
    public static int random(int minValue, int maxValue) {
        return minValue+((int)(Math.random()*((maxValue-minValue)+1)));    
    }
    
    public static double getWeigth() {
        return NeuralNetwork.minWeightValue+(Math.random()*(NeuralNetwork.maxWeightValue-NeuralNetwork.minWeightValue));        
    }
}
