package jmri.jmrit.beantable;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import jmri.*;
import jmri.jmrit.display.layoutEditor.LayoutBlockManager;
import jmri.managers.DefaultSignalMastLogicManager;
import jmri.util.ThreadingUtil;
import jmri.util.JmriJFrame;
import jmri.util.swing.JmriMouseEvent;
import jmri.util.swing.XTableColumnModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignalMastLogicTableAction extends AbstractTableAction<SignalMastLogic> {

    /**
     * Create an action with a specific title.
     * <p>
     * Note that the argument is the Action title, not the title of the
     * resulting frame. Perhaps this should be changed?
     *
     * @param s title of the action
     */
    public SignalMastLogicTableAction(String s) {
        super(s);
    }

    public SignalMastLogicTableAction() {
        this(Bundle.getMessage("TitleSignalMastLogicTable"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // create the JTable model, with changes for specific NamedBean
        createModel();
        TableRowSorter<BeanTableDataModel<SignalMastLogic>> sorter = new TableRowSorter<>(m);
        JTable dataTable = m.makeJTable(m.getMasterClassName(), m, sorter);
        // create the frame
        f = new jmri.jmrit.beantable.BeanTableFrame<SignalMastLogic>(m, helpTarget(), dataTable) {
        };
        setMenuBar(f);
        setTitle();
        addToFrame(f);
        f.pack();
        f.setVisible(true);
    }

    /**
     * Insert a table specific Tools menu. Account for the Window and Help
     * menus, which are already added to the menu bar as part of the creation of
     * the JFrame, by adding the Tools menu 2 places earlier unless the table is
     * part of the ListedTableFrame, that adds the Help menu later on.
     *
     * @param f the JFrame of this table
     */
    @Override
    public void setMenuBar(BeanTableFrame<SignalMastLogic> f) {
        final JmriJFrame finalF = f;   // needed for anonymous ActionListener class
        JMenuBar menuBar = f.getJMenuBar();
        int pos = menuBar.getMenuCount() - 1; // count the number of menus to insert the TableMenu before 'Window' and 'Help'
        int offset = 1;
        log.debug("setMenuBar number of menu items = {}", pos);
        for (int i = 0; i <= pos; i++) {
            if (menuBar.getComponent(i) instanceof JMenu) {
                if (((AbstractButton) menuBar.getComponent(i)).getText().equals(Bundle.getMessage("MenuHelp"))) {
                    offset = -1; // correct for use as part of ListedTableAction where the Help Menu is not yet present
                }
            }
        }
        JMenu pathMenu = new JMenu(Bundle.getMessage("MenuTools"));
        menuBar.add(pathMenu, pos + offset);
        JMenuItem item = new JMenuItem(Bundle.getMessage("MenuItemAutoGen"));
        pathMenu.add(item);
        item.addActionListener((ActionEvent e) -> {
            autoCreatePairs(finalF);
        });
        item = new JMenuItem(Bundle.getMessage("MenuItemAutoGenSections"));
        pathMenu.add(item);
        item.addActionListener((ActionEvent e) -> {
            ((DefaultSignalMastLogicManager) InstanceManager.getDefault(SignalMastLogicManager.class)).generateSection();
            InstanceManager.getDefault(SectionManager.class).generateBlockSections();
            JOptionPane.showMessageDialog(finalF, Bundle.getMessage("SectionGenerationComplete"));
        });
        JMenuItem setSMLDirSensors = new JMenuItem(Bundle.getMessage("MenuItemAddDirectionSensors"));
        pathMenu.add(setSMLDirSensors);
        setSMLDirSensors.addActionListener((ActionEvent e) -> {
            int n = InstanceManager.getDefault(SignalMastLogicManager.class).setupSignalMastsDirectionSensors();
            if (n > 0) {
                JOptionPane.showMessageDialog(finalF, java.text.MessageFormat.format(
                        Bundle.getMessage("MenuItemAddDirectionSensorsErrorCount"), n),
                        Bundle.getMessage("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
        });

    }

    private List<Hashtable<SignalMastLogic, SignalMast>> signalMastLogicList = null;

    @Override
    protected void createModel() {
        m = new BeanTableDataModel<SignalMastLogic>() {
            static public final int SOURCECOL = 0;
            static public final int SOURCEAPPCOL = 1;
            static public final int DESTCOL = 2;
            static public final int DESTAPPCOL = 3;
            static public final int COMCOL = 4;
            static public final int DELCOL = 5;
            static public final int ENABLECOL = 6;
            static public final int EDITLOGICCOL = 7;
            static public final int MAXSPEEDCOL = 8;
            static public final int COLUMNCOUNT = 9;

            //We have to set a manager first off, but this gets replaced.
            @Override
            protected SignalMastLogicManager getManager() {
                return InstanceManager.getDefault(SignalMastLogicManager.class);
            }

            @Override
            public String getValue(String s) {
                return "Set";
            }

            @Override
            protected String getMasterClassName() {
                return getClassName();
            }

            @Override
            public void clickOn(SignalMastLogic t) {
            }

            @Override
            protected synchronized void updateNameList() {
                // first, remove listeners from the individual objects
                if (signalMastLogicList != null) {
                    for (int i = 0; i < signalMastLogicList.size(); i++) {
                        // if object has been deleted, it's not here; ignore it
                        Hashtable<SignalMastLogic, SignalMast> b = signalMastLogicList.get(i);
                        Enumeration<SignalMastLogic> en = b.keys();
                        while (en.hasMoreElements()) {
                            SignalMastLogic sm = en.nextElement();
                            SignalMast dest = b.get(sm);
                            sm.removePropertyChangeListener(this);
                            sm.getSourceMast().removePropertyChangeListener(this);
                            dest.removePropertyChangeListener(this);
                        }
                    }
                }
                List<SignalMastLogic> source = getManager().getSignalMastLogicList();
                signalMastLogicList = new ArrayList<>();
                for (int i = 0; i < source.size(); i++) {
                    List<SignalMast> destList = source.get(i).getDestinationList();
                    source.get(i).addPropertyChangeListener(this);
                    source.get(i).getSourceMast().addPropertyChangeListener(this);
                    for (int j = 0; j < destList.size(); j++) {
                        Hashtable<SignalMastLogic, SignalMast> hash = new Hashtable<>(1);
                        hash.put(source.get(i), destList.get(j));
                        destList.get(j).addPropertyChangeListener(this);
                        signalMastLogicList.add(hash);
                    }
                }
            }

            //Will need to redo this so that we work out the row number from looking in the signalmastlogiclist.
            @Override
            public void propertyChange(java.beans.PropertyChangeEvent e) {
                if (suppressUpdate) {
                    return;
                }
                // updateNameList();
                if (e.getPropertyName().equals("length") || e.getPropertyName().equals("updatedDestination") || e.getPropertyName().equals("updatedSource")) {
                    updateNameList();
                    log.debug("Table changed length to {}", signalMastLogicList.size());
                    fireTableDataChanged();
                } else if (e.getSource() instanceof SignalMastLogic) {
                    SignalMastLogic logic = (SignalMastLogic) e.getSource();
                    if (matchPropertyName(e)) {
                        for (int i = 0; i < signalMastLogicList.size(); i++) {
                            Hashtable<SignalMastLogic, SignalMast> b = signalMastLogicList.get(i);
                            Enumeration<SignalMastLogic> en = b.keys();
                            while (en.hasMoreElements()) {
                                SignalMastLogic sm = en.nextElement();
                                if (sm == logic) {
                                    fireTableRowsUpdated(i, i);
                                }
                            }
                        }
                    }
                } else if (e.getSource() instanceof SignalMast) {
                    SignalMast sigMast = (SignalMast) e.getSource();
                    for (int i = 0; i < signalMastLogicList.size(); i++) {
                        Hashtable<SignalMastLogic, SignalMast> b = signalMastLogicList.get(i);
                        Enumeration<SignalMastLogic> en = b.keys();
                        while (en.hasMoreElements()) {
                            SignalMastLogic sm = en.nextElement();
                            //SignalMast dest = b.get(sm);
                            if (sm.getSourceMast() == sigMast) {
                                fireTableRowsUpdated(i, i);
                            }
                        }
                    }
                }
            }

            /**
             * Is this property event announcing a change this table should
             * display?
             * <p>
             * Note that events will come both from the NamedBeans and also from
             * the manager
             */
            @Override
            protected boolean matchPropertyName(java.beans.PropertyChangeEvent e) {
                return ((e.getPropertyName().contains("Comment")) || (e.getPropertyName().contains("Enable")));
            }

            @Override
            public int getColumnCount() {
                return COLUMNCOUNT;
            }

            @Override
            public void setValueAt(Object value, int row, int col) {
                SignalMastLogic rowLogic = getLogicFromRow(row);
                if ( rowLogic == null ){
                    return;
                }
                switch (col) {
                    case COMCOL:
                        rowLogic.setComment((String) value, getDestMastFromRow(row));
                        break;
                    case EDITLOGICCOL:
                        SwingUtilities.invokeLater(() -> {
                            editLogic(row);
                        });
                        break;
                    case DELCOL:
                        // button fired, delete Bean
                        deleteLogic(row);
                        break;
                    case ENABLECOL:
                        SignalMast destMast = getDestMastFromRow(row);
                        if (destMast==null){
                            break;
                        }
                        if ((Boolean) value) {
                            rowLogic.setEnabled(destMast);
                        } else {
                            rowLogic.setDisabled(destMast);
                        }
                        break;
                    default:
                        break;
                }
            }

            @Override
            public String getColumnName(int col) {
                switch (col) {
                    case SOURCECOL:
                        return Bundle.getMessage("Source");
                    case DESTCOL:
                        return Bundle.getMessage("Destination");
                    case SOURCEAPPCOL:
                        return Bundle.getMessage("LabelAspectType");
                    case DESTAPPCOL:
                        return Bundle.getMessage("LabelAspectType");
                    case COMCOL:
                        return Bundle.getMessage("Comment");
                    case DELCOL:
                        return ""; // override default, no title for Delete column
                    case EDITLOGICCOL:
                        return ""; // override default, no title for Edit column
                    case ENABLECOL:
                        return Bundle.getMessage("ColumnHeadEnabled");
                    case MAXSPEEDCOL:
                        return Bundle.getMessage("LabelMaxSpeed");
                    default:
                        return "unknown";
                }
            }

            @Override
            public Class<?> getColumnClass(int col) {
                switch (col) {
                    case SOURCECOL:
                    case DESTCOL:
                    case SOURCEAPPCOL:
                    case COMCOL:
                    case DESTAPPCOL:
                        return String.class;
                    case ENABLECOL:
                        return Boolean.class;
                    case EDITLOGICCOL:
                    case DELCOL:
                        return JButton.class;
                    case MAXSPEEDCOL:
                        return Float.class;
                    default:
                        return null;
                }
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                switch (col) {
                    case COMCOL:
                    case EDITLOGICCOL:
                    case DELCOL:
                    case ENABLECOL:
                        return true;
                    default:
                        return false;
                }
            }

            void editLogic(int row) {
                SignalMastLogic sml = getLogicFromRow(row);
                if ( sml != null ) {
                    sigLog.setMast(sml.getSourceMast(), getDestMastFromRow(row));
                    sigLog.actionPerformed(null);
                }
            }

            void deleteLogic(int row) {
                //This needs to be looked at
                SignalMastLogic sml = getLogicFromRow(row);
                SignalMast destMast = getDestMastFromRow(row);
                if ( sml != null && destMast !=null ) {
                    InstanceManager.getDefault(SignalMastLogicManager.class).removeSignalMastLogic(sml, destMast);
                }
            }

            @CheckForNull
            public SignalMast getDestMastFromRow(int row) {
                // if object has been deleted, it's not here; ignore it
                Hashtable<SignalMastLogic, SignalMast> b = signalMastLogicList.get(row);
                Enumeration<SignalMastLogic> en = b.keys();
                while (en.hasMoreElements()) {
                    return b.get(en.nextElement());
                }
                return null;
            }

            @CheckForNull
            public SignalMastLogic getLogicFromRow(int row) {
                Hashtable<SignalMastLogic, SignalMast> b = signalMastLogicList.get(row);
                Enumeration<SignalMastLogic> en = b.keys();
                while (en.hasMoreElements()) {
                    return en.nextElement();
                }
                return null;
            }

            @Override
            public int getPreferredWidth(int col) {
                switch (col) {
                    case SOURCECOL:
                    case DESTCOL:
                    case DESTAPPCOL:
                    case SOURCEAPPCOL:
                    case MAXSPEEDCOL:
                        return new JTextField(10).getPreferredSize().width;
                    case COMCOL:
                        return 75;
                    case EDITLOGICCOL: // not actually used due to the configureTable, setColumnToHoldButton, configureButton
                        return new JTextField(6).getPreferredSize().width;
                    case DELCOL: // not actually used due to the configureTable, setColumnToHoldButton, configureButton
                    case ENABLECOL:
                        return new JTextField(5).getPreferredSize().width;
                    default:
                        return new JTextField(8).getPreferredSize().width;
                }
            }

            @Override
            public void configureTable(JTable table) {
                setColumnToHoldButton(table, EDITLOGICCOL,
                        new JButton(Bundle.getMessage("ButtonEdit")));
                table.getTableHeader().setReorderingAllowed(true);

                // have to shut off autoResizeMode to get horizontal scroll to work (JavaSwing p 541)
                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

                // resize columns as requested
                for (int i = 0; i < table.getColumnCount(); i++) {
                    int width = getPreferredWidth(i);
                    table.getColumnModel().getColumn(i).setPreferredWidth(width);
                }
                table.sizeColumnsToFit(-1);

                // configValueColumn(table);
                configDeleteColumn(table);
            }

            @Override
            public SignalMastLogic getBySystemName(@Nonnull String name) {
                return null;
            }

            @Override
            public SignalMastLogic getByUserName(@Nonnull String name) {
                return null;
            }

            @Override
            synchronized public void dispose() {

                getManager().removePropertyChangeListener(this);
                if (signalMastLogicList != null) {
                    for (int i = 0; i < signalMastLogicList.size(); i++) {
                        SignalMastLogic b = getLogicFromRow(i);
                        if (b != null) {
                            b.removePropertyChangeListener(this);
                        }
                    }
                }
            }

            @Override
            public int getRowCount() {
                return signalMastLogicList.size();
            }

            @Override
            public Object getValueAt(int row, int col) {
                // some error checking
                if (row >= signalMastLogicList.size()) {
                    log.debug("row index is greater than signalMastLogicList size");
                    return null;
                }
                SignalMastLogic b = getLogicFromRow(row);
                if (b==null){
                    return null;
                }
                SignalMast destMast;
                switch (col) {
                    case SOURCECOL:
                        return b.getSourceMast().getDisplayName();
                    case DESTCOL:  // return user name
                        // sometimes, the TableSorter invokes this on rows that no longer exist, so we check
                        destMast = getDestMastFromRow(row);
                        return ( destMast != null ? destMast.getDisplayName() : null);
                    case SOURCEAPPCOL:  //
                        return b.getSourceMast().getAspect();
                    case DESTAPPCOL:  //
                        destMast = getDestMastFromRow(row);
                        return ( destMast != null ? destMast.getAspect() : null);
                    case COMCOL:
                        return b.getComment(getDestMastFromRow(row));
                    case DELCOL:
                        return Bundle.getMessage("ButtonDelete");
                    case EDITLOGICCOL:
                        return Bundle.getMessage("ButtonEdit");
                    case ENABLECOL:
                        return b.isEnabled(getDestMastFromRow(row));
                    case MAXSPEEDCOL:
                        return b.getMaximumSpeed(getDestMastFromRow(row));
                    default:
                        //log.error("internal state inconsistent with table requst for "+row+" "+col);
                        return null;
                }
            }

            @Override
            protected void configDeleteColumn(JTable table) {
                // have the delete column hold a button
                setColumnToHoldButton(table, DELCOL,
                        new JButton(Bundle.getMessage("ButtonDelete")));
            }

            @Override
            protected String getBeanType() {
                return "Signal Mast Logic";
            }

            @Override
            protected void showPopup(JmriMouseEvent e) {

            }

            @Override
            protected void setColumnIdentities(JTable table) {
                super.setColumnIdentities(table);
                Enumeration<TableColumn> columns;
                if (table.getColumnModel() instanceof XTableColumnModel) {
                    columns = ((XTableColumnModel) table.getColumnModel()).getColumns(false);
                } else {
                    columns = table.getColumnModel().getColumns();
                }
                while (columns.hasMoreElements()) {
                    TableColumn column = columns.nextElement();
                    switch (column.getModelIndex()) {
                        case SOURCEAPPCOL:
                            column.setIdentifier("SrcAspect");
                            break;
                        case DESTAPPCOL:
                            column.setIdentifier("DstAspect");
                            break;
                        case DELCOL:
                            column.setIdentifier("Delete");
                            break;
                        case EDITLOGICCOL:
                            column.setIdentifier("Edit");
                            break;
                        default:
                        // use existing value
                    }
                }
            }
        };
    }

    @Override
    protected void setTitle() {
        f.setTitle(Bundle.getMessage("TitleSignalMastLogicTable"));
    }

    @Override
    protected String helpTarget() {
        return "package.jmri.jmrit.beantable.SignalMastLogicTable";// NOI18N
    }

    @Override
    protected void addPressed(ActionEvent e) {
        sigLog.setMast(null, null);
        sigLog.actionPerformed(e);
    }

    boolean suppressUpdate = false; // does not update table model changelistener during auto create pairs
    JmriJFrame signalMastLogicFrame = null;
    JLabel sourceLabel = new JLabel();

    void autoCreatePairs(JmriJFrame f) {
        if (!InstanceManager.getDefault(LayoutBlockManager.class).isAdvancedRoutingEnabled()) {
            int response = JOptionPane.showConfirmDialog(f, Bundle.getMessage("EnableLayoutBlockRouting"),
                    Bundle.getMessage("TitleBlockRouting"), JOptionPane.YES_NO_OPTION);
            if (response == 0) {
                InstanceManager.getDefault(LayoutBlockManager.class).enableAdvancedRouting(true);
                JOptionPane.showMessageDialog(f, Bundle.getMessage("LayoutBlockRoutingEnabled"));
            } else {
                return;
            }
        }
        signalMastLogicFrame = new JmriJFrame(Bundle.getMessage("DiscoverSignalMastPairs"), false, false);
        signalMastLogicFrame.setPreferredSize(null);
        JPanel panel1 = new JPanel();
        sourceLabel = new JLabel(Bundle.getMessage("DiscoveringSignalMastPairs"));
        panel1.add(sourceLabel);
        signalMastLogicFrame.add(panel1);
        signalMastLogicFrame.pack();
        signalMastLogicFrame.setVisible(true);

        final JCheckBox genSect = new JCheckBox(Bundle.getMessage("AutoGenSectionAfterLogic"));
        genSect.setToolTipText(Bundle.getMessage("AutoGenSectionAfterLogicToolTip"));
        Object[] params = {Bundle.getMessage("AutoGenSignalMastLogicMessage"), " ", genSect};
        int retval = JOptionPane.showConfirmDialog(f, params, Bundle.getMessage("AutoGenSignalMastLogicTitle"),
                JOptionPane.YES_NO_OPTION);

        if (retval == 0) {
            InstanceManager.getDefault(SignalMastLogicManager.class).addPropertyChangeListener(propertyGenerateListener);
            // This process can take some time, so we do split it off then return to Swing/AWT
            Runnable r = () -> {
                //While the global discovery is taking place we remove the listener as this can result in a race condition.
                suppressUpdate = true;
                try {
                    InstanceManager.getDefault(SignalMastLogicManager.class).automaticallyDiscoverSignallingPairs();
                } catch (JmriException e) {
                    // Notify of problem
                    try {
                        SwingUtilities.invokeAndWait(() -> {
                            InstanceManager.getDefault(SignalMastLogicManager.class).removePropertyChangeListener(propertyGenerateListener);
                            JOptionPane.showMessageDialog(null, e.toString());
                            signalMastLogicFrame.setVisible(false);
                        });
                    } catch (java.lang.reflect.InvocationTargetException ex) {
                        log.error("failed to notify of problem with automaticallyDiscoverSignallingPairs", ex);
                    } catch (InterruptedException ex) {
                        log.error("interrupted while notifying of problem with automaticallyDiscoverSignallingPairs", ex);
                    }
                }

                // process complete, update GUI
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        m.updateNameList();
                        suppressUpdate = false;
                        m.fireTableDataChanged();
                        if (genSect.isSelected()) {
                            ((DefaultSignalMastLogicManager) InstanceManager.getDefault(SignalMastLogicManager.class)).generateSection();
                            InstanceManager.getDefault(SectionManager.class).generateBlockSections();
                        }
                    });
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    log.error("failed to update at end of automaticallyDiscoverSignallingPairs", ex);
                } catch (InterruptedException ex) {
                    log.error("interrupted during update at end of automaticallyDiscoverSignallingPairs", ex);
                }
            };
            Thread thr = ThreadingUtil.newThread(r, "Discover Signal Mast Logic");  // NOI18N
            thr.start();

        } else {
            signalMastLogicFrame.setVisible(false);
        }
    }

    protected transient PropertyChangeListener propertyGenerateListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals("autoGenerateComplete")) {// NOI18N
                if (signalMastLogicFrame != null) {
                    signalMastLogicFrame.setVisible(false);
                }
                InstanceManager.getDefault(SignalMastLogicManager.class).removePropertyChangeListener(this);
                JOptionPane.showMessageDialog(null, Bundle.getMessage("SignalMastPairGenerationComplete"));
            } else if (evt.getPropertyName().equals("autoGenerateUpdate")) {// NOI18N
                sourceLabel.setText((String) evt.getNewValue());
                signalMastLogicFrame.pack();
                signalMastLogicFrame.repaint();
            }
        }
    };

    private final jmri.jmrit.signalling.SignallingAction sigLog = new jmri.jmrit.signalling.SignallingAction();

    @Override
    protected String getClassName() {
        return SignalMastLogicTableAction.class.getName();
    }

    private final static Logger log = LoggerFactory.getLogger(SignalMastLogicTableAction.class);
}
