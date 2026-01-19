package com.example.tp7.ar;

import com.google.android.gms.maps.model.LatLng;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.Node;

/**
 * Représente un point de passage dans l'espace AR
 */
public class ARWaypoint {

    // Position GPS
    private LatLng gpsPosition;

    // Position AR locale (relative à l'origine)
    private Vector3 arPosition;

    // Node Sceneform pour le rendu 3D
    private Node sceneNode;

    // Type de waypoint
    private WaypointType type;

    // Distance depuis l'utilisateur
    private float distanceFromUser;

    // Index dans la liste de waypoints
    private int index;

    // Instruction associée
    private String instruction;

    // Visibilité
    private boolean visible;

    public enum WaypointType {
        START,          // Point de départ
        WAYPOINT,       // Point intermédiaire
        TURN_LEFT,      // Tourner à gauche
        TURN_RIGHT,     // Tourner à droite
        CONTINUE,       // Continuer
        DESTINATION     // Destination finale
    }

    public ARWaypoint(LatLng gpsPosition, WaypointType type) {
        this.gpsPosition = gpsPosition;
        this.type = type;
        this.visible = true;
    }

    public ARWaypoint(LatLng gpsPosition, Vector3 arPosition, WaypointType type) {
        this.gpsPosition = gpsPosition;
        this.arPosition = arPosition;
        this.type = type;
        this.visible = true;
    }

    // Getters et Setters

    public LatLng getGpsPosition() {
        return gpsPosition;
    }

    public void setGpsPosition(LatLng gpsPosition) {
        this.gpsPosition = gpsPosition;
    }

    public Vector3 getArPosition() {
        return arPosition;
    }

    public void setArPosition(Vector3 arPosition) {
        this.arPosition = arPosition;
    }

    public Node getSceneNode() {
        return sceneNode;
    }

    public void setSceneNode(Node sceneNode) {
        this.sceneNode = sceneNode;
    }

    public WaypointType getType() {
        return type;
    }

    public void setType(WaypointType type) {
        this.type = type;
    }

    public float getDistanceFromUser() {
        return distanceFromUser;
    }

    public void setDistanceFromUser(float distanceFromUser) {
        this.distanceFromUser = distanceFromUser;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (sceneNode != null) {
            sceneNode.setEnabled(visible);
        }
    }

    /**
     * Met à jour la distance depuis la position de l'utilisateur
     */
    public void updateDistance(Vector3 userPosition) {
        if (arPosition != null) {
            distanceFromUser = Vector3.subtract(arPosition, userPosition).length();
        }
    }

    /**
     * Vérifie si le waypoint doit être visible selon la distance
     */
    public boolean shouldBeVisible(float maxDistance) {
        return distanceFromUser <= maxDistance;
    }

    /**
     * Nettoie les ressources
     */
    public void dispose() {
        if (sceneNode != null) {
            sceneNode.setParent(null);
            sceneNode = null;
        }
    }

    @Override
    public String toString() {
        return "ARWaypoint{" +
                "type=" + type +
                ", gps=" + gpsPosition +
                ", distance=" + distanceFromUser +
                "m}";
    }
}