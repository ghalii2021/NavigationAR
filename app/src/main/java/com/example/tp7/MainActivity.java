package com.example.tp7;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tp7.ar.ARNavigationActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean locationPermissionGranted = false;

    // Pour gérer le type de carte sélectionné
    private int currentMapType = GoogleMap.MAP_TYPE_NORMAL;
    private String currentMapTypeName = "Normal";

    // Composants UI
    private Button btnMapType;
    private FloatingActionButton fabClearMarkers, fabMyLocation, fabRoute, fabStartNavigation;
    private CardView cardRouteInfo, navigationPanel;
    private TextView tvDistance, tvDuration, tvRouteTitle;
    private Button btnCloseRoute, btnViewSteps;

    // Composants de navigation
    private TextView tvNextInstruction, tvDistanceToNext, tvRemainingDistance,
            tvRemainingTime, tvArrivalTime, tvManeuverIcon;
    private ProgressBar progressNavigation;
    private ImageButton btnStopNavigation;

    // Pour l'itinéraire
    private Polyline currentPolyline;
    private Location currentLocation;
    private DirectionsHelper.RouteInfo currentRouteInfo; // Stocker les infos de route

    // Pour la navigation
    private NavigationManager navigationManager;
    private boolean isNavigationMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialiser les composants UI
        btnMapType = findViewById(R.id.btnMapType);
        fabClearMarkers = findViewById(R.id.fabClearMarkers);
        fabMyLocation = findViewById(R.id.fabMyLocation);
        fabRoute = findViewById(R.id.fabRoute);
        fabStartNavigation = findViewById(R.id.fabStartNavigation);
        cardRouteInfo = findViewById(R.id.cardRouteInfo);
        tvDistance = findViewById(R.id.tvDistance);
        tvDuration = findViewById(R.id.tvDuration);
        tvRouteTitle = findViewById(R.id.tvRouteTitle);
        btnCloseRoute = findViewById(R.id.btnCloseRoute);
        btnViewSteps = findViewById(R.id.btnViewSteps);

        // Composants de navigation
        navigationPanel = findViewById(R.id.navigationPanel);
        tvNextInstruction = findViewById(R.id.tvNextInstruction);
        tvDistanceToNext = findViewById(R.id.tvDistanceToNext);
        tvRemainingDistance = findViewById(R.id.tvRemainingDistance);
        tvRemainingTime = findViewById(R.id.tvRemainingTime);
        tvArrivalTime = findViewById(R.id.tvArrivalTime);
        tvManeuverIcon = findViewById(R.id.tvManeuverIcon);
        progressNavigation = findViewById(R.id.progressNavigation);
        btnStopNavigation = findViewById(R.id.btnStopNavigation);

        // Initialiser le gestionnaire de navigation
        navigationManager = new NavigationManager(new NavigationManager.NavigationListener() {
            @Override
            public void onNavigationUpdate(NavigationManager.NavigationUpdate update) {
                updateNavigationUI(update);
            }

            @Override
            public void onStepChanged(int stepIndex, String instruction) {
                Toast.makeText(MainActivity.this,
                        "📍 " + instruction,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onArrived() {
                Toast.makeText(MainActivity.this,
                        "🎉 Vous êtes arrivé à destination!",
                        Toast.LENGTH_LONG).show();
                stopNavigation();
            }

            @Override
            public void onOffRoute() {
                Toast.makeText(MainActivity.this,
                        "⚠️ Vous vous êtes éloigné de la route",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Initialiser le client de localisation
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtenir le fragment de la carte
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Demander les permissions de localisation
        getLocationPermission();

        // Configurer les écouteurs de clics
        setupClickListeners();
    }

    /**
     * Configure les écouteurs de clics pour les boutons
     */
    private void setupClickListeners() {
        // Bouton Type de carte
        btnMapType.setOnClickListener(v -> showMapTypeDialog());

        // FAB Effacer marqueurs
        fabClearMarkers.setOnClickListener(v -> {
            if (mMap != null) {
                mMap.clear();
                if (currentPolyline != null) {
                    currentPolyline.remove();
                    currentPolyline = null;
                }
                cardRouteInfo.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this,
                        "Tous les marqueurs ont été effacés",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // FAB Ma position
        fabMyLocation.setOnClickListener(v -> {
            if (locationPermissionGranted) {
                getDeviceLocation();
            } else {
                Toast.makeText(MainActivity.this,
                        "Permission de localisation non accordée",
                        Toast.LENGTH_SHORT).show();
                getLocationPermission();
            }
        });

        // FAB Itinéraire
        fabRoute.setOnClickListener(v -> showRouteDialog());

        // Bouton Fermer info itinéraire
        btnCloseRoute.setOnClickListener(v -> {
            cardRouteInfo.setVisibility(View.GONE);
            if (currentPolyline != null) {
                currentPolyline.remove();
                currentPolyline = null;
            }
            currentRouteInfo = null;
        });

        // Bouton Voir les étapes
        btnViewSteps.setOnClickListener(v -> {
            if (currentRouteInfo != null && !currentRouteInfo.steps.isEmpty()) {
                showStepsDialog(currentRouteInfo.steps);
            } else {
                Toast.makeText(MainActivity.this,
                        "Aucune étape disponible",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Bouton Démarrer la navigation
        fabStartNavigation.setOnClickListener(v -> {
            if (currentRouteInfo != null) {
                // Choix : Navigation normale ou AR
                showNavigationModeChoice();
            } else {
                Toast.makeText(MainActivity.this,
                        "Veuillez d'abord calculer un itinéraire",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Bouton Arrêter la navigation
        btnStopNavigation.setOnClickListener(v -> stopNavigation());
    }

    /**
     * Manipule la carte une fois disponible
     */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Position par défaut : Casablanca, Maroc
        LatLng casablanca = new LatLng(33.5731, -7.5898);

        // Ajouter un marqueur avec titre et description
        mMap.addMarker(new MarkerOptions()
                .position(casablanca)
                .title("Casablanca")
                .snippet("Ville de Casablanca, Maroc")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Déplacer la caméra vers Casablanca avec un zoom
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(casablanca, 12));

        // Activer les contrôles de zoom
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        // Afficher la position actuelle si permission accordée
        updateLocationUI();

        // Ajouter un écouteur de clics sur la carte
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            private int markerCount = 0;

            @Override
            public void onMapClick(@NonNull LatLng latLng) {
                markerCount++;

                // Ajouter un marqueur personnalisé à chaque clic
                mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Marqueur #" + markerCount)
                        .snippet("Lat: " + String.format("%.4f", latLng.latitude) +
                                ", Lng: " + String.format("%.4f", latLng.longitude))
                        .icon(BitmapDescriptorFactory.defaultMarker(getRandomHue())));

                // Afficher un message toast
                Toast.makeText(MainActivity.this,
                        "Marqueur #" + markerCount + " ajouté",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Affiche le dialogue pour calculer un itinéraire
     */
    private void showRouteDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_route, null);

        EditText etOrigin = dialogView.findViewById(R.id.etOrigin);
        EditText etDestination = dialogView.findViewById(R.id.etDestination);
        Button btnUseMyLocation = dialogView.findViewById(R.id.btnUseMyLocation);
        Button btnCalculateRoute = dialogView.findViewById(R.id.btnCalculateRoute);
        Button btnCancelRoute = dialogView.findViewById(R.id.btnCancelRoute);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Bouton utiliser ma position
        btnUseMyLocation.setOnClickListener(v -> {
            if (currentLocation != null) {
                String location = currentLocation.getLatitude() + "," + currentLocation.getLongitude();
                etOrigin.setText(location);
                Toast.makeText(this, "Position actuelle utilisée", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Position actuelle non disponible", Toast.LENGTH_SHORT).show();
            }
        });

        // Bouton calculer
        btnCalculateRoute.setOnClickListener(v -> {
            String origin = etOrigin.getText().toString().trim();
            String destination = etDestination.getText().toString().trim();

            if (origin.isEmpty() || destination.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
                return;
            }

            calculateRoute(origin, destination);
            dialog.dismiss();
        });

        // Bouton annuler
        btnCancelRoute.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Calcule l'itinéraire entre deux points (GRATUIT avec OSRM)
     */
    private void calculateRoute(String origin, String destination) {
        Toast.makeText(this, "Calcul de l'itinéraire...", Toast.LENGTH_SHORT).show();

        DirectionsHelper.getDirections(origin, destination,
                new DirectionsHelper.DirectionsCallback() {
                    @Override
                    public void onDirectionsReceived(DirectionsHelper.RouteInfo routeInfo) {
                        showRoutePreview(origin, destination, routeInfo);
                    }

                    @Override
                    public void onDirectionsError(String error) {
                        Toast.makeText(MainActivity.this,
                                "Erreur: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Affiche l'aperçu de la route avant de la tracer
     */
    private void showRoutePreview(String origin, String destination, DirectionsHelper.RouteInfo routeInfo) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_route_preview, null);

        TextView tvPreviewOrigin = dialogView.findViewById(R.id.tvPreviewOrigin);
        TextView tvPreviewDestination = dialogView.findViewById(R.id.tvPreviewDestination);
        TextView tvPreviewDistance = dialogView.findViewById(R.id.tvPreviewDistance);
        TextView tvPreviewDuration = dialogView.findViewById(R.id.tvPreviewDuration);
        TextView tvPreviewSteps = dialogView.findViewById(R.id.tvPreviewSteps);
        Button btnShowOnMap = dialogView.findViewById(R.id.btnShowOnMap);
        Button btnCancelPreview = dialogView.findViewById(R.id.btnCancelPreview);

        // Remplir les informations
        tvPreviewOrigin.setText(origin);
        tvPreviewDestination.setText(destination);
        tvPreviewDistance.setText(routeInfo.distance);
        tvPreviewDuration.setText(routeInfo.duration);

        // Afficher les 5 premières étapes (ou toutes si moins de 5)
        StringBuilder stepsText = new StringBuilder();
        int maxSteps = Math.min(5, routeInfo.steps.size());
        for (int i = 0; i < maxSteps; i++) {
            stepsText.append(routeInfo.steps.get(i)).append("\n\n");
        }
        if (routeInfo.steps.size() > 5) {
            stepsText.append("... et ").append(routeInfo.steps.size() - 5).append(" autres étapes");
        }
        tvPreviewSteps.setText(stepsText.toString());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Bouton pour afficher sur la carte
        btnShowOnMap.setOnClickListener(v -> {
            displayRoute(routeInfo);
            dialog.dismiss();
        });

        // Bouton annuler
        btnCancelPreview.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Affiche l'itinéraire sur la carte
     */
    private void displayRoute(DirectionsHelper.RouteInfo routeInfo) {
        if (mMap == null || routeInfo == null || routeInfo.points.isEmpty()) {
            return;
        }

        // Sauvegarder les infos de la route
        currentRouteInfo = routeInfo;

        // Effacer l'ancien tracé
        if (currentPolyline != null) {
            currentPolyline.remove();
        }

        // Dessiner le nouvel itinéraire avec un style amélioré
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routeInfo.points)
                .width(12)
                .color(Color.parseColor("#2196F3")) // Bleu vif
                .geodesic(true);

        currentPolyline = mMap.addPolyline(polylineOptions);

        // Ajouter des marqueurs pour le début et la fin
        LatLng start = routeInfo.points.get(0);
        LatLng end = routeInfo.points.get(routeInfo.points.size() - 1);

        mMap.addMarker(new MarkerOptions()
                .position(start)
                .title("🟢 Départ")
                .snippet("Point de départ")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(end)
                .title("🔴 Arrivée")
                .snippet("Point d'arrivée")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Ajouter des marqueurs intermédiaires tous les 20% du trajet
        int totalPoints = routeInfo.points.size();
        for (int i = 1; i < 5; i++) {
            int index = (totalPoints * i) / 5;
            if (index < totalPoints) {
                LatLng waypoint = routeInfo.points.get(index);
                mMap.addMarker(new MarkerOptions()
                        .position(waypoint)
                        .title("Étape " + i)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            }
        }

        // Ajuster la caméra pour afficher tout l'itinéraire
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng point : routeInfo.points) {
            boundsBuilder.include(point);
        }
        LatLngBounds bounds = boundsBuilder.build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

        // Afficher les informations de l'itinéraire
        tvDistance.setText("📏 Distance: " + routeInfo.distance);
        tvDuration.setText("⏱️ Durée: " + routeInfo.duration);
        cardRouteInfo.setVisibility(View.VISIBLE);

        // Afficher le bouton "Démarrer navigation"
        fabStartNavigation.show();

        Toast.makeText(this, "✅ Itinéraire affiché! Cliquez sur le bouton Play pour démarrer la navigation", Toast.LENGTH_LONG).show();
    }

    /**
     * Affiche les étapes de l'itinéraire
     */
    private void showStepsDialog(java.util.List<String> steps) {
        StringBuilder stepsText = new StringBuilder();
        for (String step : steps) {
            stepsText.append(step).append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("📍 Étapes de l'itinéraire")
                .setMessage(stepsText.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Affiche un dialogue pour choisir le type de carte
     */
    private void showMapTypeDialog() {
        if (mMap == null) return;

        String[] types = {"Normal", "Satellite", "Terrain", "Hybride"};
        int checkedItem = 0;

        switch (currentMapType) {
            case GoogleMap.MAP_TYPE_NORMAL:
                checkedItem = 0;
                break;
            case GoogleMap.MAP_TYPE_SATELLITE:
                checkedItem = 1;
                break;
            case GoogleMap.MAP_TYPE_TERRAIN:
                checkedItem = 2;
                break;
            case GoogleMap.MAP_TYPE_HYBRID:
                checkedItem = 3;
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🗺️ Choisir le type de carte");
        builder.setSingleChoiceItems(types, checkedItem, (dialog, which) -> {
            switch (which) {
                case 0:
                    changeMapType(GoogleMap.MAP_TYPE_NORMAL, "Normal");
                    break;
                case 1:
                    changeMapType(GoogleMap.MAP_TYPE_SATELLITE, "Satellite");
                    break;
                case 2:
                    changeMapType(GoogleMap.MAP_TYPE_TERRAIN, "Terrain");
                    break;
                case 3:
                    changeMapType(GoogleMap.MAP_TYPE_HYBRID, "Hybride");
                    break;
            }
            dialog.dismiss();
        });
        builder.setNegativeButton("Annuler", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Change le type de carte
     */
    private void changeMapType(int mapType, String typeName) {
        if (mMap != null) {
            mMap.setMapType(mapType);
            currentMapType = mapType;
            currentMapTypeName = typeName;
            btnMapType.setText(typeName);
            Toast.makeText(this, "Type de carte : " + typeName, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Demande les permissions de localisation
     */
    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        locationPermissionGranted = false;
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
                updateLocationUI();
            }
        }
    }

    /**
     * Met à jour l'interface utilisateur de localisation
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        try {
            if (locationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                getCurrentLocationForRoute();
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Obtient la position actuelle pour l'utiliser dans les itinéraires
     */
    private void getCurrentLocationForRoute() {
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationClient.getLastLocation();
                locationResult.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        currentLocation = task.getResult();
                    }
                });
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Obtient et affiche la position actuelle
     */
    private void getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationClient.getLastLocation();
                locationResult.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        currentLocation = task.getResult();
                        LatLng currentLatLng = new LatLng(currentLocation.getLatitude(),
                                currentLocation.getLongitude());

                        if (!isNavigationMode) {
                            mMap.addMarker(new MarkerOptions()
                                    .position(currentLatLng)
                                    .title("Ma position")
                                    .snippet("Position actuelle")
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                            BitmapDescriptorFactory.HUE_AZURE)));

                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                        }

                        Toast.makeText(this, "Position actuelle trouvée",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Position introuvable",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Démarre la navigation en temps réel
     */
    private void startNavigation() {
        if (currentRouteInfo == null) {
            Toast.makeText(this, "Aucun itinéraire calculé", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!locationPermissionGranted) {
            Toast.makeText(this, "Permission de localisation requise", Toast.LENGTH_SHORT).show();
            getLocationPermission();
            return;
        }

        isNavigationMode = true;
        navigationManager.startNavigation(currentRouteInfo);

        // Masquer la carte d'info et afficher le panneau de navigation
        cardRouteInfo.setVisibility(View.GONE);
        navigationPanel.setVisibility(View.VISIBLE);

        // Masquer le bouton de démarrage
        fabStartNavigation.hide();
        fabRoute.hide();
        fabClearMarkers.hide();

        // Activer le suivi continu de la position
        startLocationUpdates();

        // Centrer la caméra sur la position actuelle avec un zoom approprié
        if (currentLocation != null) {
            LatLng currentLatLng = new LatLng(currentLocation.getLatitude(),
                    currentLocation.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17));
        }

        // Activer le mode suivi
        try {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "🚗 Navigation démarrée", Toast.LENGTH_SHORT).show();
    }

    /**
     * Arrête la navigation
     */
    private void stopNavigation() {
        isNavigationMode = false;
        navigationManager.stopNavigation();

        // Afficher la carte d'info et masquer le panneau de navigation
        navigationPanel.setVisibility(View.GONE);
        if (currentRouteInfo != null) {
            cardRouteInfo.setVisibility(View.VISIBLE);
        }

        // Réafficher les boutons
        fabStartNavigation.show();
        fabRoute.show();
        fabClearMarkers.show();

        // Arrêter les mises à jour de position
        stopLocationUpdates();

        Toast.makeText(this, "Navigation arrêtée", Toast.LENGTH_SHORT).show();
    }

    /**
     * Démarre les mises à jour de position en temps réel
     */
    private void startLocationUpdates() {
        try {
            if (locationPermissionGranted) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = location;
                        updateNavigationPosition(location);
                    }
                });

                // Simuler des mises à jour (dans un vrai projet, utilisez LocationRequest)
                final android.os.Handler handler = new android.os.Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isNavigationMode && locationPermissionGranted) {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                                if (location != null) {
                                    currentLocation = location;
                                    updateNavigationPosition(location);
                                }
                            });
                            handler.postDelayed(this, 2000); // Mise à jour toutes les 2 secondes
                        }
                    }
                }, 2000);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Arrête les mises à jour de position
     */
    private void stopLocationUpdates() {
        // Dans un vrai projet, arrêter le LocationRequest ici
    }

    /**
     * Met à jour la position de navigation
     */
    private void updateNavigationPosition(Location location) {
        if (navigationManager != null && isNavigationMode) {
            navigationManager.updateLocation(location);

            // Centrer la caméra sur la position actuelle
            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng));
        }
    }

    /**
     * Met à jour l'interface de navigation
     */
    private void updateNavigationUI(NavigationManager.NavigationUpdate update) {
        tvNextInstruction.setText(update.nextInstruction);
        tvDistanceToNext.setText("Dans " + update.distanceToNext);
        tvRemainingDistance.setText(update.remainingDistance);
        tvRemainingTime.setText(update.remainingTime);
        tvArrivalTime.setText(update.arrivalTime);
        tvManeuverIcon.setText(update.maneuverIcon);
        progressNavigation.setProgress(update.progress);
    }

    /**
     * Affiche le choix entre navigation normale et AR
     */
    private void showNavigationModeChoice() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🗺️ Mode de navigation");
        builder.setMessage("Choisissez le mode de navigation souhaité");

        builder.setPositiveButton("🚗 Navigation Normale", (dialog, which) -> {
            startNavigation();
        });

        builder.setNegativeButton("📱 Navigation AR", (dialog, which) -> {
            startARNavigation();
        });

        builder.setNeutralButton("Annuler", null);
        builder.show();
    }

    /**
     * Démarre la navigation AR
     */
    private void startARNavigation() {
        if (currentRouteInfo == null || currentRouteInfo.points.isEmpty()) {
            Toast.makeText(this, "Itinéraire invalide", Toast.LENGTH_SHORT).show();
            return;
        }

        // Vérifier ARCore
        Toast.makeText(this, "Lancement de la navigation AR...", Toast.LENGTH_SHORT).show();

        // Préparer les données
        LatLng origin = currentRouteInfo.points.get(0);
        LatLng destination = currentRouteInfo.points.get(currentRouteInfo.points.size() - 1);

        // Lancer l'activité AR
        android.content.Intent intent = new android.content.Intent(this, ARNavigationActivity.class);
        intent.putExtra(ARNavigationActivity.EXTRA_ORIGIN,
                origin.latitude + "," + origin.longitude);
        intent.putExtra(ARNavigationActivity.EXTRA_DESTINATION,
                destination.latitude + "," + destination.longitude);
        startActivity(intent);
    }
    private float getRandomHue() {
        float[] hues = {
                BitmapDescriptorFactory.HUE_RED,
                BitmapDescriptorFactory.HUE_ORANGE,
                BitmapDescriptorFactory.HUE_YELLOW,
                BitmapDescriptorFactory.HUE_GREEN,
                BitmapDescriptorFactory.HUE_CYAN,
                BitmapDescriptorFactory.HUE_AZURE,
                BitmapDescriptorFactory.HUE_BLUE,
                BitmapDescriptorFactory.HUE_VIOLET,
                BitmapDescriptorFactory.HUE_MAGENTA,
                BitmapDescriptorFactory.HUE_ROSE
        };
        return hues[(int) (Math.random() * hues.length)];
    }

@Override
public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.map_types_menu, menu);
    return true;
}

@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int id = item.getItemId();

    if (mMap == null) {
        return super.onOptionsItemSelected(item);
    }

    if (id == R.id.map_type_normal) {
        changeMapType(GoogleMap.MAP_TYPE_NORMAL, "Normal");
        item.setChecked(true);
        return true;
    } else if (id == R.id.map_type_satellite) {
        changeMapType(GoogleMap.MAP_TYPE_SATELLITE, "Satellite");
        item.setChecked(true);
        return true;
    } else if (id == R.id.map_type_terrain) {
        changeMapType(GoogleMap.MAP_TYPE_TERRAIN, "Terrain");
        item.setChecked(true);
        return true;
    } else if (id == R.id.map_type_hybrid) {
        changeMapType(GoogleMap.MAP_TYPE_HYBRID, "Hybride");
        item.setChecked(true);
        return true;
    } else if (id == R.id.menu_clear_markers) {
        mMap.clear();
        if (currentPolyline != null) {
            currentPolyline.remove();
            currentPolyline = null;
        }
        cardRouteInfo.setVisibility(View.GONE);
        Toast.makeText(this, "Tous les marqueurs ont été effacés", Toast.LENGTH_SHORT).show();
        return true;
    } else if (id == R.id.menu_my_location) {
        if (locationPermissionGranted) {
            getDeviceLocation();
        } else {
            Toast.makeText(this, "Permission de localisation non accordée", Toast.LENGTH_SHORT).show();
            getLocationPermission();
        }
        return true;
    }

    return super.onOptionsItemSelected(item);
}
}