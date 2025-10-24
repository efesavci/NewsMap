#ifndef CLUSTERING_H
#define CLUSTERING_H

#include "dbscan_grid.h"
#include "kmeans.h"

#ifdef __cplusplus
extern "C" {
#endif

    typedef enum {
        CLUSTER_DBSCAN,
        CLUSTER_KMEANS
    } ClusterAlgo;

#ifdef __cplusplus
}
#endif
#endif
