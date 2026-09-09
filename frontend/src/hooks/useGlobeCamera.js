import { useState, useCallback, useRef } from 'react';

/**
 * Debounced camera altitude tracker.
 * 
 * Instead of calling setState on every OrbitControls 'change' event (60×/sec),
 * this hook uses a dirty flag + requestAnimationFrame to batch updates.
 * The zoom level only updates when the camera actually settles, preventing
 * cascading re-renders through supercluster and the marker layers.
 */
export default function useGlobeCamera(initialAltitude = 1.5) {
  const [altitude, setAltitude] = useState(initialAltitude);
  const pendingAltitude = useRef(initialAltitude);
  const rafId = useRef(null);
  const debounceTimer = useRef(null);

  const onCameraChange = useCallback((globeRef) => {
    if (!globeRef.current) return;
    
    const pos = globeRef.current.pointOfView();
    pendingAltitude.current = pos.altitude;

    // Cancel any pending debounce
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    if (rafId.current) cancelAnimationFrame(rafId.current);

    // Debounce: only commit to React state 150ms after the last camera movement
    debounceTimer.current = setTimeout(() => {
      rafId.current = requestAnimationFrame(() => {
        setAltitude(pendingAltitude.current);
      });
    }, 150);
  }, []);

  const setCameraAltitude = useCallback((nextAltitude) => {
    pendingAltitude.current = nextAltitude;
    setAltitude(nextAltitude);
  }, []);

  // Globe altitude is exponential while map zoom is linear. Keeping this
  // conversion logarithmic makes every camera zoom step split spatial groups.
  const zoomLevel = Math.max(
    0,
    Math.min(20, Math.round(Math.log2(initialAltitude / Math.max(altitude, 0.01)) + 2))
  );

  return { altitude, zoomLevel, onCameraChange, setCameraAltitude };
}
