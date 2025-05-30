package jmri.web.servlet.operations;

import java.io.IOException;
import java.util.*;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jmri.InstanceManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.rollingstock.engines.EngineManager;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;
import jmri.jmrit.operations.trains.Train;
import jmri.jmrit.operations.trains.TrainCommon;
import jmri.util.FileUtil;

/**
 *
 * @author Randall Wood
 */
public class HtmlConductor extends HtmlTrainCommon {

    private final static Logger log = LoggerFactory.getLogger(HtmlConductor.class);

    public HtmlConductor(Locale locale, Train train) throws IOException {
        super(locale, train);
        this.resourcePrefix = "Conductor";  // NOI18N
    }

    public String getLocation() throws IOException {
        RouteLocation location = train.getCurrentRouteLocation();
        if (location == null) {
            return String.format(locale, 
                    FileUtil.readURL(FileUtil.findURL(Bundle.getMessage(locale,"ConductorSnippet.html"))), 
                    train.getIconName(), 
                    StringEscapeUtils.escapeHtml4(train.getDescription()), 
                    StringEscapeUtils.escapeHtml4(train.getComment().replaceAll("\n", "<br>")), 
                    Setup.isPrintRouteCommentsEnabled() ? train.getRoute().getComment() : "", 
                    strings.getProperty("Terminated"), 
                    "", // terminated train has nothing to do // NOI18N
                    "", // engines in separate section
                    "", // pickup=true, local=false
                    "", // pickup=false, local=false
                    "", // pickup=false, local=true
                    "", // engines in separate section
                    "", // terminate with null string, use empty string to indicate terminated
                    strings.getProperty("Terminated"),  // NOI18N
                    train.getStatusCode());
        }

        List<Engine> engineList = InstanceManager.getDefault(EngineManager.class).getByTrainBlockingList(train);
        List<Car> carList = InstanceManager.getDefault(CarManager.class).getByTrainDestinationList(train);
        log.debug("Train has {} cars assigned to it", carList.size());

        String pickups = performWork(true, false); // pickup=true, local=false
        String setouts = performWork(false, false); // pickup=false, local=false
        String localMoves = performWork(false, true); // pickup=false, local=true

        return String.format(locale, 
                FileUtil.readURL(FileUtil.findURL(Bundle.getMessage(locale,"ConductorSnippet.html"))), 
                train.getIconName(), 
                StringEscapeUtils.escapeHtml4(train.getDescription()),
                StringEscapeUtils.escapeHtml4(train.getComment().replaceAll("\n", "<br>")), 
                Setup.isPrintRouteCommentsEnabled() ? train.getRoute().getComment() : "", 
                getCurrentAndNextLocation(),
                getLocationComments().replaceAll("\n", "<br>"),
                pickupEngines(engineList, location), // engines in separate section
                pickups, setouts, localMoves,
                dropEngines(engineList, location), // engines in separate section
                (train.getNextRouteLocation(train.getCurrentRouteLocation()) != null) ? train.getNextLocationName() : null,
                getMoveButton(),
                train.getStatusCode());
    }

    private String getCurrentAndNextLocation() {
        if (train.getCurrentRouteLocation() != null && train.getNextRouteLocation(train.getCurrentRouteLocation()) != null) {
            return String.format(locale, strings.getProperty("CurrentAndNextLocation"), // NOI18N
                    StringEscapeUtils.escapeHtml4(splitString(train.getCurrentLocationName())),
                    StringEscapeUtils.escapeHtml4(splitString(train.getNextLocationName())));
        } else if (train.getCurrentRouteLocation() != null) {
            return StringEscapeUtils.escapeHtml4(splitString(train.getCurrentLocationName()));
        }
        return strings.getProperty("Terminated"); // NOI18N
    }

    private String getMoveButton() {
        if (train.getNextRouteLocation(train.getCurrentRouteLocation()) != null) {
            return String.format(locale, strings.getProperty("MoveTo"), // NOI18N
                    StringEscapeUtils.escapeHtml4(splitString(train.getNextLocationName())));
        } else if (train.getCurrentRouteLocation() != null) {
            return strings.getProperty("Terminate");  // NOI18N
        }
        return strings.getProperty("Terminated");  // NOI18N
    }

