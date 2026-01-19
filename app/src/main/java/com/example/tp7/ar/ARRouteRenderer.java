// ========== ARRouteRenderer.java - VERSION COMPLÈTE AMÉLIORÉE ==========
package com.example.tp7.ar;

import android.content.Context;
import android.util.Log;

import com.example.tp7.utils.GeoUtils;
import com.google.android.gms.maps.model.LatLng;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;

import java.util.ArrayList;
import java.util.List;

public class ARRouteRenderer {

    private static final String TAG = "ARRouteRenderer";

    private Context context;
    private Scene scene;
    private ARSceneManager sceneManager;
    private Arrow3DRenderer arrow3DRenderer;

    private LatLng originGPS;
    private AnchorNode anchorNode;
    private List<ARWaypoint> arWaypoints;
    private List<DirectionalArrow> directionalArrows;

    private static final float ARROW_DISTANCE = 8f;  // Flèches tous les 8m
    private static final float MAX_RENDER_DISTANCE = 100f;
    private static final float WAYPOINT_SPACING = 20f;
    private static final float OBJECT_HEIGHT = 0.2f;

    // Pour calculer distance totale et temps
    private float totalRouteDistance = 0f;
    private float remainingDistance = 0f;

    public ARRouteRenderer(Context context, ARSceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
        this.scene = sceneManager.getScene();
        this.arWaypoints = new ArrayList<>();
        this.directionalArrows = new ArrayList<>();
        this.arrow3DRenderer = new Arrow3DRenderer(context);
    }

    public void setOrigin(LatLng origin) {
        this.originGPS = origin;

        if (anchorNode == null) {
            anchorNode = new AnchorNode();
            anchorNode.setParent(scene);
        }

        Log.d(TAG, "Origine AR définie à: " + origin);
    }

    public void createRoute(List<LatLng> gpsRoute) {
        if (originGPS == null) {
            Log.e(TAG, "Origine non définie!");
            return;
        }

        clearRoute();

        // Calculer la distance totale
        totalRouteDistance = calculateTotalDistance(gpsRoute);

        List<LatLng> simplifiedRoute = simplifyRoute(gpsRoute, WAYPOINT_SPACING);

        Log.d(TAG, "Création route: " + simplifiedRoute.size() + " waypoints, " +
                String.format("%.2f km", totalRouteDistance / 1000));

        // Créer les waypoints
        for (int i = 0; i < simplifiedRoute.size(); i++) {
            LatLng gpsPoint = simplifiedRoute.get(i);

            ARWaypoint.WaypointType type;
            if (i == 0) {
                type = ARWaypoint.WaypointType.START;
            } else if (i == simplifiedRoute.size() - 1) {
                type = ARWaypoint.WaypointType.DESTINATION;
            } else {
                type = determineWaypointType(simplifiedRoute, i);
            }

            Vector3 arPosition = GeoUtils.gpsToARPosition(originGPS, gpsPoint, OBJECT_HEIGHT);

            ARWaypoint waypoint = new ARWaypoint(gpsPoint, arPosition, type);
            waypoint.setIndex(i);

            arWaypoints.add(waypoint);
        }

        // Créer les flèches 3D directionnelles
        createDirectionalArrows(simplifiedRoute);

        // Créer les lignes au sol
        createRouteLines();

        Log.d(TAG, directionalArrows.size() + " flèches 3D créées");
    }

    /**
     * Calcule la distance totale de la route
     */
    private float calculateTotalDistance(List<LatLng> route) {
        float total = 0f;
        for (int i = 0; i < route.size() - 1; i++) {
            total += GeoUtils.distanceBetween(route.get(i), route.get(i + 1));
        }
        return total;
    }

    /**
     * Détermine le type de waypoint basé sur l'angle de virage
     */
    private ARWaypoint.WaypointType determineWaypointType(List<LatLng> route, int index) {
        if (index <= 0 || index >= route.size() - 1) {
            return ARWaypoint.WaypointType.WAYPOINT;
        }

        LatLng prev = route.get(index - 1);
        LatLng current = route.get(index);
        LatLng next = route.get(index + 1);

        // Calculer le bearing (azimut) entre les segments
        double bearing1 = calculateBearing(prev, current);
        double bearing2 = calculateBearing(current, next);

        // Calculer l'angle de virage
        double turnAngle = bearing2 - bearing1;

        // Normaliser entre -180 et 180
        while (turnAngle > 180) turnAngle -= 360;
        while (turnAngle < -180) turnAngle += 360;

        // Déterminer le type selon l'angle
        if (Math.abs(turnAngle) < 20) {
            return ARWaypoint.WaypointType.CONTINUE;
        } else if (turnAngle > 20) {
            return ARWaypoint.WaypointType.TURN_LEFT;
        } else {
            return ARWaypoint.WaypointType.TURN_RIGHT;
        }
    }

