package com.prg.esb.mediator.util;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import java.util.ArrayList;

/**
 * Get payload in a Property and set as a Json or xml payload to the body.
 * (A property DATA_TO_SET need to have payload to set as json or xml,
 *  A property PAYLOAD_TYPE need to have payload type as xml of json)
 */
public class SetPayloadMediator extends AbstractMediator {

    private static final Log log = LogFactory.getLog(SetPayloadMediator.class);
    private static final String DATA_TO_SET = "DATA_TO_SET";
    private static final String PAYLOAD_TYPE = "PAYLOAD_TYPE";

    public boolean mediate(MessageContext messageContext) {

        try {
            String payloadType = (String) messageContext.getProperty(PAYLOAD_TYPE);
            if(payloadType.equals("json")) {
                String payload = (String) messageContext.getProperty(DATA_TO_SET);
                if(log.isDebugEnabled()) {
                    log.debug("Property DATA_TO_SET has payload content " + payload);
                }
                org.apache.axis2.context.MessageContext axis2MessageContext = ((Axis2MessageContext) messageContext)
                        .getAxis2MessageContext();

                //setting the payload as the message payload
                JsonUtil.getNewJsonPayload(axis2MessageContext, payload, true, true);

                //set message type property
                //<property name="messageType" scope="axis2" value="application/json"/>
                ((Axis2MessageContext) messageContext)
                        .getAxis2MessageContext().setProperty("messageType", "application/json");

            } else if(payloadType.equals("xml")) {
                ArrayList<OMNode> sourceNodeList = new ArrayList<OMNode>();
                Object o = messageContext.getProperty(DATA_TO_SET);

                //create OM from property
                if (o instanceof OMElement) {
                    sourceNodeList.add((OMElement) o);
                } else if (o instanceof String) {
                    String sourceStr = (String) o;
                    OMFactory fac = OMAbstractFactory.getOMFactory();
                    sourceNodeList.add(fac.createOMText(sourceStr));
                }

                //get the body
                SOAPEnvelope env = messageContext.getEnvelope();
                SOAPBody body = env.getBody();
                OMElement e = body.getFirstElement();

                if (e != null) {
                    //replace
                    boolean isInserted = false;
                    for (OMNode elem : sourceNodeList) {
                        if (elem instanceof OMElement) {
                            e.insertSiblingBefore(elem);
                            isInserted = true;
                        } else if (elem instanceof OMText) {
                            e.setText(((OMText) elem).getText());
                        } else {
                            log.error("Invalid Source object to be inserted.");
                        }
                    }
                    if (isInserted) {
                        e.detach();
                    }
                } else {
                    // if the body is empty just add as a child
                    for (OMNode elem : sourceNodeList) {
                        if (elem instanceof OMElement) {
                            synchronized (elem){
                                body.addChild(elem);
                            }
                        } else {
                            log.error("Invalid Object type to be inserted into message body");
                        }
                    }
                }
                ((Axis2MessageContext) messageContext)
                        .getAxis2MessageContext().setProperty("messageType", "application/xml");
            } else {
                handleException("Error while setting payload to the context - messageType is not known",
                        messageContext);
            }
        } catch (Exception e) {
            handleException("Error while setting payload to the context", messageContext);
        }
        return true;
    }
}
