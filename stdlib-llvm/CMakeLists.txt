cmake_minimum_required(VERSION 3.7)
project(ShedStdlib LANGUAGES C)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")

add_library(
    shed STATIC
    src/shed.h src/shed.c
    src/stringbuilder.h src/stringbuilder.c
    src/Stdlib.Platform.StringBuilder.c
    src/Stdlib.Platform.Strings.c
)
