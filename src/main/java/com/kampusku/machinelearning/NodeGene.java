package com.kampusku.machinelearning;

import java.util.LinkedList;

/**
 *
 * @author MAHAR
 */
public class NodeGene extends Gene {
    protected NodeType nodeType;
    protected int layer;
    public double value;
    
    public LinkedList<ConnectGene> connectGeneList;

    public NodeGene(int layer, NodeType nodeType) {
        this.layer = layer;
        this.nodeType = nodeType;
        connectGeneList = new LinkedList<ConnectGene>();
        value = 0;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }
    
    public int getLayer() {
        return layer;
    }
    
    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public Gene.NodeType getNodeType() {
        return nodeType;
    }    
}
