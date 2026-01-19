package com.example.tp7.ar;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.*;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Color;

/**
 * Gestionnaire de la session ARCore et de la scène Sceneform
 */
public class ARSceneManager {

    private static final String TAG = "ARSceneManager";

    private Context context;
    private Activity activity;
    private Session arSession;
    private ArSceneView arSceneView;
    private Scene scene;

    private boolean isARAvailable = false;
    private boolean sessionInitialized = false;

    // Matériaux pour les objets 3D
    private Material arrowMaterial;
    private Material waypointMaterial;
    private Material lineMaterial;

    public interface ARInitCallback {
        void onARInitialized();
        void onARInitFailed(String error);
    }

    public ARSceneManager(Activity activity, ArSceneView arSceneView) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
        this.arSceneView = arSceneView;
        this.scene = arSceneView.getScene();
    }

    /**
     * Vérifie la disponibilité d'ARCore sur l'appareil
     */
    public void checkARAvailability(ARInitCallback callback) {
        try {
            ArCoreApk.Availability availability = ArCoreApk.getInstance()
                    .checkAvailability(context);

            if (availability.isTransient()) {
                // ARCore vérifie encore la disponibilité
                new android.os.Handler().postDelayed(() -> {
                    checkARAvailability(callback);
                }, 200);
                return;
            }

            if (availability.isSupported()) {
                isARAvailable = true;
                Log.d(TAG, "ARCore est disponible sur cet appareil");

                // Demander l'installation d'ARCore si nécessaire
                ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance()
                        .requestInstall(activity, true);

                if (installStatus == ArCoreApk.InstallStatus.INSTALLED) {
                    initializeARSession(callback);
                } else {
                    callback.onARInitFailed("ARCore doit être installé");
                }
            } else {
                isARAvailable = false;
                callback.onARInitFailed("ARCore n'est pas supporté sur cet appareil");
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la vérification ARCore", e);
            callback.onARInitFailed("Erreur: " + e.getMessage());
        }
    }

    /**
     * Initialise la session ARCore
     */
    private void initializeARSession(ARInitCallback callback) {
        if (sessionInitialized) {
            callback.onARInitialized();
            return;
        }

        try {
            // Créer la session ARCore
            arSession = new Session(context);

            // Configuration de la session
            Config config = new Config(arSession);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);

            // **FIX PRINCIPAL : Désactiver l'estimation de lumière HDR**
            config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
            arSession.configure(config);
            // Attacher la session à ArSceneView
            arSceneView.setSession(arSession);
            sessionInitialized = true;

            // Créer les matériaux par défaut
            createDefaultMaterials(callback);

            Log.d(TAG, "Session ARCore initialisée avec succès");

        } catch (UnavailableArcoreNotInstalledException e) {
            callback.onARInitFailed("ARCore n'est pas installé");
        } catch (UnavailableApkTooOldException e) {
            callback.onARInitFailed("ARCore doit être mis à jour");
        } catch (UnavailableSdkTooOldException e) {
            callback.onARInitFailed("Version Android trop ancienne");
        } catch (UnavailableDeviceNotCompatibleException e) {
            callback.onARInitFailed("Appareil non compatible avec ARCore");
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'initialisation ARCore", e);
            callback.onARInitFailed("Erreur: " + e.getMessage());
        }
    }

    /**
     * Crée les matériaux 3D par défaut
     */
    private void createDefaultMaterials(ARInitCallback callback) {
        // Matériau pour les flèches (bleu)
        MaterialFactory.makeOpaqueWithColor(context, new Color(33, 150, 243))
                .thenAccept(material -> {
                    arrowMaterial = material;
                    checkMaterialsLoaded(callback);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Erreur création matériau flèche", throwable);
                    return null;
                });

        // Matériau pour les waypoints (vert)
        MaterialFactory.makeOpaqueWithColor(context, new Color(76, 175, 80))
                .thenAccept(material -> {
                    waypointMaterial = material;
                    checkMaterialsLoaded(callback);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Erreur création matériau waypoint", throwable);
                    return null;
                });

        // Matériau pour les lignes (blanc semi-transparent)
        MaterialFactory.makeTransparentWithColor(context, new Color(255, 255, 255, 128))
                .thenAccept(material -> {
                    lineMaterial = material;
                    checkMaterialsLoaded(callback);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Erreur création matériau ligne", throwable);
                    return null;
                });
    }

    /**
     * Vérifie si tous les matériaux sont chargés
     */
    private void checkMaterialsLoaded(ARInitCallback callback) {
        if (arrowMaterial != null && waypointMaterial != null && lineMaterial != null) {
            callback.onARInitialized();
        }
    }

    /**
     * Démarre la session AR
     */
    public void resume() {
        if (arSession != null && sessionInitialized) {
            try {
                arSceneView.resume();
                arSession.resume();
                Log.d(TAG, "Session AR reprise");
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la reprise AR", e);
            }
        }
    }

    /**
     * Met en pause la session AR
     */
    public void pause() {
        if (arSceneView != null) {
            arSceneView.pause();
        }
        if (arSession != null) {
            arSession.pause();
            Log.d(TAG, "Session AR mise en pause");
        }
    }

    /**
     * Détruit la session AR
     */
    public void destroy() {
        if (arSceneView != null) {
            arSceneView.destroy();
        }
        if (arSession != null) {
            arSession.close();
            arSession = null;
        }
        sessionInitialized = false;
        Log.d(TAG, "Session AR détruite");
    }

    // Getters

    public Session getArSession() {
        return arSession;
    }

    public ArSceneView getArSceneView() {
        return arSceneView;
    }

    public Scene getScene() {
        return scene;
    }

    public boolean isARAvailable() {
        return isARAvailable;
    }

    public boolean isSessionInitialized() {
        return sessionInitialized;
    }

    public Material getArrowMaterial() {
        return arrowMaterial;
    }

    public Material getWaypointMaterial() {
        return waypointMaterial;
    }

    public Material getLineMaterial() {
        return lineMaterial;
    }
}