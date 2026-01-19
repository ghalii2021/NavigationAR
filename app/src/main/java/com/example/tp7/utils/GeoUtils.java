package com.example.tp7.utils;

import android.location.Location;
import com.google.android.gms.maps.model.LatLng;
import com.google.ar.sceneform.math.Vector3;

/**
 * Utilitaires pour convertir coordonnées GPS en coordonnées AR locales
 * Système ENU (East-North-Up) utilisé pour AR
 */
public class GeoUtils {

    // Constantes pour conversion
    private static final double EARTH_RADIUS = 6378137.0; // Rayon de la Terre en mètres
    private static final double METERS_PER_DEGREE_LAT = 111320.0; // 1° latitude = ~111.32 km

    /**
     * Convertit coordonnées GPS en position AR locale (ENU)
     * @param origin Point de référence (position de départ)
     * @param target Point cible (destination)
     * @return Vector3 en coordonnées AR (x=East, y=Up, z=-North)
     */
    public static Vector3 gpsToARPosition(LatLng origin, LatLng target) {
        return gpsToARPosition(origin, target, 0f);
    }

    /**
     * Convertit coordonnées GPS en position AR locale avec altitude
     * @param origin Point de référence
     * @param target Point cible
     * @param altitude Altitude en mètres
     * @return Vector3 en coordonnées AR
     */
    public static Vector3 gpsToARPosition(LatLng origin, LatLng target, float altitude) {
        // Différence en degrés
        double dLat = target.latitude - origin.latitude;
        double dLon = target.longitude - origin.longitude;
        // Conversion en mètres
        // East (X) : longitude
        float east = (float)(dLon * METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(origin.latitude)));
        // North (Z négatif dans AR) : latitude
        float north = (float)(dLat * METERS_PER_DEGREE_LAT);
        // Up (Y) : altitude
        float up = altitude;
        // Dans ARCore/Sceneform: X=Est, Y=Haut, Z=-Nord (vers utilisateur)
        return new Vector3(east, up, -north);
    }

    /**
     * Calcule la distance entre deux points GPS (en mètres)
     * Formule de Haversine
     */
    public static float distanceBetween(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results
        );
        return results[0];
    }

    /**
     * Calcule l'azimut (bearing) entre deux points GPS
     * @return Angle en degrés (0-360, 0=Nord)
     */
    public static float bearingBetween(LatLng from, LatLng to) {
        double lat1 = Math.toRadians(from.latitude);
        double lon1 = Math.toRadians(from.longitude);
        double lat2 = Math.toRadians(to.latitude);
        double lon2 = Math.toRadians(to.longitude);

        double dLon = lon2 - lon1;

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));

        // Normaliser entre 0 et 360
        return (float)((bearing + 360) % 360);
    }

    /**
     * Calcule un point GPS à une distance et direction données
     * @param start Point de départ
     * @param bearing Direction en degrés
     * @param distance Distance en mètres
     * @return Nouveau point GPS
     */
    public static LatLng destinationPoint(LatLng start, float bearing, float distance) {
        double lat1 = Math.toRadians(start.latitude);
        double lon1 = Math.toRadians(start.longitude);
        double brng = Math.toRadians(bearing);
        double d = distance / EARTH_RADIUS;

        double lat2 = Math.asin(
                Math.sin(lat1) * Math.cos(d) +
                        Math.cos(lat1) * Math.sin(d) * Math.cos(brng)
        );

        double lon2 = lon1 + Math.atan2(
                Math.sin(brng) * Math.sin(d) * Math.cos(lat1),
                Math.cos(d) - Math.sin(lat1) * Math.sin(lat2)
        );

        return new LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }

    /**
     * Interpolation linéaire entre deux points GPS
     * @param start Point de départ
     * @param end Point d'arrivée
     * @param fraction Fraction entre 0 et 1
     * @return Point interpolé
     */
    public static LatLng interpolate(LatLng start, LatLng end, float fraction) {
        double lat = start.latitude + (end.latitude - start.latitude) * fraction;
        double lon = start.longitude + (end.longitude - start.longitude) * fraction;
        return new LatLng(lat, lon);
    }

    /**
     * Vérifie si un point est proche d'un autre (seuil en mètres)
     */
    public static boolean isNear(LatLng point1, LatLng point2, float thresholdMeters) {
        return distanceBetween(point1, point2) <= thresholdMeters;
    }

    /**
     * Convertit une distance AR en distance GPS approximative
     * @param arDistance Distance en mètres dans l'espace AR
     * @param latitude Latitude de référence
     * @return LatLng offset
     */
    public static LatLng arDistanceToGpsOffset(float arDistance, double latitude) {
        double latOffset = arDistance / METERS_PER_DEGREE_LAT;
        double lonOffset = arDistance / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(latitude)));
        return new LatLng(latOffset, lonOffset);
    }

    /**
     * Convertit une position AR (mètres) en coordonnées GPS
     * @param origin  point GPS d’origine de la scène AR
     * @param ar      position AR (unités = mètres)
     * @return        LatLng correspondant
     */
    public static LatLng arPositionToGPS(LatLng origin, Vector3 ar) {
        // 1° lat ≈ 111 111 m
        double lat = origin.latitude  + ar.z / 111_111.0;
        // 1° lng ≈ 111 111 m * cos(lat)
        double lng = origin.longitude + ar.x / (111_111.0 * Math.cos(Math.toRadians(origin.latitude)));
        return new LatLng(lat, lng);
    }

    /**
     * Filtre de Kalman simple pour lisser les positions GPS
     */
    public static class KalmanFilter {
        private float processNoise = 0.008f;
        private float measurementNoise = 0.1f;
        private float estimation = 0;
        private float errorCovariance = 1;

        public float filter(float measurement) {
            // Prédiction
            errorCovariance += processNoise;

            // Mise à jour
            float kalmanGain = errorCovariance / (errorCovariance + measurementNoise);
            estimation += kalmanGain * (measurement - estimation);
            errorCovariance *= (1 - kalmanGain);

            return estimation;
        }

        public void reset() {
            estimation = 0;
            errorCovariance = 1;
        }
    }

    /**
     * Filtre pour lisser une position GPS
     */
    public static class PositionFilter {
        private KalmanFilter latFilter = new KalmanFilter();
        private KalmanFilter lonFilter = new KalmanFilter();

        public LatLng filter(LatLng position) {
            double filteredLat = latFilter.filter((float)position.latitude);
            double filteredLon = lonFilter.filter((float)position.longitude);
            return new LatLng(filteredLat, filteredLon);
        }

        public void reset() {
            latFilter.reset();
            lonFilter.reset();
        }
    }
}