    // needed for location comments, not yet in formatter
    private String getEngineChanges(RouteLocation location) {
        // engine change or helper service?
        if (train.getSecondLegOptions() != Train.NO_CABOOSE_OR_FRED) {
            if (location == train.getSecondLegStartRouteLocation()) {
                return engineChange(location, train.getSecondLegOptions());
            }
            if (location == train.getSecondLegEndRouteLocation() && train.getSecondLegOptions() == Train.HELPER_ENGINES) {
                return String.format(strings.getProperty("RemoveHelpersAt"), splitString(location.getName())); // NOI18N
            }
        }
        if (train.getThirdLegOptions() != Train.NO_CABOOSE_OR_FRED) {
            if (location == train.getThirdLegStartRouteLocation()) {
                return engineChange(location, train.getSecondLegOptions());
            }
            if (location == train.getThirdLegEndRouteLocation() && train.getThirdLegOptions() == Train.HELPER_ENGINES) {
                return String.format(strings.getProperty("RemoveHelpersAt"), splitString(location.getName())); // NOI18N
            }
        }
        return "";
    }

    private String getLocationComments() {
        List<Car> carList = InstanceManager.getDefault(CarManager.class).getByTrainDestinationList(train);
        StringBuilder builder = new StringBuilder();
        RouteLocation routeLocation = train.getCurrentRouteLocation();
        boolean work = isThereWorkAtLocation(train, routeLocation.getLocation());

        // print info only if new location
        String routeLocationName = StringEscapeUtils.escapeHtml4(splitString(routeLocation.getName()));
        if (work) {
            if (!train.isShowArrivalAndDepartureTimesEnabled()) {
                builder.append(String.format(locale, strings.getProperty("ScheduledWorkAt"), routeLocationName)); // NOI18N
            } else if (routeLocation == train.getTrainDepartsRouteLocation()) {
                builder.append(String.format(locale, strings.getProperty("WorkDepartureTime"), routeLocationName, train  // NOI18N
                        .getFormatedDepartureTime())); // NOI18N
            } else if (!routeLocation.getDepartureTime().equals("")) {
                builder.append(String.format(locale, strings.getProperty("WorkDepartureTime"), routeLocationName,  // NOI18N
                        routeLocation.getFormatedDepartureTime())); // NOI18N
            } else if (Setup.isUseDepartureTimeEnabled()
                    && routeLocation != train.getTrainTerminatesRouteLocation()
                    && !train.getExpectedDepartureTime(routeLocation).equals(Train.ALREADY_SERVICED)) {
                builder.append(String.format(locale, strings.getProperty("WorkDepartureTime"), routeLocationName, train  // NOI18N
                        .getExpectedDepartureTime(routeLocation)));
            } else if (!train.getExpectedArrivalTime(routeLocation).equals(Train.ALREADY_SERVICED)) {
                builder.append(String.format(locale, strings.getProperty("WorkArrivalTime"), routeLocationName, train  // NOI18N
                        .getExpectedArrivalTime(routeLocation))); // NOI18N
            } else {
                builder.append(String.format(locale, strings.getProperty("ScheduledWorkAt"), routeLocationName)); // NOI18N
            }
            // add route comment
            if (!routeLocation.getComment().trim().equals("")) {
                builder.append(String.format(locale, strings.getProperty("RouteLocationComment"), StringEscapeUtils  // NOI18N
                        .escapeHtml4(routeLocation.getComment())));
            }

            builder.append(getTrackComments(routeLocation, carList));

            // add location comment
            if (Setup.isPrintLocationCommentsEnabled() && !routeLocation.getLocation().getComment().isEmpty()) {
                builder.append(String.format(locale, strings.getProperty("LocationComment"), StringEscapeUtils  // NOI18N
                        .escapeHtml4(routeLocation.getLocation().getComment())));
            }
        }

        // engine change or helper service?
        builder.append(this.getEngineChanges(routeLocation));

        if (routeLocation != train.getTrainTerminatesRouteLocation()) {
            if (work) {
                if (!Setup.isPrintLoadsAndEmptiesEnabled()) {
                    // Message format: Train departs Boston Westbound with 12 cars, 450 feet, 3000 tons
                    builder.append(String.format(strings.getProperty("TrainDepartsCars"), routeLocationName,  // NOI18N
                            routeLocation.getTrainDirectionString(), train.getTrainLength(routeLocation), Setup
                            .getLengthUnit().toLowerCase(), train.getTrainWeight(routeLocation), train
                            .getNumberCarsInTrain(routeLocation)));
                } else {
                    // Message format: Train departs Boston Westbound with 4 loads, 8 empties, 450 feet, 3000 tons
                    int emptyCars = train.getNumberEmptyCarsInTrain(routeLocation);
                    builder.append(String.format(strings.getProperty("TrainDepartsLoads"), routeLocationName,  // NOI18N
                            routeLocation.getTrainDirectionString(), train.getTrainLength(routeLocation), Setup
                            .getLengthUnit().toLowerCase(), train.getTrainWeight(routeLocation), train
                            .getNumberCarsInTrain(routeLocation)
                            - emptyCars, emptyCars));
                }
            } else {
                log.debug("No work ({})", routeLocation.getComment());               
                if (routeLocation.getComment().trim().isEmpty()) {
                    // no route comment, no work at this location
                    if (train.isShowArrivalAndDepartureTimesEnabled()) {
                        if (routeLocation == train.getTrainDepartsRouteLocation()) {
                            builder.append(String.format(locale, strings
                                    .getProperty("NoScheduledWorkAtWithDepartureTime"), routeLocationName, train  // NOI18N
                                    .getFormatedDepartureTime()));
                        } else if (!routeLocation.getDepartureTime().isEmpty()) {
                            builder.append(String.format(locale, strings
                                    .getProperty("NoScheduledWorkAtWithDepartureTime"), routeLocationName,  // NOI18N
                                    routeLocation.getFormatedDepartureTime()));
                        } else {
                            builder.append(String.format(locale, strings.getProperty("NoScheduledWorkAt"),  // NOI18N
                                    routeLocationName));
                        }
                    } else {
                        builder.append(String.format(locale, strings.getProperty("NoScheduledWorkAt"),  // NOI18N
                                routeLocationName));
                    }
                } else {
                    // if a route comment, then only use location name and route comment, useful for passenger
                    // trains
                    if (!routeLocation.getComment().equals(RouteLocation.NONE)) {
                        if (routeLocation.getComment().trim().length() > 0) {
                            builder.append(String.format(locale, strings.getProperty("CommentAt"), // NOI18N
                                    routeLocationName, StringEscapeUtils
                                    .escapeHtml4(routeLocation.getComment())));
                        }
                    }
                    if (train.isShowArrivalAndDepartureTimesEnabled()) {
                        if (routeLocation == train.getTrainDepartsRouteLocation()) {
                            builder.append(String.format(locale, strings
                                    .getProperty("CommentAtWithDepartureTime"), routeLocationName, train // NOI18N
                                    .getFormatedDepartureTime(), StringEscapeUtils
                                    .escapeHtml4(routeLocation.getComment())));
                        } else if (!routeLocation.getDepartureTime().equals(RouteLocation.NONE)) {
                            builder.append(String.format(locale, strings
                                    .getProperty("CommentAtWithDepartureTime"), routeLocationName, // NOI18N
                                    routeLocation.getFormatedDepartureTime(), StringEscapeUtils
                                    .escapeHtml4(routeLocation.getComment())));
                        } else if (Setup.isUseDepartureTimeEnabled() &&
                                !routeLocation.getComment().equals(RouteLocation.NONE)) {
                            builder.append(String.format(locale, strings
                                    .getProperty("NoScheduledWorkAtWithDepartureTime"), routeLocationName, // NOI18N
                                    train.getExpectedDepartureTime(routeLocation)));
                        }
                    }                           
                }
                // add location comment
                if (Setup.isPrintLocationCommentsEnabled() && !routeLocation.getLocation().getComment().isEmpty()) {
                    builder.append(String.format(locale, strings.getProperty("LocationComment"), StringEscapeUtils  // NOI18N
                            .escapeHtml4(routeLocation.getLocation().getComment())));
                }
            }
        } else {
            builder.append(String.format(strings.getProperty("TrainTerminatesIn"), routeLocationName));  // NOI18N
        }
        return builder.toString();
    }

