cmake_minimum_required(VERSION 3.10)
project(evgrab C)

add_library(evgrab SHARED evgrab.c)

find_library(log-lib log)
target_link_libraries(evgrab ${log-lib})