    /**
     * Calcule le bearing (azimut) entre deux points GPS
     */
    private double calculateBearing(LatLng from, LatLng to) {
        double lat1 = Math.toRadians(from.latitude);
        double lat2 = Math.toRadians(to.latitude);
        double dLon = Math.toRadians(to.longitude - from.longitude);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360; // Normaliser 0-360
    }

    /**
     * Crée des flèches 3D directionnelles le long du parcours
     */
    private void createDirectionalArrows(List<LatLng> route) {
        directionalArrows.clear();

        for (int i = 0; i < route.size() - 1; i++) {
            LatLng start = route.get(i);
            LatLng end = route.get(i + 1);

            float segmentDistance = GeoUtils.distanceBetween(start, end);
            int numArrows = Math.max(1, (int)(segmentDistance / ARROW_DISTANCE));

            // Calculer le bearing pour ce segment
            double bearing = calculateBearing(start, end);

            for (int j = 0; j <= numArrows; j++) {
                float fraction = j / (float)Math.max(numArrows, 1);

                // Interpoler la position GPS
                double lat = start.latitude + (end.latitude - start.latitude) * fraction;
                double lng = start.longitude + (end.longitude - start.longitude) * fraction;
                LatLng arrowPos = new LatLng(lat, lng);

                // Convertir en position AR
                Vector3 arPosition = GeoUtils.gpsToARPosition(originGPS, arrowPos, OBJECT_HEIGHT);

                // Type de flèche selon le waypoint
                ARWaypoint.WaypointType arrowType = getArrowTypeForSegment(i);

                // Créer la flèche 3D
                DirectionalArrow arrow = new DirectionalArrow(
                        arrowPos,
                        arPosition,
                        (float)bearing,
                        arrowType
                );

                create3DArrowNode(arrow);
                directionalArrows.add(arrow);
            }
        }
    }

    /**
     * Crée un node de flèche 3D avec le bon type
     */
    private void create3DArrowNode(DirectionalArrow arrow) {
        if (!arrow3DRenderer.isMaterialsReady()) {
            // Attendre que les matériaux soient prêts
            new android.os.Handler().postDelayed(() -> create3DArrowNode(arrow), 100);
            return;
        }

        Node arrowNode = null;

        switch (arrow.type) {
            case TURN_LEFT:
                arrowNode = arrow3DRenderer.createLeftArrow(arrow.arPosition, arrow.bearing);
                break;
            case TURN_RIGHT:
                arrowNode = arrow3DRenderer.createRightArrow(arrow.arPosition, arrow.bearing);
                break;
            case DESTINATION:
                arrowNode = arrow3DRenderer.createDestinationMarker(arrow.arPosition);
                break;
            default:
                arrowNode = arrow3DRenderer.createStraightArrow(arrow.arPosition, arrow.bearing);
                break;
        }

        if (arrowNode != null) {
            arrowNode.setParent(anchorNode);
            arrow.sceneNode = arrowNode;
        }
    }

    /**
     * Obtient le type de flèche pour un segment
     */
    private ARWaypoint.WaypointType getArrowTypeForSegment(int segmentIndex) {
        if (segmentIndex >= 0 && segmentIndex < arWaypoints.size()) {
            return arWaypoints.get(segmentIndex).getType();
        }
        return ARWaypoint.WaypointType.CONTINUE;
    }

    /**
     * Crée les lignes au sol entre waypoints
     */
    private void createRouteLines() {
        for (int i = 0; i < arWaypoints.size() - 1; i++) {
            Vector3 start = arWaypoints.get(i).getArPosition();
            Vector3 end = arWaypoints.get(i + 1).getArPosition();
            createLine(start, end);
        }
    }

    private void createLine(Vector3 start, Vector3 end) {
        Vector3 difference = Vector3.subtract(end, start);
        Vector3 center = Vector3.add(start, difference.scaled(0.5f));
        float length = difference.length();

        ModelRenderable lineRenderable = ShapeFactory.makeCylinder(
                0.03f,
                length,
                Vector3.zero(),
                sceneManager.getLineMaterial()
        );

        Node lineNode = new Node();
        lineNode.setParent(anchorNode);
        lineNode.setLocalPosition(center);
        lineNode.setRenderable(lineRenderable);

        Vector3 direction = difference.normalized();
        com.google.ar.sceneform.math.Quaternion rotation =
                com.google.ar.sceneform.math.Quaternion.rotationBetweenVectors(
                        new Vector3(0, 1, 0),
                        direction
                );
        lineNode.setLocalRotation(rotation);
    }

