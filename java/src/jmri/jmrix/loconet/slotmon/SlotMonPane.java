package jmri.jmrix.loconet.slotmon;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import jmri.InstanceManager;
import jmri.jmrix.loconet.LnConstants;
import jmri.swing.JmriJTablePersistenceManager;
import jmri.util.table.*;

/**
 * Frame providing a command station slot manager.
 * <p>
 * Slots 102 through 127 are normally not used for loco control, so are shown
 * separately.
 *
 * @author Bob Jacobsen Copyright (C) 2001
 */
public class SlotMonPane extends jmri.jmrix.loconet.swing.LnPanel {

    /**
     * Controls whether not-in-use slots are shown
     */
    protected final JCheckBox showUnusedCheckBox = new JCheckBox();
    /**
     * Controls whether system slots (0, 121-127) are shown
     */
    protected final JCheckBox showSystemCheckBox = new JCheckBox();

    private final JButton estopAllButton = new JButton(Bundle.getMessage("ButtonSlotMonEStopAll"));

    //Added by Jeffrey Machacek 2013
    private final JButton clearAllButton = new JButton(Bundle.getMessage("ButtonSlotMonClearAll"));
    private final JButton refreshAllButton = new JButton(Bundle.getMessage("ButtonSlotRefresh"));

    private SlotMonDataModel slotModel;
    private JTable slotTable;
    private JScrollPane slotScroll;
    private transient TableRowSorter<SlotMonDataModel> sorter;

    public SlotMonPane() {
        super();
    }

    @Override
    public void initComponents(jmri.jmrix.loconet.LocoNetSystemConnectionMemo memo) {
        super.initComponents(memo);

        slotModel = new SlotMonDataModel(128, 16, memo);
        slotTable = new JTable(slotModel);
        slotTable.setName(this.getTitle());
        sorter = new TableRowSorter<>(slotModel);
        slotTable.setRowSorter(sorter);
        slotScroll = new JScrollPane(slotTable);

        // configure items for GUI
        showUnusedCheckBox.setText(Bundle.getMessage("TextSlotMonShowUnused"));
        showUnusedCheckBox.setVisible(true);
        showUnusedCheckBox.setSelected(false);
        showUnusedCheckBox.setToolTipText(Bundle.getMessage("TooltipSlotMonShowUnused"));

        showSystemCheckBox.setText(Bundle.getMessage("TextSlotMonShowSystem"));
        showSystemCheckBox.setVisible(true);
        showSystemCheckBox.setSelected(false);
        showSystemCheckBox.setToolTipText(Bundle.getMessage("TooltipSlotMonShowSystem"));

        // allow reordering of the columns
        slotTable.getTableHeader().setReorderingAllowed(true);

        // shut off autoResizeMode to get horizontal scroll to work (JavaSwing p 541)
        slotTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // resize columns as requested
        for (int i = 0; i < slotTable.getColumnCount(); i++) {
            int width = slotModel.getPreferredWidth(i);
            slotTable.getColumnModel().getColumn(i).setPreferredWidth(width);
        }
        slotTable.sizeColumnsToFit(-1);

        InstanceManager.getOptionalDefault(JmriJTablePersistenceManager.class).ifPresent((tpm) -> {
            // unable to persist because Default class provides no mechanism to
            // ensure window is destroyed when closed or that existing window is
            // reused when hidden and user reopens it from menu
            // tpm.persist(slotTable, true);
        });

        // install a button renderer & editor in the "DISP" column for freeing a slot
        setColumnToHoldButton(slotTable, slotTable.convertColumnIndexToView(SlotMonDataModel.DISPCOLUMN));

        // install a button renderer & editor in the "ESTOP" column for stopping a loco
        setColumnToHoldEStopButton(slotTable, slotTable.convertColumnIndexToView(SlotMonDataModel.ESTOPCOLUMN));

        // Install a numeric format for ConsistAddress
        setColumnForBlankWhenZero(slotTable, slotTable.convertColumnIndexToView(SlotMonDataModel.CONSISTADDRESS));
        // add listener object so checkboxes function

        refreshAllButton.addActionListener((ActionEvent e) -> {
            slotModel.refreshSlots();
        });

        showUnusedCheckBox.addActionListener((ActionEvent e) -> {
            filter();
        });
        showSystemCheckBox.addActionListener((ActionEvent e) -> {
            filter();
        });

        // add listener object so stop all button functions
        estopAllButton.addActionListener((ActionEvent e) -> {
            slotModel.estopAll();
        });

        //Jeffrey 6/29/2013
        clearAllButton.addActionListener((ActionEvent e) -> {
            slotModel.clearAllSlots();
        });

        // adjust model to default settings
        filter();

        // general GUI config
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // install items in GUI
        JPanel pane1 = new JPanel();
        pane1.setLayout(new FlowLayout());

        pane1.add(refreshAllButton);
        pane1.add(showUnusedCheckBox);
        pane1.add(showSystemCheckBox);
        pane1.add(estopAllButton);
        pane1.add(clearAllButton);

        add(pane1);
        add(slotScroll);

        // set scroll size
        //pane1.setMaximumSize(new java.awt.Dimension(100,300));
        if (pane1.getMaximumSize().height > 0 && pane1.getMaximumSize().width > 0) {
            pane1.setMaximumSize(pane1.getPreferredSize());
        }
    }

