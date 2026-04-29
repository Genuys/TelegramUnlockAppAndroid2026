cmake_minimum_required(VERSION 3.22.1)
project("awg_bridge")

add_library(awg_bridge SHARED awg_bridge.c)

find_library(log-lib log)
target_link_libraries(awg_bridge ${log-lib})