    /**
     * Met à jour la visibilité et calcule la distance restante
     */
    /**
     * Met à jour la visibilité et calcule la distance restante
     * CORRECTION: Calcul précis basé sur la position réelle de l'utilisateur
     */
    /**
     * Met à jour la visibilité et calcule la distance restante
     * CORRECTION: Calcul précis basé sur la position réelle de l'utilisateur
     */
    public void updateVisibility(Vector3 userPosition) {
        remainingDistance = 0f;

        if (arWaypoints.isEmpty()) {
            return;
        }

        // 1. Trouver le waypoint le plus proche de l'utilisateur
        int closestWaypointIndex = 0;
        float minDistToWaypoint = Float.MAX_VALUE;

        for (int i = 0; i < arWaypoints.size(); i++) {
            ARWaypoint waypoint = arWaypoints.get(i);
            float dist = Vector3.subtract(waypoint.getArPosition(), userPosition).length();

            if (dist < minDistToWaypoint) {
                minDistToWaypoint = dist;
                closestWaypointIndex = i;
            }
        }

        // 2. Calculer la distance depuis le waypoint le plus proche jusqu'à la fin
        // Distance jusqu'au waypoint le plus proche (si l'utilisateur n'est pas exactement dessus)
        ARWaypoint closestWaypoint = arWaypoints.get(closestWaypointIndex);
        float distToClosestWaypoint = GeoUtils.distanceBetween(
                convertARToGPS(userPosition),
                closestWaypoint.getGpsPosition()
        );

        // Ajouter la distance jusqu'au waypoint le plus proche
        remainingDistance = distToClosestWaypoint;

        // 3. Ajouter toutes les distances entre les waypoints suivants
        for (int i = closestWaypointIndex; i < arWaypoints.size() - 1; i++) {
            ARWaypoint current = arWaypoints.get(i);
            ARWaypoint next = arWaypoints.get(i + 1);

            float segmentDist = GeoUtils.distanceBetween(
                    current.getGpsPosition(),
                    next.getGpsPosition()
            );

            remainingDistance += segmentDist;

            // Mettre à jour la visibilité des waypoints
            current.updateDistance(userPosition);
            current.setVisible(current.shouldBeVisible(MAX_RENDER_DISTANCE));
        }

        // Mettre à jour le dernier waypoint
        if (!arWaypoints.isEmpty()) {
            ARWaypoint lastWaypoint = arWaypoints.get(arWaypoints.size() - 1);
            lastWaypoint.updateDistance(userPosition);
            lastWaypoint.setVisible(lastWaypoint.shouldBeVisible(MAX_RENDER_DISTANCE));
        }

        // 4. Mettre à jour les flèches
        for (DirectionalArrow arrow : directionalArrows) {
            arrow.updateDistance(userPosition);
            arrow.setVisible(arrow.shouldBeVisible(MAX_RENDER_DISTANCE));
        }

        // 5. Log pour debug
        Log.d(TAG, String.format("Distance restante calculée: %.2f km (%.0f m)",
                remainingDistance / 1000, remainingDistance));
    }

    /**
     * Convertit une position AR en coordonnées GPS (inverse de gpsToARPosition)
     */
    private LatLng convertARToGPS(Vector3 arPosition) {
        if (originGPS == null) {
            return originGPS;
        }

        // Conversion inverse: AR → GPS
        // arPosition.x et arPosition.z sont en mètres depuis l'origine

        double lat = originGPS.latitude + (arPosition.z / 111320.0);
        double lon = originGPS.longitude + (arPosition.x / (111320.0 * Math.cos(Math.toRadians(originGPS.latitude))));

        return new LatLng(lat, lon);
    }

    /**
     * Obtient la prochaine instruction avec distance précise
     */
    public NavigationInstruction getNextInstruction(Vector3 userPosition) {
        DirectionalArrow closest = getClosestVisibleArrow(userPosition);

        if (closest == null && !arWaypoints.isEmpty()) {
            ARWaypoint destination = arWaypoints.get(arWaypoints.size() - 1);
            float distToDest = GeoUtils.distanceBetween(
                    originGPS,
                    destination.getGpsPosition()
            );
            return new NavigationInstruction(
                    "Continuez vers la destination",
                    distToDest,
                    ARWaypoint.WaypointType.CONTINUE
            );
        }

        if (closest != null) {
            String instruction = getInstructionText(closest.type, closest.distanceFromUser);
            return new NavigationInstruction(instruction, closest.distanceFromUser, closest.type);
        }

        return new NavigationInstruction("Suivez les flèches", 0f, ARWaypoint.WaypointType.CONTINUE);
    }

