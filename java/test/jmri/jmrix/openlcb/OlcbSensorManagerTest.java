package jmri.jmrix.openlcb;

import jmri.Sensor;
import jmri.util.JUnitUtil;
import jmri.util.junit.annotations.*;
import jmri.jmrix.can.TestTrafficController;

import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.openlcb.*;

/**
 * Tests for the jmri.jmrix.openlcb.OlcbSensorManager class.
 *
 * @author Bob Jacobsen Copyright 2008, 2010
 */
public class OlcbSensorManagerTest extends jmri.managers.AbstractSensorMgrTestBase {

    private static OlcbSystemConnectionMemo memo;
    static Connection connection;
    static NodeID nodeID = new NodeID(new byte[]{1, 0, 0, 0, 0, 0});
    static java.util.ArrayList<Message> messages;

    @Override
    public String getSystemName(int i) {
        return "MSx010203040506070" + i;
    }

    @Test
    public void testCtor() {
        Assert.assertNotNull("exists", l);
    }

    @Test
    @Override
    public void testProvideName() {
        // create
        Sensor t = l.provide(getSystemName(getNumToTest1()));
        // check
        Assert.assertNotNull("real object returned ", t);
        Assert.assertEquals("system name correct ", t, l.getBySystemName(getSystemName(getNumToTest1())));
    }

    @Override
    @Test
    public void testDefaultSystemName() {
        // create
        // olcb addresses are hex values requirng 16 digits.
        Sensor t = l.provideSensor(getSystemName(getNumToTest1()));
        // check
        Assert.assertNotNull("real object returned ", t);
        Assert.assertEquals("system name correct " + t.getSystemName(), t, l.getBySystemName(getSystemName(getNumToTest1())));
    }

    @Override
    @Disabled("ignoring this test due to the system name format, needs to be properly coded")
    @ToDo("Fix system name format")
    @Test
    public void testUpperLower() {
    }

    @Override
    @Test
    public void testMoveUserName() {
        Sensor t1 = l.provideSensor(getSystemName(getNumToTest1()));
        Sensor t2 = l.provideSensor(getSystemName(getNumToTest2()));
        t1.setUserName("UserName");
        Assert.assertSame(t1, l.getByUserName("UserName"));

        t2.setUserName("UserName");
        Assert.assertSame(t2, l.getByUserName("UserName"));

        Assert.assertNull(t1.getUserName());
    }

    @Test
    public void testDotted() {
        // olcb addresses are hex values requirng 16 digits.
        Sensor t = l.provideSensor("MS01.02.03.04.05.06.07.0" + getNumToTest2());
        String name = t.getSystemName();
        Assert.assertEquals(t, l.getSensor(name));
    }

    @Test
    @Override
    public void testRegisterDuplicateSystemName() throws java.beans.PropertyVetoException,
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        String s1 = l.makeSystemName("x0102030405060701;x0102030405060702");
        String s2 = l.makeSystemName("x0102030405060703;x0102030405060704");
        testRegisterDuplicateSystemName(l, s1, s2);
    }

    @Override
    @BeforeEach
    public void setUp() {
        l = new OlcbSensorManager(memo);
    }

    @AfterEach
    public void tearDown() {
        l.dispose();
    }

    @BeforeAll
    @SuppressWarnings("deprecated") // OlcbInterface(NodeID, Connection)
    static public void preClassInit() {
        JUnitUtil.setUp();
        JUnitUtil.initInternalTurnoutManager();
        nodeID = new NodeID(new byte[]{1, 0, 0, 0, 0, 0});

        messages = new java.util.ArrayList<>();
        connection = new AbstractConnection() {
            @Override
            public void put(Message msg, Connection sender) {
                messages.add(msg);
            }
        };

        memo = new OlcbSystemConnectionMemo(); // this self-registers as 'M'
        memo.setProtocol(jmri.jmrix.can.ConfigurationManager.OPENLCB);
        memo.setInterface(new OlcbInterface(nodeID, connection) {
            @Override
            public Connection getOutputConnection() {
                return connection;
            }
        });
        memo.setTrafficController(new TestTrafficController());
        memo.configureManagers();

        jmri.util.JUnitUtil.waitFor(()-> (!messages.isEmpty()),"Initialization Complete message");
    }

    @AfterAll
    public static void postClassTearDown() {
        if(memo != null && memo.getInterface() !=null ) {
           memo.getInterface().dispose();
        }
        memo = null;
        connection = null;
        nodeID = null;
        JUnitUtil.clearShutDownManager(); // put in place because AbstractMRTrafficController implementing subclass was not terminated properly
        JUnitUtil.tearDown();

    }

}
