cmake_minimum_required(VERSION 3.22.1)
project("dunebox-native" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find prefab packages
find_package(dobby REQUIRED CONFIG)
find_package(xdl REQUIRED CONFIG)

# Source files
add_library(dunebox-native SHARED
        jni_bridge.cpp
        io_redirect.cpp
        path_resolver.cpp
)

# Link libraries
target_link_libraries(dunebox-native
        dobby::dobby
        xdl::xdl
        log
        android
)

# Include headers
target_include_directories(dunebox-native PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)
