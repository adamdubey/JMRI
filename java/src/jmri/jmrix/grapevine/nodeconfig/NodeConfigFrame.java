package jmri.jmrix.grapevine.nodeconfig;

import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import jmri.jmrix.grapevine.SerialMessage;
import jmri.jmrix.grapevine.SerialNode;
import jmri.jmrix.grapevine.SerialSensorManager;
import jmri.jmrix.grapevine.GrapevineSystemConnectionMemo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frame for user configuration of serial nodes.
 *
 * @author Bob Jacobsen Copyright (C) 2004, 2007
 * @author Dave Duchamp Copyright (C) 2004, 2006
 */
public class NodeConfigFrame extends jmri.util.JmriJFrame {

    private GrapevineSystemConnectionMemo memo = null;

    protected JSpinner nodeAddrSpinner;
    protected JLabel nodeAddrStatic = new JLabel("000");
    protected javax.swing.JComboBox<String> nodeTypeBox;

    protected JButton addButton = new JButton(Bundle.getMessage("ButtonAdd"));
    protected JButton editButton = new JButton(Bundle.getMessage("ButtonEdit"));
    protected JButton deleteButton = new JButton(Bundle.getMessage("ButtonDelete"));
    protected JButton initButton = new JButton(Bundle.getMessage("ButtonInit"));
    protected JButton doneButton = new JButton(Bundle.getMessage("ButtonDone"));
    protected JButton updateButton = new JButton(Bundle.getMessage("ButtonUpdate"));
    protected JButton cancelButton = new JButton(Bundle.getMessage("ButtonCancel"));

    protected JLabel statusText1 = new JLabel();
    protected JLabel statusText2 = new JLabel();
    protected JLabel statusText3 = new JLabel();

    protected boolean changedNode = false;  // true if a node was changed, deleted, or added
    protected boolean editMode = false;     // true if in edit mode
    private boolean checkEnabled = jmri.InstanceManager.getDefault(jmri.configurexml.ShutdownPreferences.class).isStoreCheckEnabled();

    protected SerialNode curNode = null;    // Serial Node being editted
    protected int nodeAddress = 0;          // Node address
    protected int nodeType = SerialNode.NODE2002V6; // Node type

    protected boolean errorInStatus1 = false;
    protected boolean errorInStatus2 = false;
    protected String stdStatus1 = Bundle.getMessage("NotesStd1");
    protected String stdStatus2 = Bundle.getMessage("NotesStd2");
    protected String stdStatus3 = Bundle.getMessage("NotesStd3");
    protected String editStatus1 = Bundle.getMessage("NotesEdit1");
    protected String editStatus2 = Bundle.getMessage("NotesEdit2");
    protected String editStatus3 = Bundle.getMessage("NotesEdit3");

    /**
     * Constructor method.
     * @param _memo system connection.
     */
    public NodeConfigFrame(GrapevineSystemConnectionMemo _memo) {
        super();
        memo = _memo;
    }

