/*
 * Copyright (c) 2011 Stiftung Deutsches Elektronen-Synchrotron,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY.
 *
 * THIS SOFTWARE IS PROVIDED UNDER THIS LICENSE ON AN "../AS IS" BASIS.
 * WITHOUT WARRANTY OF ANY KIND, EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE. SHOULD THE SOFTWARE PROVE DEFECTIVE
 * IN ANY RESPECT, THE USER ASSUMES THE COST OF ANY NECESSARY SERVICING, REPAIR OR
 * CORRECTION. THIS DISCLAIMER OF WARRANTY CONSTITUTES AN ESSENTIAL PART OF THIS LICENSE.
 * NO USE OF ANY SOFTWARE IS AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
 * DESY HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS,
 * OR MODIFICATIONS.
 * THE FULL LICENSE SPECIFYING FOR THE SOFTWARE THE REDISTRIBUTION, MODIFICATION,
 * USAGE AND OTHER RIGHTS AND OBLIGATIONS IS INCLUDED WITH THE DISTRIBUTION OF THIS
 * PROJECT IN THE FILE LICENSE.HTML. IF THE LICENSE IS NOT INCLUDED YOU MAY FIND A COPY
 * AT HTTP://WWW.DESY.DE/LEGAL/LICENSE.HTM
 */
package org.csstudio.domain.desy.epics.types;

import junit.framework.Assert;

import org.junit.Test;

import static org.csstudio.domain.desy.epics.types.EpicsEnum.*;

/**
 * Test for {@link EpicsEnum}. 
 * 
 * @author bknerr
 * @since 12.05.2011
 */
public class EpicsEnumTest {
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidCreationFromString1() {
        EpicsEnum.createFromString("");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidCreationFromString2() {
        EpicsEnum.createFromString("RAW:hallo");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidCreationFromString3() {
        EpicsEnum.createFromString("RAW  : 2");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidCreationFromString4() {
        EpicsEnum.createFromString("STATE(hallo):2");
    }
    
    @Test
    public void testCreationFromString() {
        EpicsEnum fromStateString = EpicsEnum.createFromString(STATE + "(0)" + SEP + "hallo");
        Assert.assertTrue(fromStateString.isState());
        Assert.assertFalse(fromStateString.isRaw());
        Assert.assertEquals(Integer.valueOf(0), fromStateString.getStateIndex());
        Assert.assertEquals("hallo", fromStateString.getState());
        try {
            fromStateString.getRaw();
        } catch (IllegalStateException e) {
            // Great.
        }
        
        EpicsEnum fromRawString = EpicsEnum.createFromString(RAW + SEP + "26");
        Assert.assertFalse(fromRawString.isState());
        Assert.assertTrue(fromRawString.isRaw());
        Assert.assertEquals(Integer.valueOf(26), fromRawString.getRaw());
        try {
            fromRawString.getState();
        } catch (IllegalStateException e) {
            // Great.
        }
        try {
            fromRawString.getStateIndex();
        } catch (IllegalStateException e) {
            // Great.
        }
        
    }
    
    @Test
    public void testCreationFromRaw() {
        EpicsEnum fromRaw = EpicsEnum.createFromRaw(1);
        Assert.assertFalse(fromRaw.isState());
        Assert.assertTrue(fromRaw.isRaw());
        Assert.assertEquals(Integer.valueOf(1), fromRaw.getRaw());
        try {
            fromRaw.getState();
        } catch (IllegalStateException e) {
            // Great.
        }
        try {
            fromRaw.getStateIndex();
        } catch (IllegalStateException e) {
            // Great.
        }
        String string = fromRaw.toString();
        Assert.assertEquals(fromRaw, EpicsEnum.createFromString(string));
        
    }
    
    @Test
    public void testCreationFromState() {
    
        EpicsEnum fromStateName = EpicsEnum.createFromStateName("huhu");
        Assert.assertFalse(fromStateName.isRaw());
        Assert.assertTrue(fromStateName.isState());
        String string = fromStateName.toString();
        EpicsEnum fromString = EpicsEnum.createFromString(string);
        Assert.assertEquals(fromStateName, fromString);
    }
    
}
