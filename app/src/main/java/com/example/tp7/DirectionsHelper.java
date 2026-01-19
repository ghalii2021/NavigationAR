package com.example.tp7;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class DirectionsHelper {

    private static final String TAG = "DirectionsHelper";
    // API GRATUITE : OSRM (Open Source Routing Machine)
    private static final String OSRM_API_URL = "https://router.project-osrm.org/route/v1/driving/";
    // API GRATUITE : Nominatim pour géocodage
    private static final String NOMINATIM_API_URL = "https://nominatim.openstreetmap.org/search";

    public interface DirectionsCallback {
        void onDirectionsReceived(RouteInfo routeInfo);
        void onDirectionsError(String error);
    }

    public static class RouteInfo {
        public List<LatLng> points;
        public String distance;
        public String duration;
        public List<String> steps;

        public RouteInfo() {
            points = new ArrayList<>();
            steps = new ArrayList<>();
        }
    }

    public static void getDirections(String origin, String destination, DirectionsCallback callback) {
        new DirectionsTask(origin, destination, callback).execute();
    }

    private static class DirectionsTask extends AsyncTask<Void, Void, RouteInfo> {
        private String origin;
        private String destination;
        private DirectionsCallback callback;
        private String errorMessage;

        DirectionsTask(String origin, String destination, DirectionsCallback callback) {
            this.origin = origin;
            this.destination = destination;
            this.callback = callback;
        }

        @Override
        protected RouteInfo doInBackground(Void... voids) {
            try {
                // Étape 1 : Géocoder l'origine et la destination si ce sont des adresses
                LatLng originLatLng = parseCoordinates(origin);
                if (originLatLng == null) {
                    originLatLng = geocodeAddress(origin);
                }
                LatLng destLatLng = parseCoordinates(destination);
                if (destLatLng == null) {
                    destLatLng = geocodeAddress(destination);
                }
                if (originLatLng == null || destLatLng == null) {
                    errorMessage = "Impossible de localiser l'adresse";
                    return null;
                }
                // Étape 2 : Obtenir l'itinéraire avec OSRM
                return getRouteFromOSRM(originLatLng, destLatLng);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching directions", e);
                errorMessage = "Erreur: " + e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(RouteInfo routeInfo) {
            if (routeInfo != null && callback != null) {
                callback.onDirectionsReceived(routeInfo);
            } else if (callback != null) {
                callback.onDirectionsError(errorMessage != null ? errorMessage : "Impossible de calculer l'itinéraire");
            }
        }

        /**
         * Tente de parser une chaîne comme coordonnées (lat,lng)
         */
        private LatLng parseCoordinates(String input) {
            try {
                String[] parts = input.trim().split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lng = Double.parseDouble(parts[1].trim());
                    return new LatLng(lat, lng);
                }
            } catch (Exception e) {
                // Pas des coordonnées valides
            }
            return null;
        }

        /**
         * Géocode une adresse en utilisant Nominatim (GRATUIT)
         */
        private LatLng geocodeAddress(String address) {
            try {
                String encodedAddress = URLEncoder.encode(address, "UTF-8");
                String urlString = NOMINATIM_API_URL + "?q=" + encodedAddress + "&format=json&limit=1";

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "TP7MapApp/1.0");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONArray results = new JSONArray(response.toString());
                    if (results.length() > 0) {
                        JSONObject location = results.getJSONObject(0);
                        double lat = location.getDouble("lat");
                        double lon = location.getDouble("lon");
                        return new LatLng(lat, lon);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Geocoding error", e);
            }
            return null;
        }

        /**
         * Obtient l'itinéraire avec OSRM (GRATUIT)
         */
        private RouteInfo getRouteFromOSRM(LatLng origin, LatLng destination) {
            try {
                // Format OSRM : lng,lat;lng,lat
                String coordinates = origin.longitude + "," + origin.latitude + ";" +
                        destination.longitude + "," + destination.latitude;

                String urlString = OSRM_API_URL + coordinates +
                        "?overview=full&steps=true&geometries=geojson";

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    return parseOSRMResponse(response.toString());
                } else {
                    errorMessage = "Erreur HTTP: " + responseCode;
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "OSRM request error", e);
                errorMessage = "Erreur OSRM: " + e.getMessage();
                return null;
            }
        }

        /**
         * Parse la réponse OSRM
         */
        private RouteInfo parseOSRMResponse(String jsonResponse) {
            try {
                JSONObject json = new JSONObject(jsonResponse);
                String code = json.getString("code");

                if (!code.equals("Ok")) {
                    errorMessage = "OSRM Status: " + code;
                    return null;
                }

                RouteInfo routeInfo = new RouteInfo();
                JSONArray routes = json.getJSONArray("routes");

                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);

                    // Distance en mètres
                    double distanceMeters = route.getDouble("distance");
                    routeInfo.distance = formatDistance(distanceMeters);

                    // Durée en secondes
                    double durationSeconds = route.getDouble("duration");
                    routeInfo.duration = formatDuration(durationSeconds);

                    // Géométrie (coordonnées du tracé)
                    JSONObject geometry = route.getJSONObject("geometry");
                    JSONArray coordinates = geometry.getJSONArray("coordinates");

                    for (int i = 0; i < coordinates.length(); i++) {
                        JSONArray coord = coordinates.getJSONArray(i);
                        double lng = coord.getDouble(0);
                        double lat = coord.getDouble(1);
                        routeInfo.points.add(new LatLng(lat, lng));
                    }

                    // Étapes
                    JSONArray legs = route.getJSONArray("legs");
                    if (legs.length() > 0) {
                        JSONObject leg = legs.getJSONObject(0);
                        JSONArray steps = leg.getJSONArray("steps");

                        for (int i = 0; i < steps.length(); i++) {
                            JSONObject step = steps.getJSONObject(i);
                            String maneuver = step.getJSONObject("maneuver").optString("type", "");
                            String name = step.optString("name", "Route sans nom");
                            double stepDistance = step.getDouble("distance");

                            String instruction = (i + 1) + ". " +
                                    translateManeuver(maneuver) + " " + name +
                                    " (" + formatDistance(stepDistance) + ")";
                            routeInfo.steps.add(instruction);
                        }
                    }
                }

                return routeInfo;
            } catch (Exception e) {
                Log.e(TAG, "Error parsing OSRM response", e);
                errorMessage = "Erreur de parsing: " + e.getMessage();
                return null;
            }
        }

        /**
         * Formate la distance
         */
        private String formatDistance(double meters) {
            if (meters < 1000) {
                return String.format("%.0f m", meters);
            } else {
                return String.format("%.1f km", meters / 1000);
            }
        }

        /**
         * Formate la durée
         */
        private String formatDuration(double seconds) {
            int hours = (int) (seconds / 3600);
            int minutes = (int) ((seconds % 3600) / 60);

            if (hours > 0) {
                return String.format("%d h %d min", hours, minutes);
            } else {
                return String.format("%d min", minutes);
            }
        }

        /**
         * Traduit les manœuvres en français
         */
        private String translateManeuver(String maneuver) {
            switch (maneuver) {
                case "turn":
                    return "Tourner";
                case "new name":
                    return "Continuer sur";
                case "depart":
                    return "Partir sur";
                case "arrive":
                    return "Arriver à";
                case "merge":
                    return "Rejoindre";
                case "on ramp":
                    return "Prendre la rampe";
                case "off ramp":
                    return "Sortir";
                case "fork":
                    return "Bifurquer";
                case "end of road":
                    return "Fin de route";
                case "continue":
                    return "Continuer";
                case "roundabout":
                    return "Rond-point";
                case "rotary":
                    return "Giratoire";
                case "turn left":
                    return "Tourner à gauche";
                case "turn right":
                    return "Tourner à droite";
                default:
                    return "Suivre";
            }
        }
    }
}