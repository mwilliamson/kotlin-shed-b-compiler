cmake_minimum_required(VERSION 3.7)
project(ShedStdlib LANGUAGES C)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Werror")

include_directories(../../c-runtime/src src)

add_library(
    shed STATIC
    src/shed_platform.h src/shed_platform.c
    ../../c-runtime/src/shed.c
    ../../c-runtime/src/strings.c
    src/shed_platform_effects.c
    ../../c-runtime/src/stringbuilder.h ../../c-runtime/src/stringbuilder.c
    ../../c-runtime/src/modules/Core.Cast.c
    src/Core.Io.c
    src/Stdlib.Platform.Process.c
    ../../c-runtime/src/modules/Stdlib.Platform.StringBuilder.c
    ../../c-runtime/src/modules/Stdlib.Platform.Strings.c
)
