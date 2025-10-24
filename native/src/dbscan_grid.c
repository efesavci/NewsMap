/*
* Fast DBSCAN (2D/3D) using a Uniform Grid (hash) index
 * Library build: compile with -fPIC -shared (via CMakeLists)
 *
 * Author: Demir Kurt
 */
#include "../include/dbscan_grid.h"

#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#define MAX_CAPACITY 100000
/* Default hash table size (prime).
 * Rule of thumb: ~10–20× the expected number of occupied cells.
 * You can increase for huge datasets.
 */
#define HASH_SIZE 200003
#define NOISE 0
#define UNCLASSIFIED (-1)
#define DIM 2
/* ---- Types -------------------------------------------------------------- */
typedef struct {
    int *indices;
    int count;
    int capacity;
    long long key_gx;
    long long key_gy;
    long long key_gz;
    int inited;

} Cell;

typedef struct {
    const double *x;
    const double *y;
    const double *z;
    int *cid;
    int n;
    int dim;
} Points;

typedef struct {
    Cell *cells;
    int size;
    double eps;
} Grid;

/* -------- Hash utilities -------------------------------------------------- */

//absolute value retriever
static inline int iabs64(long long v) {
    return (int)((v < 0) ? -v : v);
}
// 3D integer hash -> bucket index (fast)
static inline int hash_idx(long long gx, long long gy, long long gz, int tableSize) {

    long long h = 73856093LL * gx ^ 19349663LL * gy ^ 83492791LL * gz;
    if (h < 0) h = -h;
    return (int)(h % tableSize);
}

/* -------------- Grid Operations ------------------------------------------------ */

static void grid_init(Grid *g, int hash_size, double eps) {
    g -> size = (hash_size > 0) ? hash_size : HASH_SIZE;
    g -> eps = eps;
    g -> cells = (Cell*)calloc((size_t)g -> size, sizeof(Cell));
    if (!g -> cells) {
        fprintf(stderr, "dbscan: failed to allocate grid (%d buckets)\n", g -> size);
        exit(EXIT_FAILURE);
    }
}

static void grid_free(Grid *g) {
    if (!g || !g -> cells) return;
    for (int i = 0; i < g -> size; i++) {
        free(g->cells[i].indices);
    }
    free(g -> cells);
    g -> cells = NULL;
}

static inline void cell_push(Cell *c, int idx) {
    if (!c -> inited) {
        c -> capacity = 8;
        c -> indices = (int*) malloc((size_t)c -> capacity * sizeof(int));
        c-> count = 0;
        c -> inited = 1;
    } else if (c -> count >= c -> capacity) {
        c -> capacity *= 2;
        int *tmp = (int*) realloc(c->indices, (size_t)c->capacity * sizeof(int));
        if (!tmp) {
            fprintf(stderr, "cell_push: realloc failed for capacity %d\n", c->capacity);
            free(c->indices);
            exit(EXIT_FAILURE);
        }
        c->indices = tmp;
    }
    c -> indices[c -> count++] = idx;
}

static inline long long fast_floor_div(double v, double denom) {
    return (long long) floor(v / denom);
}

static void grid_insert_point(Grid *g, const Points *pts, int idx) {
    long long gx = fast_floor_div(pts -> x[idx], g -> eps);
    long long gy = fast_floor_div(pts -> y[idx], g -> eps);
    long long gz = (pts -> dim == 3) ? fast_floor_div(pts -> z[idx], g -> eps) : 0;

    int h = hash_idx(gx, gy, gz, g -> size);
    Cell *c = &g->cells[h];

    if (!c -> inited) {
        c -> key_gx = gx; c -> key_gy = gy; c -> key_gz = gz;
    }
    cell_push(c, idx);
}

static void grid_build(Grid *g, const Points *pts) {
    for (int i = 0; i < pts -> n; i++) {
        grid_insert_point(g,pts,i);
    }
}