    void setColumnToHoldButton(JTable slotTable, int column) {
        TableColumnModel tcm = slotTable.getColumnModel();
        // install the button renderers & editors in this column
        ButtonRenderer buttonRenderer = new ButtonRenderer();
        tcm.getColumn(column).setCellRenderer(buttonRenderer);
        // ensure the table rows, columns have enough room for buttons
        TableCellEditor buttonEditor = new ButtonEditor(new JButton());
        slotTable.setDefaultEditor(JButton.class, buttonEditor);
        slotTable.setRowHeight(new JButton("  " + slotModel.getValueAt(1, column)).getPreferredSize().height);
        slotTable.getColumnModel().getColumn(column)
                .setPreferredWidth(new JButton("  " + slotModel.getValueAt(1, column)).getPreferredSize().width);
    }

    /*
     * Helper class to format number and optionally make blank when zero
     */
    private static class NumberFormatRenderer extends DefaultTableCellRenderer
    {
        public NumberFormatRenderer(String pattern, boolean suppressZero) {
            super();
            this.pattern = pattern;
            this.suppressZero = suppressZero;
            setHorizontalAlignment(JLabel.RIGHT);
        }
        @Override
        public void setValue(Object value)
        {
            try
            {
                if (value != null && value instanceof Number) {
                    if (suppressZero && ((Number) value).doubleValue() == 0.0 ) {
                        value = "";
                    }
                    NumberFormat formatter = new DecimalFormat(pattern);
                    value = formatter.format(value);
                }
            }
            catch(IllegalArgumentException e) {}
            super.setValue(value);
        }
        private String pattern;
        private boolean suppressZero;
    }

    void setColumnForBlankWhenZero(JTable slotTable, int column) {
        TableColumnModel tcm = slotTable.getColumnModel();
        TableCellRenderer renderer = new NumberFormatRenderer("####",true);
        tcm.getColumn(column).setCellRenderer(renderer);
    }

    void setColumnToHoldEStopButton(JTable slotTable, int column) {
        TableColumnModel tcm = slotTable.getColumnModel();
        // install the button renderers & editors in this column
        ButtonRenderer buttonRenderer = new ButtonRenderer();
        tcm.getColumn(column).setCellRenderer(buttonRenderer);
        // ensure the table rows, columns have enough room for buttons
        TableCellEditor buttonEditor = new ButtonEditor(new JButton());
        slotTable.setDefaultEditor(JButton.class, buttonEditor);
        slotTable.setRowHeight(new JButton("  " + slotModel.getValueAt(1, column)).getPreferredSize().height);
        slotTable.getColumnModel().getColumn(column)
                .setPreferredWidth(new JButton("  " + slotModel.getValueAt(1, column)).getPreferredSize().width);
    }

    @Override
    public String getHelpTarget() {
        return "package.jmri.jmrix.loconet.slotmon.SlotMonFrame"; // NOI18N
    }

    @Override
    public String getTitle() {
        return getTitle(Bundle.getMessage("MenuItemSlotMonitor"));
    }

    @Override
    public void dispose() {
        slotModel.dispose();
        slotModel = null;
        slotTable = null;
        slotScroll = null;
        super.dispose();
    }

    private void filter() {
        RowFilter<SlotMonDataModel, Integer> rf = new RowFilter<SlotMonDataModel, Integer>() {
            @Override
            public boolean include(RowFilter.Entry<? extends SlotMonDataModel, ? extends Integer> entry) {
                int slotNum = entry.getIdentifier();
                // default filter is IN-USE and regular systems slot
                boolean include = entry.getModel().getSlot(entry.getIdentifier()).slotStatus() != LnConstants.LOCO_FREE && (slotNum > 0 && slotNum < 121);

                if (!include && showUnusedCheckBox.isSelected() && (slotNum > 0 && slotNum < 121)) {
                    include = true;
                }
                if (!include && showSystemCheckBox.isSelected() && (slotNum == 0 || slotNum > 120)) {
                    include = true;
                }
                return include;
            }
        };
        sorter.setRowFilter(rf);
    }

    @Override
    public List<JMenu> getMenus() {
        List<JMenu> menuList = new ArrayList<>();
        menuList.add(getFileMenu());
        return menuList;
    }

    private JMenu getFileMenu(){
        JMenu fileMenu = new JMenu(Bundle.getMessage("MenuFile")); // NOI18N
        fileMenu.add(new JTableToCsvAction((Bundle.getMessage("ExportCsvAll")),
            null, slotModel, "Slot_Monitor_All.csv", new int[]{
            SlotMonDataModel.ESTOPCOLUMN})); // NOI18N
        fileMenu.add(new JTableToCsvAction((Bundle.getMessage("ExportCsvView")),
            slotTable, slotModel, "Slot_Monitor_View.csv", new int[]{
            SlotMonDataModel.ESTOPCOLUMN})); // NOI18N
        return fileMenu;
    }

}
