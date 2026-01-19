
// ========== Arrow3DRenderer.java - NOUVEAU FICHIER ==========
package com.example.tp7.ar;

import android.content.Context;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.Color;

/**
 * Créateur de flèches 3D détaillées pour la navigation AR
 */
public class Arrow3DRenderer {

    private Material blueMaterial;
    private Material yellowMaterial;
    private Material greenMaterial;

    public Arrow3DRenderer(Context context) {
        createMaterials(context);
    }

    private void createMaterials(Context context) {
        // Matériau bleu pour flèches normales
        MaterialFactory.makeOpaqueWithColor(context, new Color(33, 150, 243, 255))
                .thenAccept(material -> blueMaterial = material);

        // Matériau jaune pour virages
        MaterialFactory.makeOpaqueWithColor(context, new Color(255, 193, 7, 255))
                .thenAccept(material -> yellowMaterial = material);

        // Matériau vert pour destination
        MaterialFactory.makeOpaqueWithColor(context, new Color(76, 175, 80, 255))
                .thenAccept(material -> greenMaterial = material);
    }

    /**
     * Crée une flèche 3D droite (continuer tout droit)
     */
    public Node createStraightArrow(Vector3 position, float rotation) {
        if (blueMaterial == null) return null;

        Node arrowNode = new Node();
        arrowNode.setLocalPosition(position);

        // Corps de la flèche (rectangle allongé)
        ModelRenderable body = ShapeFactory.makeCube(
                new Vector3(0.2f, 0.05f, 0.8f),
                Vector3.zero(),
                blueMaterial
        );

        // Pointe de la flèche (triangle)
        ModelRenderable tip = ShapeFactory.makeCube(
                new Vector3(0.4f, 0.05f, 0.3f),
                new Vector3(0, 0, 0.55f),
                blueMaterial
        );

        Node bodyNode = new Node();
        bodyNode.setRenderable(body);
        bodyNode.setParent(arrowNode);

        Node tipNode = new Node();
        tipNode.setRenderable(tip);
        tipNode.setParent(arrowNode);

        // Rotation pour orienter vers la direction
        Quaternion rot = Quaternion.axisAngle(new Vector3(0, 1, 0), rotation);
        arrowNode.setLocalRotation(rot);

        return arrowNode;
    }

    /**
     * Crée une flèche 3D courbe GAUCHE
     */
    public Node createLeftArrow(Vector3 position, float rotation) {
        if (yellowMaterial == null) return null;

        Node arrowNode = new Node();
        arrowNode.setLocalPosition(position);

        // Corps courbé (plusieurs segments)
        for (int i = 0; i < 5; i++) {
            float angle = i * 18f; // 90° total en 5 segments
            float radius = 0.5f;

            float x = -radius * (float)Math.sin(Math.toRadians(angle));
            float z = radius * (1 - (float)Math.cos(Math.toRadians(angle)));

            ModelRenderable segment = ShapeFactory.makeCube(
                    new Vector3(0.15f, 0.05f, 0.25f),
                    new Vector3(x, 0, z),
                    yellowMaterial
            );

            Node segmentNode = new Node();
            segmentNode.setRenderable(segment);
            segmentNode.setLocalRotation(
                    Quaternion.axisAngle(new Vector3(0, 1, 0), -angle)
            );
            segmentNode.setParent(arrowNode);
        }

        // Pointe de la flèche à gauche
        ModelRenderable tip = ShapeFactory.makeCube(
                new Vector3(0.35f, 0.05f, 0.25f),
                new Vector3(-0.5f, 0, 0.5f),
                yellowMaterial
        );

        Node tipNode = new Node();
        tipNode.setRenderable(tip);
        tipNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1, 0), -90));
        tipNode.setParent(arrowNode);

        // Rotation globale
        Quaternion rot = Quaternion.axisAngle(new Vector3(0, 1, 0), rotation);
        arrowNode.setLocalRotation(rot);

        return arrowNode;
    }

    /**
     * Crée une flèche 3D courbe DROITE
     */
    public Node createRightArrow(Vector3 position, float rotation) {
        if (yellowMaterial == null) return null;

        Node arrowNode = new Node();
        arrowNode.setLocalPosition(position);

        // Corps courbé (plusieurs segments)
        for (int i = 0; i < 5; i++) {
            float angle = i * 18f; // 90° total
            float radius = 0.5f;

            float x = radius * (float)Math.sin(Math.toRadians(angle));
            float z = radius * (1 - (float)Math.cos(Math.toRadians(angle)));

            ModelRenderable segment = ShapeFactory.makeCube(
                    new Vector3(0.15f, 0.05f, 0.25f),
                    new Vector3(x, 0, z),
                    yellowMaterial
            );

            Node segmentNode = new Node();
            segmentNode.setRenderable(segment);
            segmentNode.setLocalRotation(
                    Quaternion.axisAngle(new Vector3(0, 1, 0), angle)
            );
            segmentNode.setParent(arrowNode);
        }

        // Pointe de la flèche à droite
        ModelRenderable tip = ShapeFactory.makeCube(
                new Vector3(0.35f, 0.05f, 0.25f),
                new Vector3(0.5f, 0, 0.5f),
                yellowMaterial
        );

        Node tipNode = new Node();
        tipNode.setRenderable(tip);
        tipNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1, 0), 90));
        tipNode.setParent(arrowNode);

        // Rotation globale
        Quaternion rot = Quaternion.axisAngle(new Vector3(0, 1, 0), rotation);
        arrowNode.setLocalRotation(rot);

        return arrowNode;
    }

    /**
     * Crée un marqueur de destination (cylindre vert)
     */
    public Node createDestinationMarker(Vector3 position) {
        if (greenMaterial == null) return null;

        Node markerNode = new Node();
        markerNode.setLocalPosition(position);

        ModelRenderable cylinder = ShapeFactory.makeCylinder(
                0.3f,
                1.5f,
                new Vector3(0, 0.75f, 0),
                greenMaterial
        );

        markerNode.setRenderable(cylinder);
        return markerNode;
    }

    public boolean isMaterialsReady() {
        return blueMaterial != null && yellowMaterial != null && greenMaterial != null;
    }
}


