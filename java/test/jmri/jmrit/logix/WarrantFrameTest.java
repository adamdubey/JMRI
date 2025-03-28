package jmri.jmrit.logix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import javax.swing.JFrame;

import jmri.ConfigureManager;
import jmri.InstanceManager;
import jmri.util.JUnitUtil;
import jmri.util.swing.JemmyUtil;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JDialogOperator;
import org.netbeans.jemmy.operators.JFrameOperator;

/**
 *
 * @author Paul Bender Copyright (C) 2017
 * @author Pete Cressman Copyright (C) 2020
 */
public class WarrantFrameTest {
    private WarrantManager _warrantMgr;

    @Test
    @DisabledIfSystemProperty(named ="java.awt.headless", matches ="true")
    public void testCTorNull(){
       assertThat(new WarrantFrame(null, null)).withFailMessage("WarrantFrame exits").isNotNull();
    }

    @Test
    @DisabledIfSystemProperty(named ="java.awt.headless", matches ="true")
    public void testCTorWarrant() throws Exception {
        // load and display
        File f = new File("java/test/jmri/jmrit/logix/valid/MeetTest.xml");
        InstanceManager.getDefault(ConfigureManager.class).load(f);
        jmri.util.JUnitAppender.suppressErrorMessage("Portal elem = null");

        WarrantPreferences.getDefault().setShutdown(WarrantPreferences.Shutdown.NO_MERGE);

        Warrant w = _warrantMgr.getWarrant("WestBoundStart");
        assertThat(new WarrantFrame(w, null)).withFailMessage("WarrantFrame exits").isNotNull();
    }

    @Test
    @DisabledIfSystemProperty(named ="java.awt.headless", matches ="true")
    public void testJoinWarrantsStop() throws Exception {
        // load and display
        File f = new File("java/test/jmri/jmrit/logix/valid/MeetTest.xml");
        InstanceManager.getDefault(ConfigureManager.class).load(f);
        jmri.util.JUnitAppender.suppressErrorMessage("Portal elem = null");

        WarrantPreferences.getDefault().setShutdown(WarrantPreferences.Shutdown.NO_MERGE);
        JUnitUtil.waitFor(100);

        Warrant startW = _warrantMgr.getWarrant("WestBoundStart");
        Warrant endW = _warrantMgr.getWarrant("WestBoundFinish");

        new Thread(() -> {
            JFrameOperator jfo = new JFrameOperator(WarrantTableFrame.getDefault());
            JDialogOperator jdo = new JDialogOperator(jfo, Bundle.getMessage("QuestionTitle"));
            JButtonOperator jbo = new JButtonOperator(jdo, Bundle.getMessage("ButtonYes"));
            jbo.push();
        }).start();
        
        WarrantFrame warrantFrame= new WarrantFrame(startW, endW);
        assertThat(warrantFrame).withFailMessage("JoinWFrame exits").isNotNull();

        warrantFrame._userNameBox.setText("WestBoundLocal");
        JFrameOperator editFrame = new JFrameOperator(warrantFrame);
        JemmyUtil.pressButton(editFrame, Bundle.getMessage("ButtonSave"));

        Warrant w = _warrantMgr.getWarrant("WestBoundLocal");
        assertThat(w).withFailMessage("Concatenated Warrant exits").isNotNull();

        warrantFrame.close();
        warrantFrame.dispose();
    }

    @Test
    @DisabledIfSystemProperty(named ="java.awt.headless", matches ="true")
    public void testJoinWarrantsNoStop() throws Exception {
        // load and display
        File f = new File("java/test/jmri/jmrit/logix/valid/MeetTest.xml");
        InstanceManager.getDefault(ConfigureManager.class).load(f);
        jmri.util.JUnitAppender.suppressErrorMessage("Portal elem = null");

        WarrantPreferences.getDefault().setShutdown(WarrantPreferences.Shutdown.NO_MERGE);
        JUnitUtil.waitFor(100);

        Warrant startW = _warrantMgr.getWarrant("WestBoundStart");
        Warrant endW = _warrantMgr.getWarrant("WestBoundFinish");

        new Thread(() -> {
            JFrameOperator jfo = new JFrameOperator(WarrantTableFrame.getDefault());
            JDialogOperator jdo = new JDialogOperator(jfo, Bundle.getMessage("QuestionTitle"));
            JButtonOperator jbo = new JButtonOperator(jdo, Bundle.getMessage("ButtonNo"));
            jbo.push();
        }).start();
        
        WarrantFrame warrantFrame= new WarrantFrame(startW, endW);
        assertThat(warrantFrame).withFailMessage("JoinWFrame exits").isNotNull();

        warrantFrame._userNameBox.setText("WestBound");
        JFrameOperator editFrame = new JFrameOperator(warrantFrame);
        JemmyUtil.pressButton(editFrame, Bundle.getMessage("ButtonSave"));

        Warrant w = _warrantMgr.getWarrant("WestBound");
        assertThat(w).withFailMessage("Concatenated Warrant exits").isNotNull();

        warrantFrame.close();
        warrantFrame.dispose();
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
        JUnitUtil.resetInstanceManager();
        JUnitUtil.initConfigureManager();
        JUnitUtil.resetProfileManager();
        JUnitUtil.initRosterConfigManager();
        JUnitUtil.initWarrantManager();
        JUnitUtil.initDebugThrottleManager();
        _warrantMgr = InstanceManager.getDefault(WarrantManager.class);
    }

    @AfterEach
    public void tearDown() {
        _warrantMgr.dispose();
        _warrantMgr = null;
        JUnitUtil.deregisterBlockManagerShutdownTask();
        JUnitUtil.tearDown();
    }

    // private final static Logger log = LoggerFactory.getLogger(WarrantFrameTest.class);

}