    private String performWork(boolean pickup, boolean local) {
        if (pickup) {
           return pickupCars();
        } else {
            return dropCars(local);
        }
    }

    private String pickupCars() {
        StringBuilder builder = new StringBuilder();
        RouteLocation rlocation = train.getCurrentRouteLocation();
        List<Car> carList = InstanceManager.getDefault(CarManager.class).getByTrainDestinationList(train);
        List<Track> tracks = rlocation.getLocation().getTracksByNameList(null);
        List<String> trackNames = new ArrayList<>();
        List<String> pickedUp = new ArrayList<>();
        this.clearUtilityCarTypes();
        for (Track track : tracks) {
            if (trackNames.contains(splitString(track.getName()))) {
                continue;
            }
            trackNames.add(splitString(track.getName())); // use a track name once
            // block cars by destination
            for (RouteLocation rld : train.getRoute().getLocationsBySequenceList()) {
                for (Car car : carList) {
                    if (pickedUp.contains(car.getId())
                            || (Setup.isSortByTrackNameEnabled() && !splitString(track.getName()).equals(
                                    splitString(car.getTrackName())))) {
                        continue;
                    }
                    if (car.isLocalMove() && rlocation == rld) {
                        continue;
                    }
                    // block pick up cars
                    // caboose or FRED is placed at end of the train
                    // passenger cars are already blocked in the car list
                    // passenger cars with negative block numbers are placed at
                    // the front of the train, positive numbers at the end of
                    // the train.
                    // note that a car in train doesn't have a track assignment
                    if (isNextCar(car, rlocation, rld)) {
                        pickedUp.add(car.getId());
                        if (car.isUtility()) {
                            builder.append(pickupUtilityCars(carList, car, TrainCommon.IS_MANIFEST));
                         // use truncated format if there's a switch list
                        } else if (Setup.isPrintTruncateManifestEnabled() && rlocation.getLocation().isSwitchListEnabled()) {
                            builder.append(pickUpCar(car, Setup.getPickupTruncatedManifestMessageFormat()));
                        } else {
                            builder.append(pickUpCar(car, Setup.getPickupManifestMessageFormat()));
                        }
                    }
                }
            }
        }
        return builder.toString();
    }