    /**
     * Initialize the config window.
     */
    @Override
    public void initComponents() {
        setTitle(Bundle.getMessage("ConfigNodesTitle"));

        Container contentPane = getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // Set up node address and node type
        JPanel panel1 = new JPanel();
        panel1.setLayout(new BoxLayout(panel1, BoxLayout.Y_AXIS));

        // panel11 is the node address and type
        JPanel panel11 = new JPanel();
        panel11.setLayout(new FlowLayout());
        panel11.add(new JLabel(Bundle.getMessage("LabelNodeAddress") + " "));
        nodeAddrSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        panel11.add(nodeAddrSpinner);
        nodeAddrSpinner.setToolTipText(Bundle.getMessage("TipNodeAddress"));
        panel11.add(nodeAddrStatic);
        nodeAddrStatic.setVisible(false);
        panel11.add(new JLabel("   " + Bundle.getMessage("LabelNodeType") + " "));
        nodeTypeBox = new JComboBox<String>(SerialNode.getBoardNames());
        panel11.add(nodeTypeBox);
        nodeTypeBox.setToolTipText(Bundle.getMessage("TipNodeType"));
        contentPane.add(panel11);

        // Set up the notes panel
        JPanel panel3 = new JPanel();
        panel3.setLayout(new BoxLayout(panel3, BoxLayout.Y_AXIS));
        JPanel panel31 = new JPanel();
        panel31.setLayout(new FlowLayout());
        statusText1.setText(stdStatus1);
        statusText1.setVisible(true);
        panel31.add(statusText1);
        JPanel panel32 = new JPanel();
        panel32.setLayout(new FlowLayout());
        statusText2.setText(stdStatus2);
        statusText2.setVisible(true);
        panel32.add(statusText2);
        JPanel panel33 = new JPanel();
        panel33.setLayout(new FlowLayout());
        statusText3.setText(stdStatus3);
        statusText3.setVisible(true);
        panel33.add(statusText3);
        panel3.add(panel31);
        panel3.add(panel32);
        panel3.add(panel33);
        Border panel3Border = BorderFactory.createEtchedBorder();
        Border panel3Titled = BorderFactory.createTitledBorder(panel3Border,
                Bundle.getMessage("BoxLabelNotes"));
        panel3.setBorder(panel3Titled);
        contentPane.add(panel3);

        // Set up buttons
        JPanel panel4 = new JPanel();
        panel4.setLayout(new FlowLayout());
        addButton.setText(Bundle.getMessage("ButtonAdd"));
        addButton.setVisible(true);
        addButton.setToolTipText(Bundle.getMessage("TipAddButton"));
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addButtonActionPerformed();
            }
        });
        panel4.add(addButton);
        editButton.setText(Bundle.getMessage("ButtonEdit"));
        editButton.setVisible(true);
        editButton.setToolTipText(Bundle.getMessage("TipEditButton"));
        panel4.add(editButton);
        editButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editButtonActionPerformed();
            }
        });
        panel4.add(deleteButton);
        deleteButton.setText(Bundle.getMessage("ButtonDelete"));
        deleteButton.setVisible(true);
        deleteButton.setToolTipText(Bundle.getMessage("TipDeleteButton"));
        panel4.add(deleteButton);
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteButtonActionPerformed();
            }
        });

        panel4.add(doneButton);
        doneButton.setText(Bundle.getMessage("ButtonDone"));
        doneButton.setVisible(true);
        doneButton.setToolTipText(Bundle.getMessage("TipDoneButton"));
        panel4.add(doneButton);
        doneButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doneButtonActionPerformed();
            }
        });

        panel4.add(updateButton);
        updateButton.setText(Bundle.getMessage("ButtonUpdate"));
        updateButton.setVisible(true);
        updateButton.setToolTipText(Bundle.getMessage("TipUpdateButton"));
        panel4.add(updateButton);
        updateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateButtonActionPerformed();
            }
        });
        updateButton.setVisible(false);

        panel4.add(initButton);
        initButton.setText(Bundle.getMessage("ButtonInit"));
        initButton.setVisible(true);
        initButton.setToolTipText(Bundle.getMessage("TipInitButton"));
        panel4.add(initButton);
        initButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                initButtonActionPerformed();
            }
        });
        initButton.setVisible(true);

        panel4.add(cancelButton);
        cancelButton.setText(Bundle.getMessage("ButtonCancel"));
        cancelButton.setVisible(true);
        cancelButton.setToolTipText(Bundle.getMessage("TipCancelButton"));
        panel4.add(cancelButton);
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelButtonActionPerformed();
            }
        });
        cancelButton.setVisible(false);

        contentPane.add(panel4);

        // add help menu to window
        addHelpMenu("package.jmri.jmrix.grapevine.nodeconfig.NodeConfigFrame", true);

        // pack for display
        pack();
    }

    /**
     * Set the node number.
     * @param node node address.
     */
    public void setNodeAddress(int node) {
        nodeAddrSpinner.setValue(node);
    }

    /**
     * Handle Add button.
     */
    public void addButtonActionPerformed() {
        // Check that a node with this address does not exist
        int nodeAddress = readNodeAddress();
        if (nodeAddress < 0) {
            return;
        }
        // get a SerialNode corresponding to this node address if one exists
        curNode = (SerialNode) memo.getTrafficController().getNodeFromAddress(nodeAddress);
        if (curNode != null) {
            statusText1.setText(Bundle.getMessage("Error1", Integer.toString(nodeAddress)));
            statusText1.setVisible(true);
            errorInStatus1 = true;
            resetNotes2();
            return;
        }
        nodeType = nodeTypeBox.getSelectedIndex();

        // all ready, create the new node
        curNode = new SerialNode(nodeAddress, nodeType, memo.getTrafficController());

        // configure the new node
        setNodeParameters();
        // register any orphan sensors that this node may have
        ((SerialSensorManager)memo.getSensorManager()).registerSensorsForNode(curNode);
        // reset after succefully adding node
        resetNotes();
        changedNode = true;
        // provide user feedback
        statusText1.setText(Bundle.getMessage("FeedBackAdd") + " "
                + Integer.toString(nodeAddress));
        errorInStatus1 = true;
    }

    /**
     * Handle Edit button.
     */
    public void editButtonActionPerformed() {
        // Find Serial Node address
        nodeAddress = readNodeAddress();
        if (nodeAddress < 0) {
            return;
        }
        // get the SerialNode corresponding to this node address
        curNode = (SerialNode) memo.getTrafficController().getNodeFromAddress(nodeAddress);
        if (curNode == null) {
            statusText1.setText(Bundle.getMessage("Error4"));
            statusText1.setVisible(true);
            errorInStatus1 = true;
            resetNotes2();
            return;
        }
        // Set up static node address
        nodeAddrStatic.setText(Integer.toString(nodeAddress));
        nodeAddrSpinner.setVisible(false);
        nodeAddrStatic.setVisible(true);
        // get information for this node and set up combo box
        nodeType = curNode.getNodeType();
        nodeTypeBox.setSelectedIndex(nodeType);
        // Switch buttons
        editMode = true;
        addButton.setVisible(false);
        editButton.setVisible(false);
        deleteButton.setVisible(false);
        doneButton.setVisible(false);
        updateButton.setVisible(true);
        cancelButton.setVisible(true);
        // Switch to edit notes
        statusText1.setText(editStatus1);
        statusText2.setText(editStatus2);
        statusText3.setText(editStatus3);
    }

    /**
     * Handle Init button.
     */
    public void initButtonActionPerformed() {
        // Find Serial Node address
        int nodeAddress = readNodeAddress();
        log.debug("node read {}", nodeAddress);
        if (nodeAddress < 0) {
            return;
        }
        // get the SerialNode corresponding to this node address
        curNode = (SerialNode) memo.getTrafficController().getNodeFromAddress(nodeAddress);
        if (curNode == null) {
            statusText1.setText(Bundle.getMessage("Error4"));
            statusText1.setVisible(true);
            errorInStatus1 = true;
            resetNotes2();
            return;
        }
        // ok, do the init
        log.debug("init packet sent to node {}", nodeAddress);
        memo.getTrafficController().sendSerialMessage((SerialMessage) curNode.createInitPacket(), null);
    }

    /**
     * Handle Delete button.
     */
    public void deleteButtonActionPerformed() {
        // Find Serial Node address
        int nodeAddress = readNodeAddress();
        if (nodeAddress < 0) {
            return;
        }
        // get the SerialNode corresponding to this node address
        curNode = (SerialNode) memo.getTrafficController().getNodeFromAddress(nodeAddress);
        if (curNode == null) {
            statusText1.setText(Bundle.getMessage("Error4"));
            statusText1.setVisible(true);
            errorInStatus1 = true;
            resetNotes2();
            return;
        }
        // confirm deletion with the user
        if (javax.swing.JOptionPane.OK_OPTION == javax.swing.JOptionPane.showConfirmDialog(
                this, Bundle.getMessage("ConfirmDelete1") + "\n"
                + Bundle.getMessage("ConfirmDelete2"), Bundle.getMessage("ConfirmDeleteTitle"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE)) {
            // delete this node
            memo.getTrafficController().deleteNode(nodeAddress);
            // provide user feedback
            resetNotes();
            statusText1.setText(Bundle.getMessage("FeedBackDelete") + " "
                    + Integer.toString(nodeAddress));
            errorInStatus1 = true;
            changedNode = true;
        } else {
            // reset as needed
            resetNotes();
        }
    }

    /**
     * Handle Done button.
     */
    public void doneButtonActionPerformed() {
        if (editMode) {
            // Reset
            editMode = false;
            curNode = null;
            // Switch buttons
            addButton.setVisible(true);
            editButton.setVisible(true);
            deleteButton.setVisible(true);
            doneButton.setVisible(true);
            updateButton.setVisible(false);
            cancelButton.setVisible(false);
            nodeAddrSpinner.setVisible(true);
            nodeAddrStatic.setVisible(false);
        }
        if (changedNode && !checkEnabled) {
            // Remind user to Save new configuration
            javax.swing.JOptionPane.showMessageDialog(this,
                    Bundle.getMessage("ReminderNode1") + "\n" + Bundle.getMessage("Reminder2"),
                    Bundle.getMessage("ReminderTitle"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
        setVisible(false);
        dispose();
    }

    /**
     * Handle Update button.
     */
    public void updateButtonActionPerformed() {
        // update node information
        nodeType = nodeTypeBox.getSelectedIndex();
        log.debug("update performed: was {} request {}", curNode.getNodeType(), nodeType);
        if (curNode.getNodeType() != nodeType) {
            // node type has changed
            curNode.setNodeType(nodeType);
        }
        setNodeParameters();
        changedNode = true;
        // Reset Edit Mode
        editMode = false;
        curNode = null;
        // Switch buttons
        addButton.setVisible(true);
        editButton.setVisible(true);
        deleteButton.setVisible(true);
        doneButton.setVisible(true);
        updateButton.setVisible(false);
        cancelButton.setVisible(false);
        // make node address editable again
        nodeAddrSpinner.setVisible(true);
        nodeAddrStatic.setVisible(false);
        // refresh notes panel
        statusText2.setText(stdStatus2);
        statusText3.setText(stdStatus3);
        // provide user feedback
        statusText1.setText(Bundle.getMessage("FeedBackUpdate") + " "
                + Integer.toString(nodeAddress));
        errorInStatus1 = true;
    }

    /**
     * Handle Cancel button.
     */
    public void cancelButtonActionPerformed() {
        // Reset
        editMode = false;
        curNode = null;
        // Switch buttons
        addButton.setVisible(true);
        editButton.setVisible(true);
        deleteButton.setVisible(true);
        doneButton.setVisible(true);
        updateButton.setVisible(false);
        cancelButton.setVisible(false);
        // make node address editable again
        nodeAddrSpinner.setVisible(true);
        nodeAddrStatic.setVisible(false);
        // refresh notes panel
        statusText1.setText(stdStatus1);
        statusText2.setText(stdStatus2);
        statusText3.setText(stdStatus3);
    }

    /**
     * Do the Done action if the window is closed early.
     */
    @Override
    public void windowClosing(java.awt.event.WindowEvent e) {
        doneButtonActionPerformed();
    }

    /**
     * Set node parameters. The node must exist, and be in 'curNode'.
     * Also, the node type must be set and in 'nodeType'.
     */
    void setNodeParameters() {
        // set curNode type
        curNode.setNodeType(nodeType);
        // Cause reinitialization of this Node to reflect these parameters
        memo.getTrafficController().initializeSerialNode(curNode);
    }

    /**
     * Reset the Notes error after error display.
     */
    private void resetNotes() {
        if (errorInStatus1) {
            if (editMode) {
                statusText1.setText(editStatus1);
            } else {
                statusText1.setText(stdStatus1);
            }
            errorInStatus1 = false;
        }
        resetNotes2();
    }

    /**
     * Reset the second line of the Notes area.
     */
    private void resetNotes2() {
        if (errorInStatus2) {
            if (editMode) {
                statusText1.setText(editStatus2);
            } else {
                statusText2.setText(stdStatus2);
            }
            errorInStatus2 = false;
        }
    }

    /**
     * Read node address and check for legal range.
     *
     * @return a node address in the range 0-127.
     * Valid range guarded by SpinnerNumberModel.
     */
    private int readNodeAddress() {
        int addr = -1;
        addr = (Integer) nodeAddrSpinner.getValue();
        return (addr);
    }

    private final static Logger log = LoggerFactory.getLogger(NodeConfigFrame.class);

}
