/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.learnpad.or.impl;

import eu.learnpad.exception.LpRestException;
import eu.learnpad.ontology.execution.ExecutionStates;
import eu.learnpad.ontology.execution.domain.States;
import eu.learnpad.or.BridgeInterface;
import java.io.ByteArrayOutputStream;
import javax.inject.Inject;
import javax.xml.bind.JAXB;

/**
 *
 * @author sandro.emmenegger
 */
public class BridgeInterfaceImpl implements BridgeInterface{
    
    @Inject
    ExecutionStates executionStates;

    @Override
    public void putModelSet(String modelSetId, String type) throws LpRestException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void sendResourceNotification(String modelSetId, String resourceId, String artifactIds, String action) throws LpRestException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] askRecommendation(String modelSetId, String artifactId, String userId, String type) throws LpRestException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] simulationNotification(String modelSetId, String modelId, String action, String simulationId) throws LpRestException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addExecutionState(String artifactName, String artifactDescription, String artifactType, String modelType, String freeDescription, String existingArtifactId, String existingArtifactStructureDepth) throws LpRestException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addExecutionState(String modelSetId, String executionId, String userId, String threadId, String pageId, String artifactId) throws LpRestException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] listExecutionStates(String userId) throws LpRestException {
        States states = executionStates.getStatesOfLatestAddedModelSet(userId);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JAXB.marshal(states, bos);
        return bos.toByteArray();
    }
    
}