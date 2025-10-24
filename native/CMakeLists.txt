cmake_minimum_required(VERSION 3.16)
project(ClusteringLib LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

include_directories(${PROJECT_SOURCE_DIR}/include)

set(SOURCES
        src/dbscan_grid.c
        src/kmeans.c
)

add_library(clustering SHARED ${SOURCES})

set_target_properties(clustering PROPERTIES
        OUTPUT_NAME "clustering"
        VERSION 1.0
        SOVERSION 1
)

if(WIN32)
    target_compile_definitions(clustering PRIVATE _WIN32)
else()
    target_compile_definitions(clustering PRIVATE _GNU_SOURCE)
endif()

install(TARGETS clustering
        LIBRARY DESTINATION lib
        ARCHIVE DESTINATION lib
        RUNTIME DESTINATION bin
)
install(DIRECTORY include/ DESTINATION include)
