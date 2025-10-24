#ifndef KMEANS_H
#define KMEANS_H

#ifdef _WIN32
#define KMEANS_EXPORT __declspec(dllexport)
#else
#define KMEANS_EXPORT __attribute__((visibility("default")))
#endif

KMEANS_EXPORT void kmeans_run(
    const double *x,
    const double *y,
    const double *z,
    int n,
    int dim,
    int k,
    int max_iters,
    int *cluster_ids
);

#endif /* KMEANS_H */
