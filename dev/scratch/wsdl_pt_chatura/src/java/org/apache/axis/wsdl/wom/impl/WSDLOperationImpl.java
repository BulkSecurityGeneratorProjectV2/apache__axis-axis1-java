/**
 * Copyright 2001-2004 The Apache Software Foundation.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.axis.wsdl.wom.impl;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import org.apache.axis.wsdl.wom.MessageReference;
import org.apache.axis.wsdl.wom.WSDLOperation;

/**
 * @author Chathura Herath
 *
 */
public class WSDLOperationImpl implements WSDLOperation {

	private String name;
	private URI targetnemespace;
	private int messageExchangePattern;
	private MessageReference inputMessage;
	private MessageReference outputMessage;
 	private List infaults = new LinkedList();
	private List outfaults = new LinkedList();
	//value of parent if not specified
	private int style;
	
	private boolean safety = false;
	
	private List features = new LinkedList();
	
	private List properties = new LinkedList();	
	
	
	 
	public List getFeatures() {
		return features;
	}
	public void setFeatures(List features) {
		this.features = features;
	}
	public List getProperties() {
		return properties;
	}
	public void setProperties(List properties) {
		this.properties = properties;
	}
	public List getInfaults() {
		return infaults;
	}
	public void setInfaults(List infaults) {
		this.infaults = infaults;
	}
	public MessageReference getInputMessage() {
		return inputMessage;
	}
	public void setInputMessage(MessageReference inputMessage) {
		this.inputMessage = inputMessage;
	}
	public int getMessageExchangePattern() {
		return messageExchangePattern;
	}
	public void setMessageExchangePattern(int messageExchangePattern) {
		this.messageExchangePattern = messageExchangePattern;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List getOutfaults() {
		return outfaults;
	}
	public void setOutfaults(List outfaults) {
		this.outfaults = outfaults;
	}
	public MessageReference getOutputMessage() {
		return outputMessage;
	}
	public void setOutputMessage(MessageReference outputMessage) {
		this.outputMessage = outputMessage;
	}
	
	public boolean isSafe() {
		return safety;
	}
	public void setSafety(boolean safe) {
		this.safety = safe;
	}
	public int getStyle() {
		return style;
	}
	public void setStyle(int style) {
		this.style = style;
	}
	public URI getTargetnemespace() {
		return targetnemespace;
	}
	public void setTargetnemespace(URI targetnemespace) {
		this.targetnemespace = targetnemespace;
	}
}
