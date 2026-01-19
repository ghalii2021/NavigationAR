package com.example.tp7;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class NavigationManager {
    private static final String TAG = "NavigationManager";
    private static final float ARRIVAL_THRESHOLD = 50; // mètres
    private static final float NEXT_STEP_THRESHOLD = 30; // mètres

    private DirectionsHelper.RouteInfo routeInfo;
    private List<String> steps;
    private int currentStepIndex = 0;
    private boolean isNavigating = false;

    private NavigationListener listener;
    private Handler handler;
    private Runnable updateRunnable;

    public interface NavigationListener {
        void onNavigationUpdate(NavigationUpdate update);
        void onStepChanged(int stepIndex, String instruction);
        void onArrived();
        void onOffRoute();
    }

    public static class NavigationUpdate {
        public String nextInstruction;
        public String distanceToNext;
        public String remainingDistance;
        public String remainingTime;
        public String arrivalTime;
        public int progress;
        public String maneuverIcon;

        public NavigationUpdate() {}
    }

    public NavigationManager(NavigationListener listener) {
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Démarre la navigation
     */
    public void startNavigation(DirectionsHelper.RouteInfo routeInfo) {
        this.routeInfo = routeInfo;
        this.steps = routeInfo.steps;
        this.currentStepIndex = 0;
        this.isNavigating = true;

        Log.d(TAG, "Navigation started with " + steps.size() + " steps");
    }

    /**
     * Met à jour la position actuelle
     */
    public void updateLocation(Location location) {
        if (!isNavigating || routeInfo == null || steps.isEmpty()) {
            return;
        }

        LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());

        // Vérifier si on est arrivé
        LatLng destination = routeInfo.points.get(routeInfo.points.size() - 1);
        float distanceToDestination = distanceBetween(currentPosition, destination);

        if (distanceToDestination < ARRIVAL_THRESHOLD) {
            isNavigating = false;
            if (listener != null) {
                listener.onArrived();
            }
            return;
        }

        // Trouver le point le plus proche sur la route
        int closestPointIndex = findClosestPoint(currentPosition);

        // Vérifier si on doit passer à l'étape suivante
        updateCurrentStep(currentPosition, closestPointIndex);

        // Calculer et envoyer les mises à jour
        NavigationUpdate update = calculateNavigationUpdate(currentPosition, closestPointIndex);
        if (listener != null) {
            listener.onNavigationUpdate(update);
        }
    }

    /**
     * Trouve le point le plus proche sur la route
     */
    private int findClosestPoint(LatLng currentPosition) {
        float minDistance = Float.MAX_VALUE;
        int closestIndex = 0;

        for (int i = 0; i < routeInfo.points.size(); i++) {
            float distance = distanceBetween(currentPosition, routeInfo.points.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    /**
     * Met à jour l'étape actuelle si nécessaire
     */
    private void updateCurrentStep(LatLng currentPosition, int closestPointIndex) {
        // Simple logique : passer à l'étape suivante tous les 20% du trajet
        int totalPoints = routeInfo.points.size();
        int pointsPerStep = totalPoints / Math.max(steps.size(), 1);
        int estimatedStepIndex = closestPointIndex / Math.max(pointsPerStep, 1);

        if (estimatedStepIndex > currentStepIndex && estimatedStepIndex < steps.size()) {
            currentStepIndex = estimatedStepIndex;
            if (listener != null) {
                listener.onStepChanged(currentStepIndex, steps.get(currentStepIndex));
            }
        }
    }

    /**
     * Calcule les informations de navigation
     */
    private NavigationUpdate calculateNavigationUpdate(LatLng currentPosition, int closestPointIndex) {
        NavigationUpdate update = new NavigationUpdate();

        // Instruction suivante
        if (currentStepIndex < steps.size()) {
            update.nextInstruction = steps.get(currentStepIndex);
            update.maneuverIcon = getManeuverIcon(update.nextInstruction);
        } else {
            update.nextInstruction = "Continuer tout droit";
            update.maneuverIcon = "⬆️";
        }

        // Distance jusqu'à la prochaine étape
        int nextStepPoint = Math.min((currentStepIndex + 1) * (routeInfo.points.size() / Math.max(steps.size(), 1)),
                routeInfo.points.size() - 1);
        float distanceToNextStep = calculateRemainingDistance(closestPointIndex, nextStepPoint);
        update.distanceToNext = formatDistance(distanceToNextStep);

        // Distance restante totale
        float remainingDist = calculateRemainingDistance(closestPointIndex, routeInfo.points.size() - 1);
        update.remainingDistance = formatDistance(remainingDist);

        // Temps restant (estimation basée sur 50 km/h en moyenne)
        float remainingTimeMinutes = (remainingDist / 1000) * 1.2f; // 1.2 min par km
        update.remainingTime = formatDuration(remainingTimeMinutes * 60);

        // Heure d'arrivée estimée
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, (int) remainingTimeMinutes);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        update.arrivalTime = sdf.format(calendar.getTime());

        // Progression
        update.progress = (int) ((closestPointIndex / (float) routeInfo.points.size()) * 100);

        return update;
    }

    /**
     * Calcule la distance restante entre deux points sur la route
     */
    private float calculateRemainingDistance(int fromIndex, int toIndex) {
        float totalDistance = 0;
        for (int i = fromIndex; i < toIndex && i < routeInfo.points.size() - 1; i++) {
            totalDistance += distanceBetween(routeInfo.points.get(i), routeInfo.points.get(i + 1));
        }
        return totalDistance;
    }

    /**
     * Calcule la distance entre deux points
     */
    private float distanceBetween(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results
        );
        return results[0];
    }

    /**
     * Formate une distance
     */
    private String formatDistance(float meters) {
        if (meters < 1000) {
            return String.format("%.0f m", meters);
        } else {
            return String.format("%.1f km", meters / 1000);
        }
    }

    /**
     * Formate une durée en secondes
     */
    private String formatDuration(float seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);

        if (hours > 0) {
            return String.format("%d h %d min", hours, minutes);
        } else {
            return String.format("%d min", minutes);
        }
    }

    /**
     * Retourne une icône basée sur l'instruction
     */
    private String getManeuverIcon(String instruction) {
        String lower = instruction.toLowerCase();

        if (lower.contains("gauche")) {
            return "⬅️";
        } else if (lower.contains("droite")) {
            return "➡️";
        } else if (lower.contains("tout droit") || lower.contains("continuer")) {
            return "⬆️";
        } else if (lower.contains("demi-tour")) {
            return "↩️";
        } else if (lower.contains("rond-point") || lower.contains("giratoire")) {
            return "🔄";
        } else if (lower.contains("sortir") || lower.contains("sortie")) {
            return "↗️";
        } else if (lower.contains("arriver") || lower.contains("destination")) {
            return "🏁";
        } else {
            return "⬆️";
        }
    }

    /**
     * Arrête la navigation
     */
    public void stopNavigation() {
        isNavigating = false;
        currentStepIndex = 0;
        if (updateRunnable != null && handler != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    /**
     * Vérifie si la navigation est active
     */
    public boolean isNavigating() {
        return isNavigating;
    }

    /**
     * Obtient l'étape actuelle
     */
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }
}