import { useMemo } from 'react';
import Supercluster from 'supercluster';

export const DETAIL_ZOOM = 5;

const hasValidCoordinates = point => Number.isFinite(Number(point.lat)) &&
  Number.isFinite(Number(point.lon)) && Number(point.lat) >= -90 && Number(point.lat) <= 90 &&
  Number(point.lon) >= -180 && Number(point.lon) <= 180 &&
  !(Number(point.lat) === 0 && Number(point.lon) === 0);

function groupExactLocations(features) {
  const groups = new Map();
  features.forEach(feature => {
    const [lon, lat] = feature.geometry.coordinates;
    const key = `${lat.toFixed(5)},${lon.toFixed(5)}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(feature);
  });

  return Array.from(groups.values()).map(group => {
    if (group.length === 1) return group[0];

    const containedHotspots = group.map(feature => feature.properties);
    const first = group[0];
    return {
      ...first,
      properties: {
        ...first.properties,
        locationStack: true,
        point_count: group.length,
        containedHotspots,
        articles: containedHotspots.flatMap(hotspot => hotspot.articles || []),
        category: 'MULTIPLE',
      },
    };
  });
}

/**
 * Enterprise hook for handling blazing fast 10,000+ point clustering on the client side.
 * Converts raw application data into GeoJSON format required by supercluster.
 */
export default function useClustering(points, zoom) {
  // Memoize the supercluster instance so we only rebuild the spatial index when data changes, not on zoom.
  const { supercluster, features } = useMemo(() => {
    const sc = new Supercluster({
      radius: 28,       // Keep only genuinely nearby hotspots together
      maxZoom: DETAIL_ZOOM - 1,
      minPoints: 2,     // Minimum points to form a cluster
    });

    const rawFeatures = points.filter(hasValidCoordinates).map(point => ({
      type: 'Feature',
      properties: {
        cluster: false,
        ...point
      },
      geometry: {
        type: 'Point',
        coordinates: [point.lon, point.lat]
      }
    }));
    
    sc.load(rawFeatures);
    return { supercluster: sc, features: rawFeatures };
  }, [points]);

  // Memoize the actual cluster retrieval so it only re-runs when the integer zoom level changes
  const clusters = useMemo(() => {
    if (zoom >= DETAIL_ZOOM) {
      return groupExactLocations(features);
    }

    // Get all clusters within the full world bounding box: [westLng, southLat, eastLng, northLat]
    return supercluster
      .getClusters([-180, -90, 180, 90], zoom)
      .map(feature => {
        if (!feature.properties.cluster) return feature;

        // A mathematical centroid can land in the ocean. Use the actual child
        // event nearest that centroid as the aggregate's representative point.
        const [centroidLon, centroidLat] = feature.geometry.coordinates;
        const representative = supercluster
          .getLeaves(feature.properties.cluster_id, Infinity)
          .reduce((best, leaf) => {
            const [lon, lat] = leaf.geometry.coordinates;
            const lonDelta = Math.min(Math.abs(lon - centroidLon), 360 - Math.abs(lon - centroidLon));
            const distance = lonDelta * lonDelta + (lat - centroidLat) * (lat - centroidLat);
            return !best || distance < best.distance ? { leaf, distance } : best;
          }, null).leaf;

        return {
          ...feature,
          geometry: representative.geometry,
          properties: {
            ...feature.properties,
            expansion_zoom: Math.min(DETAIL_ZOOM, supercluster.getClusterExpansionZoom(feature.properties.cluster_id)),
          },
        };
      });
  }, [features, supercluster, zoom]);

  return clusters;
}
