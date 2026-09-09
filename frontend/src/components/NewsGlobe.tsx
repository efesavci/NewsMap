import React, { useRef, useMemo, useCallback } from 'react';
import Globe from 'react-globe.gl';
import * as THREE from 'three';
import useClustering, { DETAIL_ZOOM } from '../hooks/useClustering';
import useGlobeCamera from '../hooks/useGlobeCamera';

// --- Types ---
export interface Hotspot {
  lat: number;
  lon: number;
  point_count?: number;
  [key: string]: any;
}

interface NewsGlobeProps {
  hotspots: Hotspot[];
  onSelectHotspot: (spot: Hotspot) => void;
}

export default function NewsGlobe({ hotspots, onSelectHotspot }: NewsGlobeProps) {
  const globeEl = useRef<any>(null);

  // Camera Controller — debounced
  const { zoomLevel, onCameraChange, setCameraAltitude } = useGlobeCamera(1.5);

  // Clustering
  const clusteredData = useClustering(hotspots, zoomLevel);

  // Stable marker array
  const allMarkers = useMemo(() => {
    return clusteredData.map((feature: any) => {
      const [lon, lat] = feature.geometry.coordinates;
      if (feature.properties.cluster) {
        return {
          lat, lon,
          point_count: feature.properties.point_count,
          cluster_id: feature.properties.cluster_id,
          expansionZoom: feature.properties.expansion_zoom,
          isCluster: true,
        };
      }
      return { ...feature.properties, lat, lon, isCluster: false };
    });
  }, [clusteredData]);

  // --- LAYER 1: Define base globe material (Glass orb look) ---
  const baseGlobeMaterial = useMemo(() => {
    return new THREE.MeshLambertMaterial({
      color: '#0b0e12',
      transparent: true,
      opacity: 0.82,
      depthWrite: false,
    });
  }, []);

  const handleGlobeReady = useCallback(() => {
    if (!globeEl.current) return;

    globeEl.current.pointOfView({ altitude: 1.5 });

    const controls = globeEl.current.controls();
    controls.enableDamping = true;
    controls.dampingFactor = 0.12;
    controls.addEventListener('change', () => onCameraChange(globeEl));
  }, [onCameraChange]);

  // Click handler
  const handleMarkerClick = useCallback((marker: any) => {
    if (!globeEl.current) return;
    if (marker.isCluster) {
      const expansionZoom = marker.expansionZoom ?? DETAIL_ZOOM;
      const targetAltitude = Math.max(0.045, 1.5 / Math.pow(2, expansionZoom - 2));
      setCameraAltitude(targetAltitude);
      globeEl.current.pointOfView(
        { lat: marker.lat, lng: marker.lon, altitude: targetAltitude },
        800
      );
    } else {
      onSelectHotspot(marker);
    }
  }, [onSelectHotspot, setCameraAltitude, zoomLevel]);

  // Stable callbacks
  const ringLat = useCallback((d: any) => d.lat, []);
  const ringLng = useCallback((d: any) => d.lon, []);
  const ringColor = useCallback((d: any) => d.isCluster ? '#b9d98b' : d.radarScore ? '#f3c969' : '#f27d5f', []);
  const ringMaxRadius = useCallback((d: any) => d.isCluster
    ? Math.min(0.34 + Math.log2(d.point_count + 1) * 0.13, 0.82)
    : 0.58, []);
  const ringSpeed = useCallback((d: any) => d.isCluster ? 0.35 : 0.55, []);
  const ringPeriod = useCallback((d: any) => d.isCluster ? 1900 : 1600, []);
  const pointLat = useCallback((d: any) => d.lat, []);
  const pointLng = useCallback((d: any) => d.lon, []);
  const pointColor = useCallback(() => 'rgba(0, 0, 0, 0)', []);
  const pointAlt = useCallback(() => 0.01, []);
  // Transparent points provide hit targets for the animated rings.
  const pointRadius = useCallback((d: any) => d.isCluster ? 1.1 : 0.56, []);
  const pointLabel = useCallback((d: any) => d.isCluster
    ? `${d.point_count} events — click to expand`
    : d.locationStack
      ? `${d.point_count} events at ${d.location}`
      : `${d.eventTitle || d.location} — ${d.articles?.length ?? 0} reports${d.radarScore ? ` · Radar ${Math.round(d.radarScore * 100)}%` : ''}`, []);
  const htmlLat = useCallback((d: any) => d.lat, []);
  const htmlLng = useCallback((d: any) => d.lon, []);
  const htmlAltitude = useCallback(() => 0.015, []);
  const htmlElement = useCallback((d: any) => {
    const marker = document.createElement('button');
    const label = pointLabel(d);
    marker.type = 'button';
    marker.title = label;
    marker.setAttribute('aria-label', label);
    marker.dataset.expansionZoom = String(d.expansionZoom ?? '');
    marker.dataset.clusterId = String(d.cluster_id ?? '');
    marker.className = d.isCluster ? 'hotspot-target cluster-target' : 'hotspot-target event-target';
    marker.textContent = d.isCluster ? String(d.point_count) : '';
    marker.style.width = d.isCluster ? '24px' : '18px';
    marker.style.height = d.isCluster ? '24px' : '18px';
    marker.style.padding = '0';
    marker.style.border = '0';
    marker.style.cursor = 'pointer';
    marker.style.pointerEvents = 'auto';
    marker.style.transform = 'translate(-50%, -50%)';
    marker.addEventListener('click', event => {
      event.stopPropagation();
      handleMarkerClick(d);
    });
    return marker;
  }, [handleMarkerClick, pointLabel]);

  return (
    <div className="map-container" style={{ cursor: 'grab' }}>
      <Globe
        ref={globeEl}
        backgroundColor="#080b0f"
        onGlobeReady={handleGlobeReady}
        globeMaterial={baseGlobeMaterial}
        globeImageUrl="/borders.png"

        showAtmosphere={true}
        atmosphereColor="#68716e"
        atmosphereAltitude={0.1}

        ringsData={allMarkers}
        ringLat={ringLat}
        ringLng={ringLng}
        ringColor={ringColor}
        ringMaxRadius={ringMaxRadius}
        ringPropagationSpeed={ringSpeed}
        ringRepeatPeriod={ringPeriod}

        pointsData={allMarkers}
        pointLat={pointLat}
        pointLng={pointLng}
        pointColor={pointColor}
        pointAltitude={pointAlt}
        pointRadius={pointRadius}
        pointLabel={pointLabel}
        onPointClick={handleMarkerClick}

        htmlElementsData={allMarkers}
        htmlLat={htmlLat}
        htmlLng={htmlLng}
        htmlAltitude={htmlAltitude}
        htmlElement={htmlElement}
      />
    </div>
  );
}