    private String dropCars(boolean local) {
        StringBuilder builder = new StringBuilder();
        RouteLocation location = train.getCurrentRouteLocation();
        List<Car> carList = InstanceManager.getDefault(CarManager.class).getByTrainDestinationList(train);
        List<Track> tracks = location.getLocation().getTracksByNameList(null);
        List<String> trackNames = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (Track track : tracks) {
            if (trackNames.contains(splitString(track.getName()))) {
                continue;
            }
            trackNames.add(splitString(track.getName())); // use a track name once
            for (Car car : carList) {
                if (dropped.contains(car.getId())
                        || (Setup.isSortByTrackNameEnabled() && !splitString(track.getName()).equals(
                                splitString(car.getDestinationTrackName())))) {
                    continue;
                }
                if (car.isLocalMove() == local
                        && (car.getRouteDestination() == location && car.getDestinationTrack() != null)) {
                    dropped.add(car.getId());
                    if (car.isUtility()) {
                        builder.append(setoutUtilityCars(carList, car, local));
                    } else {
                        String[] format = (!local) ? Setup.getDropManifestMessageFormat() : Setup
                                .getLocalManifestMessageFormat();
                        builder.append(dropCar(car, format, local));
                    }
                }
            }
        }
        return builder.toString();
    }
}
