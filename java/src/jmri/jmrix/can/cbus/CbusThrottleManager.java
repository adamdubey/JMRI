package jmri.jmrix.can.cbus;

import java.util.*;
import java.awt.GraphicsEnvironment;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import jmri.*;
import jmri.jmrit.throttle.ThrottlesPreferences;
import jmri.jmrix.AbstractThrottleManager;
import jmri.jmrix.can.*;
import jmri.util.TimerUtil;
import jmri.util.ThreadingUtil;

import static jmri.ThrottleListener.DecisionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CBUS implementation of a ThrottleManager.
 *
 * @author Bob Jacobsen Copyright (C) 2001
 * @author Andrew Crosland Copyright (C) 2009
 * @author Steve Young Copyright (C) 2019
 * @author Andrew Crosland Copyright (C) 2021
 */
public class CbusThrottleManager extends AbstractThrottleManager implements CanListener, Disposable {

    private boolean _handleExpected = false;
    private boolean _handleExpectedSecondLevelRequest = false;
    private int _intAddr;
    private DccLocoAddress _dccAddr;
    protected int THROTTLE_TIMEOUT = 5000;
    private boolean canErrorDialogVisible;
    private boolean invalidErrorDialogVisible;
    private boolean _singleThrottleInUse = false; // For single throttle support

    private final HashMap<Integer, CbusThrottle> softThrottles = new HashMap<>(CbusConstants.CBUS_MAX_SLOTS);

