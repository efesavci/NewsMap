#ifndef DBSCAN_H
#define DBSCAN_H

#ifdef __cplusplus
extern "C" {
#endif

#ifdef _WIN32
#define DBSCAN_EXPORT __declspec(dllexport)
#else
#define DBSCAN_EXPORT __attribute__((visibility("default")))
#endif

    /**
     * dbscan_run
     *
     * Runs DBSCAN on 2D or 3D data using a uniform grid index (hash-based).
     *
     * Parameters:
     *   x, y, z       : pointers to coordinate arrays (z may be NULL if dim == 2)
     *   n             : number of points
     *   dim           : 2 or 3
     *   eps           : neighborhood radius
     *   minPts        : minimum points to form a core point
     *   cluster_ids   : output array of length n; filled with:
     *                    -1 (UNCLASSIFIED) initially, then 0 for NOISE, 1..K for clusters
     *
     * Notes:
     *   - This function allocates and frees its own internal index structures.
     *   - It writes cluster labels directly into cluster_ids.
     *   - Return value: void (errors logged to stderr on invalid parameters).
     */
    DBSCAN_EXPORT void dbscan_run(
        const double *x,
        const double *y,
        const double *z,   /* may be NULL for dim==2 */
        int n,
        int dim,
        double eps,
        int minPts,
        int *cluster_ids
    );

#ifdef __cplusplus
}
#endif

#endif /* DBSCAN_H */
