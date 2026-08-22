cmake_minimum_required(VERSION 3.22.1)
project("nativepatch")

add_library(nativepatch SHARED
    src/main/cpp/nativepatch.c
)

find_library(log-lib log)

target_link_libraries(nativepatch
    ${log-lib}
)