    public CbusThrottleManager(CanSystemConnectionMemo memo) {
        super(memo);
        this.memo = memo;
        tc = memo.getTrafficController();
        addTc(tc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        removeTc(tc);
        stopThrottleRequestTimer();
    }

    private final TrafficController tc;
    private final CanSystemConnectionMemo memo;

    /**
     * CBUS allows Throttle sharing, both internally within JMRI and externally by command stations
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected boolean singleUse() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestThrottleSetup(LocoAddress address, boolean control) {
        startThrottleRequestTimer(false);
        requestThrottleSetup(address, DecisionType.STEAL_OR_SHARE);
    }

    /**
     * As this method is called by both throttle recovery and normal throttle creation,
     * methods calling need to start their own timeouts to ensure the correct
     * error message is displayed.
     */
    private void requestThrottleSetup(LocoAddress address, DecisionType decision) {
        if ( !( address instanceof DccLocoAddress)) {
            log.error("{} is not a DccLocoAddress",address);
            return;
        }

        if (memo.hasMultipleThrottles() || !_singleThrottleInUse) {
            _dccAddr = (DccLocoAddress) address;
            _intAddr = _dccAddr.getNumber();

            // The CBUS protocol requires that we request a session from the command
            // station. Throttle object will be notified by Command Station
            log.debug("Requesting {} session for loco {}",decision,_dccAddr);
            if (_dccAddr.isLongAddress()) {
                _intAddr |= 0xC000;
            }
            CanMessage msg;

            switch (decision) {
                case STEAL_OR_SHARE:
                    // 1st line request
                    // Request a session for this throttle normally
                    _handleExpectedSecondLevelRequest = false;
                    msg = new CanMessage(3, tc.getCanid());
                    msg.setOpCode(CbusConstants.CBUS_RLOC);
                    msg.setElement(1, _intAddr / 256);
                    msg.setElement(2, _intAddr & 0xff);
                    break;
                case STEAL:
                    // 2nd line request
                    // Request a Steal session
                    _handleExpectedSecondLevelRequest = true;
                    msg = new CanMessage(4, tc.getCanid());
                    msg.setOpCode(CbusConstants.CBUS_GLOC);
                    msg.setElement(1, _intAddr / 256);
                    msg.setElement(2, _intAddr & 0xff);
                    msg.setElement(3, 0x01); // bit 0 flag set
                    break;
                case SHARE:
                    // 2nd line request
                    // Request a Share session
                    _handleExpectedSecondLevelRequest = true;
                    msg = new CanMessage(4, tc.getCanid());
                    msg.setOpCode(CbusConstants.CBUS_GLOC);
                    msg.setElement(1, _intAddr / 256);
                    msg.setElement(2, _intAddr & 0xff);
                    msg.setElement(3, 0x02); // bit 1 flag set
                    break;
                default:
                    log.error("decision type {} unknown to CbusThrottleManager",decision);
                    return;
            }

            // send the request to layout
            _handleExpected = true;
            tc.sendCanMessage(msg, this);
        } else {
            failedThrottleRequest(address, "Only one Throttle can be in use at anyone time with this connection.");
            log.warn("Single CBUS Throttle already in use");
        }
    }

    /**
     * stopAll()
     *
     * <p>
     * Called when track stopped message received. Sets all JMRI managed
     * throttles to speed zero
     */
    private void stopAll() {
        // Get set of handles for JMRI managed throttles and
        // iterate over them setting the speed of each throttle to 0
        // log.info("stopAll() setting all speeds to emergency stop");
        for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
            CbusThrottle throttle = entry.getValue();
            throttle.setSpeedSetting(-1.0f);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void message(CanMessage m) {
        if ( m.extendedOrRtr() ) {
            return;
        }
        int opc = m.getElement(0);
        int handle;
        switch (opc) {
            case CbusConstants.CBUS_ESTOP:
            case CbusConstants.CBUS_RESTP:
                stopAll();
                break;
            case CbusConstants.CBUS_KLOC: // Kill loco
                log.debug("Kill loco message");
                // Find a throttle corresponding to the handle
                handle = m.getElement(1);
                for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                    CbusThrottle throttle = entry.getValue();
                    if (throttle.getHandle() == handle) {
                        // Remove the Throttle from the managed list
                        softThrottles.remove(throttle.getHandle());
                    }
                }
                _singleThrottleInUse = false;
                break;
            case CbusConstants.CBUS_DSPD:
                // only if emergency stop
                if ((m.getElement(2) & 0x7f) == 1) {
                    // Find a throttle corresponding to the handle
                    handle = m.getElement(1);
                    for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                        CbusThrottle throttle = entry.getValue();
                        if (throttle.getHandle() == handle) {
                            // Set the throttle session to match the DSPD packet
                            throttle.updateSpeedSetting(m.getElement(2) & 0x7f);
                            throttle.updateIsForward((m.getElement(2) & 0x80) == 0x80);
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"SLF4J_SIGN_ONLY_FORMAT", "SLF4J_FORMAT_SHOULD_BE_CONST"})
        // justification="I18N of log message")
    @Override
    public void reply(CanReply m) {
        if ( m.extendedOrRtr() ) {
            return;
        }
        int opc = m.getElement(0);
        int handle = m.getElement(1);

        switch (opc) {
            case CbusConstants.CBUS_PLOC:
                int rcvdIntAddr = (m.getElement(2) & 0x3f) * 256 + m.getElement(3);
                boolean rcvdIsLong = (m.getElement(2) & 0xc0) != 0;
                DccLocoAddress rcvdDccAddr = new DccLocoAddress(rcvdIntAddr, rcvdIsLong);
                log.debug("Throttle manager received PLOC with session {} for address {}",m.getElement(1),rcvdIntAddr);
                if ((_handleExpected) && rcvdDccAddr.equals(_dccAddr)) {
                    log.debug("PLOC was expected");
                    // We're expecting an engine report and it matches our address
                    stopThrottleRequestTimer();
                    handle = m.getElement(1);
                    if (!memo.hasMultipleThrottles()) {
                        _singleThrottleInUse = true;
                    }

                    // check if the PLOC has come from a throttle session cancel notification
                    for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                        CbusThrottle throttle = entry.getValue();
                        if (throttle.isStolen()) {
                            log.debug("setting handle from {} to {}",throttle.getHandle(),handle);
                            throttle.setHandle(handle);
                            // uses timeout to help prevent steal loops
                           // jmri.util.ThreadingUtil.runOnLayoutDelayed( () -> {
                                throttle.setStolen(false); // sends the reactivation PCL
                           // },500 );
                            throttle.throttleInit(m.getElement(4), m.getElement(5), m.getElement(6), m.getElement(7));
                            _handleExpected = false;
                            return;
                        }
                    }

                    // Initialise new throttle from PLOC data to allow taking over moving trains
                    CbusThrottle throttle = new CbusThrottle((CanSystemConnectionMemo) adapterMemo, rcvdDccAddr, handle);
                    notifyThrottleKnown(throttle, rcvdDccAddr);
                    throttle.throttleInit(m.getElement(4), m.getElement(5), m.getElement(6), m.getElement(7));
                    softThrottles.put(handle, throttle);
                    _handleExpected = false;
                }
                break;
            case CbusConstants.CBUS_ERR:
                handleErr(m);
                break;
            case CbusConstants.CBUS_DSPD:
                // Find a throttle corresponding to the handle
                for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                    CbusThrottle throttle = entry.getValue();
                    if (throttle.getHandle() == handle) {
                        // Set the throttle session to match the DSPD packet received
                        throttle.updateSpeedSetting(m.getElement(2) & 0x7f);
                        throttle.updateIsForward((m.getElement(2) & 0x80) == 0x80);
                        // if something external to JMRI is sharing a session
                        // dispatch is invalid
                        throttle.setDispatchActive(false);
                    }
                }
                break;

            case CbusConstants.CBUS_DFUN:
                // Find a throttle corresponding to the handle
                for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                    CbusThrottle throttle = entry.getValue();
                    if (throttle.getHandle() == handle) {
                        // if something external to JMRI is sharing a session
                        // dispatch is invalid
                        throttle.setDispatchActive(false);
                        throttle.updateFunctionGroup(m.getElement(2),m.getElement(3));
                    }
                }
                break;

            case CbusConstants.CBUS_DFNON:
            case CbusConstants.CBUS_DFNOF:
                // Find a throttle corresponding to the handle
                for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                    CbusThrottle throttle = entry.getValue();
                    if (throttle.getHandle() == handle) {
                        // dispatch is invalid if something external to JMRI is sharing a session
                        throttle.setDispatchActive(false);
                        throttle.updateFunction(m.getElement(2), (opc == CbusConstants.CBUS_DFNON));
                    }
                }
                break;

            case CbusConstants.CBUS_ESTOP:
            case CbusConstants.CBUS_RESTP:
                stopAll();
                break;
            case CbusConstants.CBUS_DKEEP:
                for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                    CbusThrottle throttle = entry.getValue();
                    if (throttle.getHandle() == handle) {
                        // if something external to JMRI is sharing a session
                        // dispatch is invalid
                        throttle.setDispatchActive(false);
                    }
                }
                break;
            default:
                break;
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="SLF4J_SIGN_ONLY_FORMAT",
                                                        justification="I18N of log message")
    private void handleErr(CanReply m) {
        int handle = m.getElement(1);
        int rcvdIntAddr = (m.getElement(1) & 0x3f) * 256 + m.getElement(2);
        boolean rcvdIsLong = (m.getElement(1) & 0xc0) != 0;
        // DccLocoAddress rcvdDccAddr = new DccLocoAddress(rcvdIntAddr, rcvdIsLong);
        int errCode = m.getElement(3);

        boolean responseForUs = ((_handleExpected) && new DccLocoAddress(rcvdIntAddr, rcvdIsLong).equals(_dccAddr));

        switch (errCode) {
            case CbusConstants.ERR_LOCO_STACK_FULL:
            case CbusConstants.ERR_LOCO_ADDRESS_TAKEN:

                String errStr;
                if ( errCode == CbusConstants.ERR_LOCO_STACK_FULL ){
                    errStr = Bundle.getMessage("ERR_LOCO_STACK_FULL") + " " + rcvdIntAddr;
                } else {
                    errStr = Bundle.getMessage("ERR_LOCO_ADDRESS_TAKEN", rcvdIntAddr);
                }

                // log.debug("handlexpected {} _dccAddr {} got {} ", _handleExpected, _dccAddr, rcvdDccAddr);

                if (responseForUs) { // We were expecting an engine report and it matches our address
                    log.debug("Failed throttle request due to ERR");
                    _handleExpected = false;
                    stopThrottleRequestTimer();

                    // if this is the result of a share or steal request,
                    // we need to stop here and inform the ThrottleListener
                    if ( _handleExpectedSecondLevelRequest ){
                        failedThrottleRequest(_dccAddr, errStr);
                        return;
                    }

                    // so this is the message from the 1st normal request
                    // now we check the command station,
                    // and notify the ThrottleListener ()

                    boolean steal = false;
                    boolean share = false;

                    CbusCommandStation cs = (CbusCommandStation) memo.get(CommandStation.class);

                    if ( cs != null ) {
                        log.debug("cs says can steal {}, can share {}", cs.isStealAvailable(), cs.isShareAvailable() );
                        steal = cs.isStealAvailable();
                        share = cs.isShareAvailable();
                    }

                    if ( !steal && !share ){
                        failedThrottleRequest(_dccAddr, errStr);
                    }
                    else if ( steal && share ){
                        notifyDecisionRequest(_dccAddr, DecisionType.STEAL_OR_SHARE);
                    }
                    else if ( steal ){
                        notifyDecisionRequest(_dccAddr, DecisionType.STEAL);
                    }
                    else { // must be share
                        notifyDecisionRequest(_dccAddr, DecisionType.SHARE);
                    }
                } else {
                    log.debug("ERR address not matched");
                }
                break;

            case CbusConstants.ERR_SESSION_NOT_PRESENT:
                // most likely called via a command station being reset or
                // coming back online
                log.warn("{}",Bundle.getMessage("ERR_SESSION_NOT_PRESENT",handle));

                if (responseForUs) {
                    // We were expecting an engine report and it matches our address
                    _handleExpected = false;
                    failedThrottleRequest(_dccAddr, Bundle.getMessage("CBUS_ERROR")
                        + Bundle.getMessage("ERR_SESSION_NOT_PRESENT",handle));
                    log.warn("Session not present when expecting a session number");
                }

                // check if it's a JMRI throttle session,
                // Inform the throttle associated with this session handle, if any
                for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                    CbusThrottle throttle = entry.getValue();
                    if (throttle.getHandle() == handle) {
                        log.warn("Cancelling JMRI Throttle Session {} for loco {}",
                            handle,
                            throttle.getLocoAddress().toString()
                        );
                        attemptRecoverThrottle(throttle);
                        break;
                    }
                }
                break;
            case CbusConstants.ERR_CONSIST_EMPTY:
                log.warn("{} {}",Bundle.getMessage("ERR_CONSIST_EMPTY"), handle);
                break;
            case CbusConstants.ERR_LOCO_NOT_FOUND:
                log.warn("{} {}", Bundle.getMessage("ERR_LOCO_NOT_FOUND"), handle);
                break;
            case CbusConstants.ERR_CAN_BUS_ERROR:
                log.error("{}",Bundle.getMessage("ERR_CAN_BUS_ERROR"));
                if (!GraphicsEnvironment.isHeadless() && !canErrorDialogVisible ) {

                    ThreadingUtil.runOnGUI(() -> {
                        canErrorDialogVisible = true;
                        JOptionPane pane = new JOptionPane(Bundle.getMessage("ERR_CAN_BUS_ERROR"));
                        pane.setMessageType(JOptionPane.ERROR_MESSAGE);
                        JDialog canErrorDialog = pane.createDialog(null, Bundle.getMessage("CBUS_ERROR"));

                        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, ignored -> {
                            canErrorDialog.dispose();
                            canErrorDialogVisible = false;
                        });


                        canErrorDialog.setModal(false);
                        canErrorDialog.setVisible(true);
                    });
                }
                return;
            case CbusConstants.ERR_INVALID_REQUEST:
                log.error("{}", Bundle.getMessage("ERR_INVALID_REQUEST"));
                if (!GraphicsEnvironment.isHeadless() && !invalidErrorDialogVisible){
                    ThreadingUtil.runOnGUI(() -> {
                        invalidErrorDialogVisible = true;
                        JOptionPane pane = new JOptionPane(Bundle.getMessage("ERR_INVALID_REQUEST"));
                        pane.setMessageType(JOptionPane.ERROR_MESSAGE);
                        JDialog invalidErrorDialog = pane.createDialog(null, Bundle.getMessage("CBUS_ERROR"));
                        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, ignored -> {
                            invalidErrorDialog.dispose();
                            invalidErrorDialogVisible = false;
                        });
                        invalidErrorDialog.setModal(false);
                        invalidErrorDialog.setVisible(true);
                    });
                }
                return;
            case CbusConstants.ERR_SESSION_CANCELLED:
                // There will be a session cancelled error for the other throttle(s)
                // when you are stealing, but as you don't yet have a session id, it
                // won't match so you will ignore it, then a PLOC will come with that
                // session id and your requested loco number which is giving it to you.

                log.debug("{}", Bundle.getMessage("ERR_SESSION_CANCELLED",handle));

                // Inform the throttle associated with this session handle, if any
                for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                    CbusThrottle throttle = entry.getValue();
                    if (throttle.getHandle() == handle) {
                        if (throttle.isStolen()){ // already actioned
                            log.debug("external steal already actioned, returning");
                            return;
                        }
                        log.warn("External Steal / Cancel for loco {} Session {} ",throttle.getLocoAddress(), handle );
                        attemptRecoverThrottle(throttle);
                        break;
                    }
                }
                break;
            default:
                log.error("{} error code: {}", Bundle.getMessage("ERR_UNKNOWN"), errCode);
                break;
        }
    }

    /**
     * Attempts Throttle Recovery when a session has been lost
     */
    private void attemptRecoverThrottle(CbusThrottle throttle){

        log.debug("start of recovery, current throttle stolen {} session {} num recovr attempts {} hashmap size {}",
            throttle.isStolen(), throttle.getHandle(), throttle.getNumRecoverAttempts(),
            softThrottles.size() );

        int oldhandle = throttle.getHandle();

        throttle.increaseNumRecoverAttempts();

        if (throttle.getNumRecoverAttempts() > 10) { // catch runaways
            _handleExpected = false;
            throttle.throttleDispose(); // stop throttle keep-alive messages, send PCL ThrottleConnected false
            showSessionCancelDialogue(throttle.getLocoAddress());
            softThrottles.remove(oldhandle); // remove from local list
            forceDisposeThrottle( throttle.getLocoAddress() ); // remove from JMRI share list
        }

        throttle.setStolen(true);
        throttle.setHandle(-1);

        boolean steal = false;
        boolean share = false;

        CbusCommandStation cs = (CbusCommandStation) memo.get(CommandStation.class);
        if ( cs != null ) {
            log.debug("cs says can steal {}, can share {}", cs.isStealAvailable(), cs.isShareAvailable() );
            steal = cs.isStealAvailable();
            share = cs.isShareAvailable();
        }

        if (share && InstanceManager.getDefault(ThrottlesPreferences.class).isSilentShare()){
            // share is available on command station AND silent share preference checked
            log.info("Requesting Silent Share loco {} to regain a session",throttle.getLocoAddress());
            ThreadingUtil.runOnLayoutDelayed( () -> {
                startThrottleRequestTimer(true);
                requestThrottleSetup(throttle.getLocoAddress(), DecisionType.SHARE);
            },1000);
        }
        else if (steal && InstanceManager.getDefault(ThrottlesPreferences.class).isSilentSteal()) {
            // steal is available on command station AND silent steal preference checked
            log.info("Requesting Silent Steal loco {} to regain a session",throttle.getLocoAddress());
            ThreadingUtil.runOnLayoutDelayed( () -> {
                startThrottleRequestTimer(true);
                requestThrottleSetup(throttle.getLocoAddress(), DecisionType.STEAL);
            },1000);
        } else {
            throttle.throttleDispose(); // stop throttle keep-alive messages, send PCL ThrottleConnected false
            showSessionCancelDialogue(throttle.getLocoAddress());
            softThrottles.remove(oldhandle); // remove from local list
            forceDisposeThrottle( throttle.getLocoAddress() ); // remove from JMRI share list
        }
    }

    /**
     * CBUS has a dynamic Dispatch function, defaulting to false
     * {@inheritDoc}
     */
    @Override
    public boolean hasDispatchFunction() {
        return false;
    }

    /**
     * Any address is potentially a long address.
     * {@inheritDoc}
     */
    @Override
    public boolean canBeLongAddress(int address) {
        return address > 0;
    }

    /**
     * Address 127 and below is a short address.
     * {@inheritDoc}
     */
    @Override
    public boolean canBeShortAddress(int address) {
        return address < 128;
    }

    /**
     * Short and long address spaces overlap and are not unique.
     * @return always false.
     * {@inheritDoc}
     */
    @Override
    public boolean addressTypeUnique() {
        return false;
    }

    /**
     * Local method for deciding short/long address.
     * @param num the address number
     * @return true if address equal to or greater than 128.
     */
    static boolean isLongAddress(int num) {
        return (num >= 128);
    }

    /**
     * Hardware has a stealing implementation.
     * {@inheritDoc}
     */
    @Override
    public boolean enablePrefSilentStealOption() {
        return true;
    }

    /**
     * Hardware has a sharing implementation.
     * {@inheritDoc}
     */
    @Override
    public boolean enablePrefSilentShareOption() {
        return true;
    }

    /**
     * CBUS Hardware will make its own decision on preferred option.
     * <p>
     * This is the default for scripts that do NOT have a GUI for asking what to do when
     * a steal / share decision is required.
     * {@inheritDoc}
     */
    @Override
    protected void makeHardwareDecision(LocoAddress address,DecisionType question){
        // no need to check if share / steal currently enabled on command station,
        // this has already been done to produce the correct question
        switch (question) {
            case STEAL:
                // share has been disabled in command station
                responseThrottleDecision(address, null, DecisionType.STEAL );
                break;
            case SHARE:
                // steal has been disabled in command station
                responseThrottleDecision(address, null, DecisionType.SHARE );
                break;
            default: // case STEAL_OR_SHARE:
                if ( InstanceManager.getDefault(ThrottlesPreferences.class).isSilentSteal() ){
                    responseThrottleDecision(address, null, DecisionType.STEAL );
                }
                else {
                    responseThrottleDecision(address, null, DecisionType.SHARE );
                }
                break;
        }
    }

    /**
     * Send a request to steal or share a requested throttle.
     * <p>
     * {@inheritDoc}
     *
     */
    @Override
    public void responseThrottleDecision(LocoAddress address, ThrottleListener l, DecisionType decision) {
        log.debug("Received {} response for Loco {}, listener {}",decision,address,l);
        startThrottleRequestTimer(false);
        requestThrottleSetup(address,decision);
    }

    private TimerTask throttleRequestTimer;

    /**
     * Start timer to wait for command station to respond to RLOC or GLOC
     */
    private void startThrottleRequestTimer(boolean isRecovery) {
        throttleRequestTimer = new TimerTask() {
            @Override
            public void run() {
                timeout(isRecovery);
            }
        };
        TimerUtil.schedule(throttleRequestTimer, ( THROTTLE_TIMEOUT ) );
    }

    private void stopThrottleRequestTimer(){
        if (throttleRequestTimer!=null){
            throttleRequestTimer.cancel();
        }
        throttleRequestTimer = null;
    }

    /**
     * Internal routine to notify failed throttle request a timeout
     */
    private void timeout(boolean isRecovery) {
        log.debug("Throttle request (RLOC or PLOC) timed out");
        stopThrottleRequestTimer();
        if (isRecovery){
            log.warn("Session recovery not possible for {}",_dccAddr);
            forceDisposeThrottle( _dccAddr ); // remove from JMRI share list

            for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
                CbusThrottle throttle = entry.getValue();
                if (throttle.getLocoAddress() == _dccAddr) {
                    throttle.throttleDispose();
                    showSessionCancelDialogue(_dccAddr);
                    softThrottles.remove(throttle.getHandle());
                }
            }
        }
        else { // not in recovery, normal request timeout, is command station connected?
            failedThrottleRequest(_dccAddr, Bundle.getMessage("ERR_THROTTLE_TIMEOUT"));
        }
    }

    /**
     * MERG CBUS Throttle sessions default to 128 SS.
     * This can be changed by a subsequent message from Throttle to CS,
     * or by message from Command Station to CbusThrottle.
     * Supported modes are 128, 28 and 14.
     * {@inheritDoc }
     */
    @Override
    public EnumSet<SpeedStepMode> supportedSpeedModes() {
        return EnumSet.of(SpeedStepMode.NMRA_DCC_128
                , SpeedStepMode.NMRA_DCC_28
                , SpeedStepMode.NMRA_DCC_14);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disposeThrottle(DccThrottle t, ThrottleListener l) {
        log.debug("disposeThrottle called for {}", t);
        if (t instanceof CbusThrottle) {
            log.debug("Cbus Dispose calling abstract Throttle manager dispose");
            if (super.disposeThrottle(t, l)) {

                CbusThrottle lnt = (CbusThrottle) t;
                lnt.releaseFromCommandStation();
                lnt.throttleDispose();
                // forceDisposeThrottle( (DccLocoAddress) lnt.getLocoAddress() );
                log.debug("called throttleDispose");
                _singleThrottleInUse = false;
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void forceDisposeThrottle(LocoAddress la) {
        super.forceDisposeThrottle(la);
        _singleThrottleInUse = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateNumUsers( LocoAddress la, int numUsers ){
        log.debug("throttle {} notification that num. users is now {}",la,numUsers);
        for (Map.Entry<Integer, CbusThrottle> entry : softThrottles.entrySet()) {
            CbusThrottle throttle = entry.getValue();
            if (throttle.getLocoAddress() == la) {
                if ((numUsers == 1) && throttle.getSpeedSetting() > 0) {
                    throttle.setDispatchActive(true);
                    return;
                }
                throttle.setDispatchActive(false);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelThrottleRequest(LocoAddress address, ThrottleListener l) {

        // calling super removes the ThrottleListener from the callback list,
        // The listener which has just sent the cancel doesn't need notification
        // of the cancel but other listeners might
        super.cancelThrottleRequest(address, l);
        failedThrottleRequest(address, "Throttle Request " + address + " Cancelled.");
    }

    private final static Logger log = LoggerFactory.getLogger(CbusThrottleManager.class);
}
