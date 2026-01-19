cmake_minimum_required(VERSION 3.22.1)
project(tp7)

# Librairie native (vide si pas de code natif)
add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

# Max page size pour Android 15+
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# Trouver log-lib pour Android
find_library(log-lib log)

# Lier la librairie
target_link_libraries(native-lib ${log-lib})
