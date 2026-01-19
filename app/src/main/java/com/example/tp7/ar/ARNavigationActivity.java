package com.example.tp7.ar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.tp7.R;
import com.example.tp7.DirectionsHelper;
import com.example.tp7.utils.GeoUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.math.Vector3;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ARNavigationActivity extends AppCompatActivity {

    private static final String TAG = "ARNavigationActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    public static final String EXTRA_ORIGIN = "origin";
    public static final String EXTRA_DESTINATION = "destination";

    // UI Components
    private ArSceneView arSceneView;
    private TextView tvInstruction;
    private TextView tvDistance;
    private TextView tvStatus;
    private TextView tvManeuverIcon;
    private TextView tvRemainingDistance;
    private TextView tvRemainingTime;
    private TextView tvArrivalTime;
    private FloatingActionButton btnRecenter;
    private FloatingActionButton btnExit;

    // AR Managers
    private ARSceneManager arSceneManager;
    private ARRouteRenderer arRouteRenderer;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private LatLng currentGPS;

    // Navigation data
    private LatLng originGPS;
    private LatLng destinationGPS;
    private List<LatLng> routePoints;
    private boolean isNavigating = false;

    // Position filter
    private GeoUtils.PositionFilter positionFilter;

    // Update handler
    private Handler updateHandler;
    private Runnable updateRunnable;

    // ===== CORRECTION VITESSE =====
    // Vitesse moyenne réaliste: 30 km/h = 8.33 m/s (circulation urbaine)
    private static final float AVERAGE_SPEED_KMH = 30f; // km/h
    private static final float AVERAGE_SPEED_MS = AVERAGE_SPEED_KMH / 3.6f; // m/s (~8.33 m/s)

    // Suivi vitesse réelle (optionnel pour amélioration future)
    private List<Float> recentSpeeds = new ArrayList<>();
    private static final int MAX_SPEED_SAMPLES = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_navigation);

        initViews();
        initLocationClient();
        getNavigationData();

        if (checkPermissions()) {
            initializeAR();
        } else {
            requestPermissions();
        }
    }

    private void initViews() {
        arSceneView = findViewById(R.id.arSceneView);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.tvStatus);
        tvManeuverIcon = findViewById(R.id.tvManeuverIcon);
        tvRemainingDistance = findViewById(R.id.tvRemainingDistance);
        tvRemainingTime = findViewById(R.id.tvRemainingTime);
        tvArrivalTime = findViewById(R.id.tvArrivalTime);
        btnRecenter = findViewById(R.id.btnRecenter);
        btnExit = findViewById(R.id.btnExit);

        btnRecenter.setOnClickListener(v -> recenterRoute());
        btnExit.setOnClickListener(v -> finish());

        tvStatus.setText("Initialisation AR...");
        tvManeuverIcon.setText("⬆️");
    }

    private void initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        positionFilter = new GeoUtils.PositionFilter();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    LatLng rawGPS = new LatLng(
                            currentLocation.getLatitude(),
                            currentLocation.getLongitude()
                    );
                    currentGPS = positionFilter.filter(rawGPS);

                    // Suivre la vitesse réelle
                    trackSpeed(currentLocation);

                    updateARNavigation();
                }
            }
        };
    }

    /**
     * Suit la vitesse réelle de l'utilisateur pour calculs plus précis
     */
    private void trackSpeed(Location location) {
        if (location.hasSpeed()) {
            float speedMS = location.getSpeed(); // m/s

            // Ignorer vitesses aberrantes (> 150 km/h ou < 0)
            if (speedMS >= 0 && speedMS < 41.67f) { // 150 km/h max
                recentSpeeds.add(speedMS);

                // Garder seulement les 10 dernières mesures
                if (recentSpeeds.size() > MAX_SPEED_SAMPLES) {
                    recentSpeeds.remove(0);
                }
            }
        }
    }

    /**
     * Calcule la vitesse moyenne basée sur les mesures récentes
     * Si pas de données, utilise la vitesse par défaut (30 km/h)
     */
    private float getAverageSpeed() {
        if (recentSpeeds.isEmpty()) {
            return AVERAGE_SPEED_MS; // Défaut: 30 km/h
        }

        float sum = 0;
        for (float speed : recentSpeeds) {
            sum += speed;
        }
        float avgSpeed = sum / recentSpeeds.size();

        // Si vitesse très faible (< 5 km/h), utiliser vitesse par défaut
        if (avgSpeed < 1.39f) { // 5 km/h
            return AVERAGE_SPEED_MS;
        }

        return avgSpeed;
    }

    private void getNavigationData() {
        if (getIntent().hasExtra(EXTRA_ORIGIN)) {
            String originStr = getIntent().getStringExtra(EXTRA_ORIGIN);
            String[] parts = originStr.split(",");
            originGPS = new LatLng(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1])
            );
        }

        if (getIntent().hasExtra(EXTRA_DESTINATION)) {
            String destStr = getIntent().getStringExtra(EXTRA_DESTINATION);
            String[] parts = destStr.split(",");
            destinationGPS = new LatLng(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1])
            );
        }

        Log.d(TAG, "Navigation: " + originGPS + " → " + destinationGPS);
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                CAMERA_PERMISSION_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeAR();
            } else {
                Toast.makeText(this, "Permissions caméra et GPS requises", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeAR() {
        tvStatus.setText("Initialisation ARCore...");

        arSceneManager = new ARSceneManager(this, arSceneView);
        arRouteRenderer = new ARRouteRenderer(this, arSceneManager);

        arSceneManager.checkARAvailability(new ARSceneManager.ARInitCallback() {
            @Override
            public void onARInitialized() {
                tvStatus.setText("AR initialisé - Calcul de la route...");
                calculateRoute();
            }

            @Override
            public void onARInitFailed(String error) {
                tvStatus.setText("Erreur AR: " + error);
                Toast.makeText(ARNavigationActivity.this,
                        "Erreur AR: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void calculateRoute() {
        if (originGPS == null || destinationGPS == null) {
            Toast.makeText(this, "Données de navigation manquantes", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String origin = originGPS.latitude + "," + originGPS.longitude;
        String destination = destinationGPS.latitude + "," + destinationGPS.longitude;

        DirectionsHelper.getDirections(origin, destination,
                new DirectionsHelper.DirectionsCallback() {
                    @Override
                    public void onDirectionsReceived(DirectionsHelper.RouteInfo routeInfo) {
                        routePoints = routeInfo.points;
                        startARNavigation();
                    }

                    @Override
                    public void onDirectionsError(String error) {
                        Toast.makeText(ARNavigationActivity.this,
                                "Erreur calcul route: " + error,
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private void startARNavigation() {
        if (routePoints == null || routePoints.isEmpty()) {
            Toast.makeText(this, "Route invalide", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("Obtention position GPS...");

        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    currentLocation = location;
                    currentGPS = new LatLng(location.getLatitude(), location.getLongitude());

                    arRouteRenderer.setOrigin(currentGPS);
                    arRouteRenderer.createRoute(routePoints);

                    startLocationUpdates();
                    startARUpdates();

                    isNavigating = true;
                    tvStatus.setText("🧭 Navigation AR active");

                    updateRouteInfo();

                    Toast.makeText(this, "✅ Navigation démarrée - Suivez les flèches 3D!", Toast.LENGTH_LONG).show();
                } else {
                    tvStatus.setText("Position GPS introuvable");
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Permission GPS refusée", e);
        }
    }

    private void startLocationUpdates() {
        try {
            LocationRequest locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(1000)
                    .setFastestInterval(500);

            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
            );
        } catch (SecurityException e) {
            Log.e(TAG, "Permission GPS refusée", e);
        }
    }

    private void startARUpdates() {
        updateHandler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isNavigating) {
                    updateARNavigation();
                    updateHandler.postDelayed(this, 100);
                }
            }
        };
        updateHandler.post(updateRunnable);
    }

    private void updateARNavigation() {
        if (currentGPS == null) return;

        Vector3 userARPosition = GeoUtils.gpsToARPosition(
                arRouteRenderer.getOriginGPS(),
                currentGPS,
                0f
        );

        arRouteRenderer.updateVisibility(userARPosition);

        ARRouteRenderer.NavigationInstruction instruction =
                arRouteRenderer.getNextInstruction(userARPosition);

        if (instruction != null) {
            updateNavigationUI(instruction);
        }

        updateRouteInfo();

        ARWaypoint closest = arRouteRenderer.getClosestWaypoint(userARPosition);
        if (closest != null) {
            float distance = GeoUtils.distanceBetween(currentGPS, closest.getGpsPosition());
            if (closest.getType() == ARWaypoint.WaypointType.DESTINATION && distance < 15f) {
                onArrival();
            }
        }
    }

    /**
     * ===== CORRECTION CALCUL TEMPS =====
     * Met à jour les informations de route avec calcul correct basé sur 30 km/h
     */
    private void updateRouteInfo() {
        float remainingDist = arRouteRenderer.getRemainingDistance();

        // ===== DISTANCE RESTANTE =====
        String distText;
        if (remainingDist < 1000) {
            distText = String.format(Locale.getDefault(), "%.0f m", remainingDist);
        } else {
            distText = String.format(Locale.getDefault(), "%.1f km", remainingDist / 1000);
        }
        tvRemainingDistance.setText(distText);

        // ===== TEMPS RESTANT =====
        // Utiliser la vitesse moyenne (réelle si disponible, sinon 60 km/h)
        float avgSpeed = getAverageSpeed();
        float remainingTimeSeconds = remainingDist / avgSpeed;

        String timeText = formatDuration((int) remainingTimeSeconds);
        tvRemainingTime.setText(timeText);

        // ===== HEURE D'ARRIVÉE =====
        Calendar arrivalCal = Calendar.getInstance();
        arrivalCal.add(Calendar.SECOND, (int) remainingTimeSeconds);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        tvArrivalTime.setText(sdf.format(arrivalCal.getTime()));

        // ===== LOG DEBUG =====
        Log.d(TAG, String.format(Locale.getDefault(),
                "Distance: %.2f km | Vitesse: %.1f km/h | Temps: %s | Arrivée: %s",
                remainingDist / 1000,
                avgSpeed * 3.6f,
                timeText,
                sdf.format(arrivalCal.getTime())
        ));
    }

    /**
     * Formate une durée en secondes vers format lisible
     * CORRECTION: Calculs corrects pour heures et minutes
     */
    private String formatDuration(int seconds) {
        if (seconds < 0) {
            return "0 min";
        }

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d h %d min", hours, minutes);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%d min", minutes);
        } else {
            return "< 1 min";
        }
    }

    /**
     * Met à jour l'interface de navigation
     */
    private void updateNavigationUI(ARRouteRenderer.NavigationInstruction instruction) {
        tvInstruction.setText(instruction.text);

        String distanceText;
        if (instruction.distance < 1000) {
            distanceText = String.format(Locale.getDefault(), "%.0f m", instruction.distance);
        } else {
            distanceText = String.format(Locale.getDefault(), "%.1f km", instruction.distance / 1000);
        }
        tvDistance.setText(distanceText);

        String icon = getManeuverIcon(instruction.type);
        tvManeuverIcon.setText(icon);
    }

    private String getManeuverIcon(ARWaypoint.WaypointType type) {
        switch (type) {
            case START:
                return "🟢";
            case DESTINATION:
                return "🏁";
            case TURN_LEFT:
                return "⬅️";
            case TURN_RIGHT:
                return "➡️";
            case CONTINUE:
                return "⬆️";
            default:
                return "📍";
        }
    }

    private void recenterRoute() {
        if (currentGPS != null && routePoints != null) {
            arRouteRenderer.setOrigin(currentGPS);
            arRouteRenderer.clearRoute();
            arRouteRenderer.createRoute(routePoints);
            Toast.makeText(this, "✅ Route recentrée", Toast.LENGTH_SHORT).show();
        }
    }

    private void onArrival() {
        isNavigating = false;
        tvStatus.setText("🎉 Arrivée à destination!");
        tvInstruction.setText("Vous êtes arrivé!");
        tvManeuverIcon.setText("🏁");
        tvRemainingDistance.setText("0 m");
        tvRemainingTime.setText("0 min");
        Toast.makeText(this, "🎉 Félicitations! Vous êtes arrivé!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arSceneManager != null) {
            arSceneManager.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (arSceneManager != null) {
            arSceneManager.pause();
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        isNavigating = false;

        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }

        if (arRouteRenderer != null) {
            arRouteRenderer.clearRoute();
        }

        if (arSceneManager != null) {
            arSceneManager.destroy();
        }
    }
}