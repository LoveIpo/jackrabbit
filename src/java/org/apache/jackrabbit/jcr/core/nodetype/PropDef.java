/*
 * Copyright 2002-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.jcr.core.nodetype;

import org.apache.jackrabbit.jcr.core.InternalValue;

import javax.jcr.PropertyType;
import java.util.Arrays;

/**
 * A <code>PropDef</code> ...
 *
 * @author Stefan Guggisberg
 * @version $Revision: 1.23 $, $Date: 2004/09/09 15:23:43 $
 */
public class PropDef extends ChildItemDef {

    private int requiredType = PropertyType.UNDEFINED;
    private ValueConstraint valueConstraint = null;
    private InternalValue[] defaultValues = null;
    private boolean multiple = false;

    /**
     * Default constructor.
     */
    public PropDef() {
    }

    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	}
	if (obj instanceof PropDef) {
	    PropDef other = (PropDef) obj;
	    return super.equals(obj) &&
		    requiredType == other.requiredType &&
		    (valueConstraint == null ? other.valueConstraint == null : valueConstraint.equals(other.valueConstraint)) &&
		    Arrays.equals(defaultValues, other.defaultValues) &&
		    multiple == other.multiple;
	}
	return false;
    }

    /**
     * @param requiredType
     */
    public void setRequiredType(int requiredType) {
	this.requiredType = requiredType;
    }

    /**
     * @param valueConstraint
     */
    public void setValueConstraint(ValueConstraint valueConstraint) {
	this.valueConstraint = valueConstraint;
    }

    /**
     * @param defaultValues
     */
    public void setDefaultValues(InternalValue[] defaultValues) {
	this.defaultValues = defaultValues;
    }

    /**
     * @param multiple
     */
    public void setMultiple(boolean multiple) {
	this.multiple = multiple;
    }

    /**
     * @return
     */
    public int getRequiredType() {
	return requiredType;
    }

    /**
     * @return
     */
    public ValueConstraint getValueConstraint() {
	return valueConstraint;
    }

    /**
     * @return
     */
    public InternalValue[] getDefaultValues() {
	return defaultValues;
    }

    /**
     * @return
     */
    public boolean isMultiple() {
	return multiple;
    }

    /**
     * @return
     */
    public boolean definesNode() {
	return false;
    }
}