static int region_query(const Grid *g, const Points *pts, int p_idx, int **neighbors,
    int *capacity) {

    const double eps2 = g -> eps * g-> eps;

    long long gx = fast_floor_div(pts -> x[p_idx], g -> eps);
    long long gy = fast_floor_div(pts -> y[p_idx], g -> eps);
    long long gz = (pts -> dim == 3) ? fast_floor_div(pts -> z[p_idx], g -> eps) : 0;

    int count = 0;
    int z_min = pts -> dim == 3 ? pts -> dim: 0;
    int z_max = pts -> dim == 3 ? pts -> dim: 1;

    for (int dx = -1; dx <= +1; dx++) {
        for (int dy = -1; dy <= +1; dy++) {
            for (int dz = z_min; dz < z_max; dz++) {
                int h = hash_idx(gx + dx, gy + dy, gz + dz, g -> size);
                const Cell *c = &g -> cells[h];
                if (!c -> inited || c->count == 0) continue;

                for (int i = 0; i < c -> count; i++ ){
                    int idx = c -> indices[i];
                    if (count >= *capacity) {
                        *capacity = (*capacity == 0) ? 256 : (*capacity * 2);
                        *neighbors = (int*) realloc(*neighbors, (*capacity * sizeof(int)));
                    }

                    double dx_ = pts -> x[idx] - pts -> x[p_idx];
                    double dy_ = pts -> y[idx] - pts -> y[p_idx];
                    double d2 = dx_ * dx_ + dy_ * dy_;
                    if (pts -> dim == 3) {
                        double dz_ = pts -> z[idx] - pts -> z[p_idx];
                        d2 += dz_ * dz_;
                    }
                    if (d2 <= eps2) {
                        (*neighbors)[count++] = idx;
                    }

                }


            }
        }
    }
    return count;
}

static int expand_cluster(const Grid *g, Points *pts,int p_idx, int cluster_id, int minPts
    ,int **seed_buf, int *seed_cap,int **tmp_buf,int *tmp_cap) {

    int seed_count = region_query(g,pts,p_idx,seed_buf,seed_cap);
    if (seed_count < minPts) {
        pts -> cid[p_idx] = NOISE;
        return 0;
    }
    for (int i = 0; i < seed_count; i++) {
        pts -> cid[(*seed_buf)[i]] = cluster_id;
    }
    pts -> cid[p_idx] = cluster_id;

    int qhead = 0;
    while (qhead < seed_count) {
        int current = (*seed_buf)[qhead++];

        int new_count = region_query(g,pts,current,tmp_buf,tmp_cap);
        if (new_count >= minPts) {
            for (int j = 0; j < new_count; j++) {
                int q = (*tmp_buf)[j];
                if (pts -> cid[q] == UNCLASSIFIED || pts -> cid[q] == NOISE) {
                    if (pts -> cid[q] == UNCLASSIFIED) {
                        if (seed_count >= *seed_cap) {
                            *seed_cap = (*seed_cap == 0) ? 256 : (*seed_cap * 2);
                            *seed_buf = (int*) realloc(*seed_buf, *seed_cap * sizeof(int));
                        }
                        (*seed_buf)[seed_count++] = q;
                    }
                    pts -> cid[q] = cluster_id;
                }

            }
        }
    }
    return 1;
}

static void dbscan_run_internal(Points *pts, double eps, int minPts) {
    Grid grid;
    grid_init(&grid, HASH_SIZE, eps);
    grid_build(&grid,pts);

    int *seed_buf = NULL, seed_cap = 0;
    int *tmp_buf = NULL, tmp_cap = 0;

    int cluster_id = 1;

    for (int i = 0; i < pts -> n; i++) {
        if (pts -> cid[i] != UNCLASSIFIED) continue;
        if (expand_cluster(&grid, pts, i, cluster_id, minPts,
                           &seed_buf, &seed_cap, &tmp_buf, &tmp_cap)) {
            cluster_id++;
                           }
    }


    free(seed_buf);
    free(tmp_buf);
    grid_free(&grid);
}

static void dbscan_with_grid(
    const double *x,
    const double *y,
    const double *z,
    int n,
    int dim,
    double eps,
    int minPts,
    int *cluster_ids) {

    if (!x || !y || n <= 0 || !cluster_ids) {
        fprintf(stderr, "dbscan_with_grid: invalid input\n");
        return;
    }
    if (dim != 2 && dim != 3) {
        fprintf(stderr, "dbscan_run: dim must be 2 or 3 (got %d)\n", dim);
        return;
    }
    if (dim == 3 && !z) {
        fprintf(stderr, "dbscan_run: z must be non-NULL when dim == 3\n");
        return;
    }
    if (eps <= 0.0 || minPts <= 0) {
        fprintf(stderr, "dbscan_run: eps and minPts must be positive\n");
        return;
    }
    for (int i = 0; i < n; i++) cluster_ids[i] = UNCLASSIFIED;

    Points pts = {
        .x = x, .y = y, .z = z,
        .cid = cluster_ids,
        .n = n,
        .dim = dim
    };

    dbscan_run_internal(&pts, eps, minPts);

}