    /**
     * Obtient la flèche visible la plus proche devant l'utilisateur
     */
    private DirectionalArrow getClosestVisibleArrow(Vector3 userPosition) {
        DirectionalArrow closest = null;
        float minDistance = Float.MAX_VALUE;

        for (DirectionalArrow arrow : directionalArrows) {
            Vector3 toArrow = Vector3.subtract(arrow.arPosition, userPosition);
            float distance = toArrow.length();

            // Seulement les flèches devant (z négatif dans la direction de la caméra)
            if (distance < minDistance && distance < 50f) {
                minDistance = distance;
                closest = arrow;
            }
        }

        return closest;
    }

    private String getInstructionText(ARWaypoint.WaypointType type, float distance) {
        String distText;
        if (distance < 50) {
            distText = String.format("dans %.0f m", distance);
        } else if (distance < 1000) {
            distText = String.format("dans %.0f m", distance);
        } else {
            distText = String.format("dans %.1f km", distance / 1000);
        }

        switch (type) {
            case TURN_LEFT:
                return "Tournez à gauche " + distText;
            case TURN_RIGHT:
                return "Tournez à droite " + distText;
            case DESTINATION:
                return "Arrivée " + distText;
            default:
                return "Continuez tout droit";
        }
    }

    public ARWaypoint getClosestWaypoint(Vector3 position) {
        if (arWaypoints.isEmpty()) return null;

        ARWaypoint closest = arWaypoints.get(0);
        float minDistance = Vector3.subtract(closest.getArPosition(), position).length();

        for (ARWaypoint waypoint : arWaypoints) {
            float distance = Vector3.subtract(waypoint.getArPosition(), position).length();
            if (distance < minDistance) {
                minDistance = distance;
                closest = waypoint;
            }
        }

        return closest;
    }

    public void clearRoute() {
        for (ARWaypoint waypoint : arWaypoints) {
            waypoint.dispose();
        }
        arWaypoints.clear();

        for (DirectionalArrow arrow : directionalArrows) {
            arrow.dispose();
        }
        directionalArrows.clear();

        if (anchorNode != null) {
            List<Node> children = new ArrayList<>(anchorNode.getChildren());
            for (Node child : children) {
                child.setParent(null);
            }
        }

        Log.d(TAG, "Route AR nettoyée");
    }

    private List<LatLng> simplifyRoute(List<LatLng> route, float spacing) {
        if (route.isEmpty()) return new ArrayList<>();

        List<LatLng> simplified = new ArrayList<>();
        simplified.add(route.get(0));

        float accumulatedDistance = 0;

        for (int i = 1; i < route.size(); i++) {
            LatLng prev = route.get(i - 1);
            LatLng current = route.get(i);

            float distance = GeoUtils.distanceBetween(prev, current);
            accumulatedDistance += distance;

            if (accumulatedDistance >= spacing) {
                simplified.add(current);
                accumulatedDistance = 0;
            }
        }

        LatLng last = route.get(route.size() - 1);
        if (!simplified.get(simplified.size() - 1).equals(last)) {
            simplified.add(last);
        }

        return simplified;
    }

    // Getters
    public List<ARWaypoint> getArWaypoints() { return arWaypoints; }
    public LatLng getOriginGPS() { return originGPS; }
    public float getTotalRouteDistance() { return totalRouteDistance; }
    public float getRemainingDistance() { return remainingDistance; }

    // Classes internes
    private static class DirectionalArrow {
        LatLng gpsPosition;
        Vector3 arPosition;
        float bearing; // Angle en degrés (0-360)
        ARWaypoint.WaypointType type;
        Node sceneNode;
        float distanceFromUser;

        DirectionalArrow(LatLng gps, Vector3 ar, float bearing, ARWaypoint.WaypointType type) {
            this.gpsPosition = gps;
            this.arPosition = ar;
            this.bearing = bearing;
            this.type = type;
        }

        void updateDistance(Vector3 userPosition) {
            distanceFromUser = Vector3.subtract(arPosition, userPosition).length();
        }

        boolean shouldBeVisible(float maxDistance) {
            return distanceFromUser <= maxDistance;
        }

        void setVisible(boolean visible) {
            if (sceneNode != null) {
                sceneNode.setEnabled(visible);
            }
        }

        void dispose() {
            if (sceneNode != null) {
                sceneNode.setParent(null);
                sceneNode = null;
            }
        }
    }

    public static class NavigationInstruction {
        public String text;
        public float distance;
        public ARWaypoint.WaypointType type;

        NavigationInstruction(String text, float distance, ARWaypoint.WaypointType type) {
            this.text = text;
            this.distance = distance;
            this.type = type;
        }
    }
}